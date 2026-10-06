package com.example.nutricart.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.ai.AiRequestJson
import com.example.nutricart.data.ai.FakeAiEngine
import com.example.nutricart.data.ai.firebase.FirebaseAiReasoningEngine
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiPrioritizedItem
import com.example.nutricart.domain.ai.AiPriority
import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRecommendation
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiResponse
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.generate.GenerateViewModel
import com.example.nutricart.ui.screens.grocerylist.AiInsightsCard
import com.example.nutricart.ui.screens.grocerylist.AiInsightsFailure
import com.example.nutricart.ui.screens.grocerylist.AiInsightsUiState
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import com.example.nutricart.ui.theme.NutriCartTheme
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

// Phase 11: the model prioritises items that are already on the list, and Kotlin decides
// which of its picks are shown. Fake engine and gateway; nothing reaches the network.
// Catalog ids: 2 white rice, 5 lentils, 17 peanuts, 38 sugar.
@RunWith(RobolectricTestRunner::class)
class AiPersonalizedPicksTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var env: TestEnvironment
    private var listId = 0L

    private val base = AiResponse(
        summary = "The list leans on staples.",
        reasoning = "Two food groups are missing.",
        recommendations = listOf(AiRecommendation(AiRecommendationType.Variety, "Add variety", "Fruit is absent."))
    )

    private val baseAnswer = """
        {"summary": "The list leans on staples.", "reasoning": "Two food groups are missing.",
         "recommendations": [{"type": "variety", "title": "Add variety", "explanation": "Fruit is absent."}]}
    """.trimIndent()

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking {
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save("Rangpur Division", 4, setOf(Allergen.Peanuts), setOf(HealthCondition.Diabetes))
            env.catalog.items()
            listId = env.lists.createList(
                6_000,
                listOf(NewListItem(2, 20, 75), NewListItem(5, 4, 160), NewListItem(17, 2, 180), NewListItem(38, 2, 80))
            )
        }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun listScreen(engine: AiReasoningEngine, id: Long = listId) =
        GroceryListViewModel(SavedStateHandle(mapOf(Routes.ARG_LIST_ID to id)), env.lists, env.profiles, engine, 5_000)
            .also { viewModel -> env.awaitUntil { !viewModel.state.value.loading && viewModel.state.value.review != null } }

    private fun GroceryListViewModel.insightsAfterRequest(): AiInsightsUiState {
        onRequestInsights()
        env.awaitUntil { state.value.insights.let { it != AiInsightsUiState.Loading && it != AiInsightsUiState.Idle } }
        return state.value.insights
    }

    private fun pick(name: String, priority: AiPriority = AiPriority.High, reason: String = "It is a staple.") =
        AiPrioritizedItem(name, priority, reason)

    private fun picksFor(vararg picks: AiPrioritizedItem, id: Long = listId): List<AiPrioritizedItem> {
        val viewModel = listScreen(FakeAiEngine { AiResult.Success(base.copy(prioritizedItems = picks.toList())) }, id)
        return (viewModel.insightsAfterRequest() as AiInsightsUiState.Ready).picks
    }

    private fun stored(id: Long = listId) = runBlocking { env.lists.getItems(id) }

    private fun generate(budget: Int): Long {
        val viewModel = GenerateViewModel(
            SavedStateHandle(mapOf(Routes.ARG_BUDGET to budget)), env.profiles, env.catalog, env.lists, 0
        )
        env.awaitUntil { !viewModel.state.value.running }
        return viewModel.state.value.listId ?: error("generation failed: ${viewModel.state.value.error}")
    }

    // 1 and 6: generation is the deterministic generator's alone, with or without AI

    @Test
    fun generation_isTheSameWhateverTheAiDoes() {
        val first = generate(8_000)
        val offline = listScreen(FakeAiEngine { AiResult.Failure(AiError.Network) }, first)
        assertEquals(AiInsightsUiState.Failed(AiInsightsFailure.Connection), offline.insightsAfterRequest())
        assertEquals(stored(first), offline.state.value.items)

        // An answer that tries to reshape the list changes nothing either
        val reshaping = base.copy(prioritizedItems = listOf(pick("Dragon fruit"), pick("Peanuts")))
        val answered = listScreen(FakeAiEngine { AiResult.Success(reshaping) }, first)
        assertTrue(answered.insightsAfterRequest() is AiInsightsUiState.Ready)

        val second = generate(8_000)
        fun lines(id: Long) = stored(id).map { Triple(it.catalog.id, it.item.quantity, it.item.unitPrice) }
        assertEquals(lines(first), lines(second))
        // The peanut allergy still decided what was generated
        assertTrue(stored(second).none { Allergen.Peanuts in it.catalog.allergens })
    }

    // 2: what the model is given to choose from

    @Test
    fun context_offersOnlyTheListsOwnItemsAsCandidates() {
        val engine = FakeAiEngine { AiResult.Success(base) }
        listScreen(engine).insightsAfterRequest()

        val request = engine.requests.single()
        assertEquals(stored().map { it.catalog.name }, request.context.items.map { it.name })
        assertEquals(stored().map { it.catalog.nutrientTag.name }, request.context.items.map { it.mainNutrient })
        assertEquals(listOf("Peanuts", "White sugar"), request.context.flaggedItems.map { it.name })

        val prompt = AiRequestJson.prompt(request)
        assertTrue(prompt.contains("Copy each itemName exactly as it is written in the items list"))
        assertTrue(prompt.contains("never name anything that is not in that list"))
        assertTrue(prompt.contains("never pick an item that is in flaggedItems"))
        // Candidates carry no price, no per-item nutrition figure and no catalog id
        val item = JSONObject(prompt.substringAfter("Context (JSON):\n")).getJSONArray("items").getJSONObject(0)
        assertEquals(setOf("name", "category", "quantity", "unit", "mainNutrient"), item.keys().asSequence().toSet())
        listOf("arpita", "secret123", "Rangpur", "unitPrice", "caloriesPer100g").forEach {
            assertFalse(prompt.contains(it, ignoreCase = true))
        }
    }

    // 3: an unknown product never gets through

    @Test
    fun picksNamingSomethingNotOnTheList_areDropped() {
        val before = stored()

        val picks = picksFor(
            pick("Dragon fruit"),
            pick("Lentils (Masoor)", AiPriority.Medium),
            pick("Lentils"),
            pick("Brown rice"),
            pick("  white RICE (miniket) ", AiPriority.High)
        )

        // Only the two real items, under the app's own names, highest priority first
        assertEquals(listOf("White rice (Miniket)", "Lentils (Masoor)"), picks.map { it.itemName })
        assertEquals(listOf(AiPriority.High, AiPriority.Medium), picks.map { it.priority })
        // And nothing was added to the list
        assertEquals(before, stored())
    }

    @Test
    fun picks_areShownOncePerItemInPriorityOrder() {
        val picks = picksFor(
            pick("Lentils (Masoor)", AiPriority.Low, "First mention."),
            pick("White rice (Miniket)", AiPriority.High),
            pick("Lentils (Masoor)", AiPriority.High, "Second mention.")
        )

        assertEquals(listOf("White rice (Miniket)", "Lentils (Masoor)"), picks.map { it.itemName })
        assertEquals("First mention.", picks.last().reason)
    }

    // 4: the allergy and health-condition results are not the model's to overrule

    @Test
    fun picksForFlaggedItems_areDroppedAndTheFlagsStay() {
        val viewModel = listScreen(
            FakeAiEngine {
                AiResult.Success(
                    base.copy(
                        prioritizedItems = listOf(
                            pick("Peanuts", reason = "A good protein source."),
                            pick("White sugar"),
                            pick("Lentils (Masoor)")
                        )
                    )
                )
            }
        )
        val reviewBefore = viewModel.state.value.review

        val ready = viewModel.insightsAfterRequest() as AiInsightsUiState.Ready

        assertEquals(listOf("Lentils (Masoor)"), ready.picks.map { it.itemName })
        assertEquals(reviewBefore, viewModel.state.value.review)
        assertEquals(listOf("Peanuts"), viewModel.state.value.review!!.allergenMatches.map { it.itemName })
        assertFalse(stored().first { it.catalog.id == 17L }.item.alertOverridden)
    }

    @Test
    fun typedAllergy_isResolvedByTheExistingMatcherBeforeThePicksAreChecked() {
        runBlocking {
            env.profiles.save("Rangpur Division", 4, emptySet(), emptySet(), customAllergies = listOf("groundnut", "Kiwi"))
        }

        val picks = picksFor(pick("Peanuts"), pick("White sugar"))

        // "groundnut" is a known name for peanuts; "Kiwi" is unknown and flags nothing
        assertEquals(listOf("White sugar"), picks.map { it.itemName })
    }

    // 5: a malformed answer is a failure, never a partial result

    private fun insightsFor(change: JSONObject.() -> Unit): AiInsightsUiState {
        val answer = JSONObject(baseAnswer).apply(change).toString()
        return listScreen(FirebaseAiReasoningEngine({ _, _ -> answer })).insightsAfterRequest()
    }

    private fun pickJson(name: Any?, priority: Any?, reason: Any?) = JSONObject().apply {
        if (name != null) put("itemName", name)
        if (priority != null) put("priority", priority)
        if (reason != null) put("reason", reason)
    }

    @Test
    fun wellFormedPicksAndTradeOffs_areParsed() {
        val state = insightsFor {
            put("prioritizedItems", JSONArray().put(pickJson("Lentils (Masoor)", "medium", "Covers protein.")))
            put("tradeOffs", JSONArray().put("Budget is spent on staples before variety."))
        } as AiInsightsUiState.Ready

        assertEquals(listOf(pick("Lentils (Masoor)", AiPriority.Medium, "Covers protein.")), state.picks)
        assertEquals(listOf("Budget is spent on staples before variety."), state.response.tradeOffs)
    }

    @Test
    fun malformedPicksOrTradeOffs_failTheWholeAnswer() {
        val unavailable = AiInsightsUiState.Failed(AiInsightsFailure.Unavailable)
        fun picks(vararg items: JSONObject): JSONObject.() -> Unit = { put("prioritizedItems", JSONArray(items.toList())) }

        assertEquals(unavailable, insightsFor(picks(pickJson("Lentils (Masoor)", "urgent", "x"))))
        assertEquals(unavailable, insightsFor(picks(pickJson("Lentils (Masoor)", "high", null))))
        assertEquals(unavailable, insightsFor(picks(pickJson(null, "high", "x"))))
        assertEquals(unavailable, insightsFor(picks(pickJson(17, "high", "x"))))
        assertEquals(unavailable, insightsFor(picks(pickJson("Lentils (Masoor)", "high", " "))))
        assertEquals(unavailable, insightsFor(picks(pickJson("Peanuts", "high", "Peanuts are safe here."))))
        assertEquals(unavailable, insightsFor(picks(*Array(7) { pickJson("Lentils (Masoor)", "high", "x") })))
        assertEquals(unavailable, insightsFor { put("prioritizedItems", "Lentils") })
        assertEquals(unavailable, insightsFor { put("tradeOffs", JSONArray().put(3)) })
        assertEquals(unavailable, insightsFor { put("tradeOffs", JSONArray(List(4) { "One more." })) })
        assertEquals(unavailable, insightsFor { put("tradeOffs", JSONArray().put("This guarantees balance.")) })
    }

    // 8: the Phase 10 answer still works on its own

    @Test
    fun answerWithoutPicks_isStillThePhase10Answer() {
        val state = insightsFor { }

        assertEquals(AiInsightsUiState.Ready(base), state)
        assertTrue((state as AiInsightsUiState.Ready).picks.isEmpty())
    }

    // 7: budget figures are Kotlin's

    @Test
    fun budgetFigures_areTheAppsOwnWhateverTheAnswerSays() {
        val expectedTotal = 20 * 75 + 4 * 160 + 2 * 180 + 2 * 80
        val viewModel = listScreen(
            FakeAiEngine {
                AiResult.Success(
                    base.copy(
                        summary = "The list totals 1 BDT against a 99 BDT budget.",
                        tradeOffs = listOf("The total is 1 BDT, well over budget."),
                        prioritizedItems = listOf(pick("Lentils (Masoor)", reason = "It costs 5 BDT."))
                    )
                )
            }
        )

        assertTrue(viewModel.insightsAfterRequest() is AiInsightsUiState.Ready)

        val state = viewModel.state.value
        assertEquals(expectedTotal, state.total)
        assertEquals(6_000, state.budget)
        assertEquals(ListTotals(expectedTotal, 6_000), state.totals)
        assertEquals(6_000 - expectedTotal, state.remaining)
        assertEquals(stored().sumOf { it.item.quantity * it.item.unitPrice }, state.total)
    }

    // The card

    @Test
    fun card_showsThePicksAndTradeOffsWhenThereAreAny() {
        composeRule.setContent {
            NutriCartTheme {
                AiInsightsCard(
                    state = AiInsightsUiState.Ready(
                        base.copy(tradeOffs = listOf("Staples before variety.")),
                        listOf(pick("Lentils (Masoor)", AiPriority.High, "Covers protein."))
                    ),
                    onRequest = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("AI PICKS FROM YOUR LIST").assertIsDisplayed()
        composeRule.onNodeWithText("Lentils (Masoor)").assertIsDisplayed()
        composeRule.onNodeWithText("HIGH").assertIsDisplayed()
        composeRule.onNodeWithText("Covers protein.").assertIsDisplayed()
        composeRule.onNodeWithText("Staples before variety.").assertExists()
        composeRule.onNodeWithText("Written by AI", substring = true).assertExists()
    }

    @Test
    fun card_leavesThePicksOutWhenNonePassedTheCheck() {
        composeRule.setContent {
            NutriCartTheme { AiInsightsCard(state = AiInsightsUiState.Ready(base), onRequest = {}, onDismiss = {}) }
        }

        composeRule.onNodeWithText("The list leans on staples.").assertIsDisplayed()
        composeRule.onNodeWithText("AI PICKS FROM YOUR LIST").assertDoesNotExist()
        composeRule.onNodeWithText("TRADE-OFFS").assertDoesNotExist()
    }
}
