package com.example.nutricart.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.example.nutricart.R
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.ai.AiRequestJson
import com.example.nutricart.data.ai.FakeAiEngine
import com.example.nutricart.data.ai.firebase.FirebaseAiReasoningEngine
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiFlaggedItem
import com.example.nutricart.domain.ai.AiNutritionSummary
import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRecommendation
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiResponse
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.ai.AiTask
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.alerts.ProfileReview
import com.example.nutricart.ui.screens.grocerylist.AiInsightsCard
import com.example.nutricart.ui.screens.grocerylist.AiInsightsFailure
import com.example.nutricart.ui.screens.grocerylist.AiInsightsUiState
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import com.example.nutricart.ui.theme.NutriCartTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
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

// Phase 10: the optional AI insights on the list screen, against the real database and a
// fake engine. Nothing here reaches the network. Catalog ids: 2 white rice, 5 lentils,
// 17 peanuts, 38 sugar.
@RunWith(RobolectricTestRunner::class)
class AiInsightsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var env: TestEnvironment
    private var listId = 0L

    private val response = AiResponse(
        summary = "The list leans on staples.",
        reasoning = "Two food groups are missing.",
        recommendations = listOf(AiRecommendation(AiRecommendationType.Variety, "Add variety", "Fruit is absent."))
    )

    private val validAnswer = """
        {"summary": "The list leans on staples.", "reasoning": "Two food groups are missing.",
         "recommendations": [{"type": "variety", "title": "Add variety", "explanation": "Fruit is absent."}]}
    """.trimIndent()

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking {
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save(
                "Rangpur Division", 4, setOf(Allergen.Peanuts), setOf(HealthCondition.Diabetes),
                customAllergies = listOf("Kiwi"), customConditions = listOf("Migraine")
            )
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

    private fun listScreen(engine: AiReasoningEngine, timeoutMs: Long = 5_000, id: Long = listId) =
        GroceryListViewModel(SavedStateHandle(mapOf(Routes.ARG_LIST_ID to id)), env.lists, env.profiles, engine, timeoutMs)
            .also { viewModel -> env.awaitUntil { !viewModel.state.value.loading && viewModel.state.value.review != null } }

    private fun GroceryListViewModel.insights() = state.value.insights

    private fun GroceryListViewModel.requestAndWait() {
        onRequestInsights()
        env.awaitUntil { insights() != AiInsightsUiState.Loading && insights() != AiInsightsUiState.Idle }
    }

    private fun stored() = runBlocking { env.lists.getItems(listId) }

    private fun itemId(catalogId: Long) = stored().first { it.catalog.id == catalogId }.item.id

    private fun settle() {
        Thread.sleep(300)
        env.awaitUntil { true }
    }

    // The request context

    @Test
    fun context_holdsTheListTheProfileConstraintsAndTheAppsOwnFindings() {
        val engine = FakeAiEngine { AiResult.Success(response) }
        val viewModel = listScreen(engine)

        viewModel.requestAndWait()

        val request = engine.requests.single()
        val context = request.context
        val profile = runBlocking { env.profiles.get() }
        val review = ProfileReview.analyze(stored(), profile)!!
        assertEquals(AiTask.ReviewGroceryPlan, request.task)
        assertEquals(4, context.householdSize)
        assertEquals(6_000, context.budget)
        assertEquals(30, context.planningPeriodDays)
        assertEquals("BDT", context.currency)
        assertEquals(listOf("Peanuts", "Kiwi"), context.allergies)
        assertEquals(listOf("Diabetes", "Migraine"), context.healthConditions)
        assertEquals(stored().map { it.catalog.name }, context.items.map { it.name })
        assertEquals(listOf(20, 4, 2, 2), context.items.map { it.quantity })
        // Figures are the app's own, copied
        assertEquals(20 * 75 + 4 * 160 + 2 * 180 + 2 * 80, context.listTotal)
        assertEquals(viewModel.state.value.total, context.listTotal)
        assertEquals(AiNutritionSummary.from(NutritionAnalyzer.analyze(stored(), 4)!!), context.nutrition)
        // What the review flagged, as the review has it
        assertEquals(
            listOf(
                AiFlaggedItem("Peanuts", listOf("ContainsListedAllergen (Peanuts)"), keptByUser = false),
                AiFlaggedItem("White sugar", listOf("ConcentratedCarbohydrate (Diabetes)"), keptByUser = false)
            ),
            context.flaggedItems
        )
        assertEquals(review.considerations.size, context.listConsiderations.size)
        context.listConsiderations.forEach { assertTrue(it, it.endsWith("(Diabetes)")) }
        assertEquals(listOf("Kiwi", "Migraine"), context.notCheckedByRules)
    }

    @Test
    fun context_sendsNothingPersonalAndNoDatabaseFields() {
        val engine = FakeAiEngine { AiResult.Success(response) }
        listScreen(engine).requestAndWait()

        val prompt = AiRequestJson.prompt(engine.requests.single())

        listOf(
            "Arpita", "arpita@example.com", "secret123", "Rangpur", "accountId", "listId", "unitPrice",
            "catalogItemId", "bought", "password", "allergens", "flaggedConditions"
        ).forEach { assertFalse("the prompt holds \"$it\"", prompt.contains(it, ignoreCase = true)) }
        val context = JSONObject(prompt.substringAfter("Context (JSON):\n"))
        assertEquals(
            setOf("name", "category", "quantity", "unit", "mainNutrient", "amount"),
            context.getJSONArray("items").getJSONObject(0).keys().asSequence().toSet()
        )
        assertEquals(
            setOf("name", "reasons", "keptByUser"),
            context.getJSONArray("flaggedItems").getJSONObject(0).keys().asSequence().toSet()
        )
    }

    @Test
    fun context_keepsAKeptItemFlagged() {
        runBlocking { env.lists.setKeptAnyway(itemId(17), true) }
        val engine = FakeAiEngine { AiResult.Success(response) }
        val viewModel = listScreen(engine)
        env.awaitUntil { viewModel.state.value.review!!.needsReview == 1 }

        viewModel.requestAndWait()

        val peanuts = engine.requests.single().context.flaggedItems.first()
        assertEquals("Peanuts", peanuts.name)
        assertTrue(peanuts.keptByUser)
        assertEquals(listOf("ContainsListedAllergen (Peanuts)"), peanuts.reasons)
    }

    @Test
    fun instructions_putTheAppsRulesAboveTheModel() {
        val instructions = AiRequestJson.INSTRUCTIONS

        listOf(
            "only source of facts",
            "authoritative",
            "never contradict it",
            "Do not invent or estimate prices, nutrition figures, allergen contents or product availability",
            "Never suggest adding a food that contains a listed allergy",
            "never say or imply that a flagged item is acceptable",
            "say they were not checked",
            "say so instead of guessing",
            "Do not give medical advice",
            "do not mention diagnosis"
        ).forEach { assertTrue("missing: $it", instructions.contains(it)) }
    }

    // Loading, success, failure

    @Test
    fun request_goesFromIdleThroughLoadingToTheAnswer() {
        val gate = CompletableDeferred<AiResult>()
        val engine = FakeAiEngine { gate.await() }
        val viewModel = listScreen(engine)
        assertEquals(AiInsightsUiState.Idle, viewModel.insights())

        viewModel.onRequestInsights()
        assertEquals(AiInsightsUiState.Loading, viewModel.insights())
        env.awaitUntil { engine.requests.size == 1 }
        // A second tap while waiting sends nothing more
        viewModel.onRequestInsights()
        settle()
        assertEquals(1, engine.requests.size)
        // The list is usable while it waits
        assertEquals(4, viewModel.state.value.items.size)

        gate.complete(AiResult.Success(response))
        env.awaitUntil { viewModel.insights() is AiInsightsUiState.Ready }
        assertEquals(response, (viewModel.insights() as AiInsightsUiState.Ready).response)
    }

    @Test
    fun everyFailure_isShownAsAFriendlyStateAndLeavesTheListAlone() {
        AiError.entries.forEach { error ->
            val viewModel = listScreen(FakeAiEngine { AiResult.Failure(error, "HTTP 503 x-goog") })
            val before = viewModel.state.value

            viewModel.requestAndWait()

            val expected = if (error == AiError.Network) AiInsightsFailure.Connection else AiInsightsFailure.Unavailable
            assertEquals(AiInsightsUiState.Failed(expected), viewModel.insights())
            assertEquals(before.copy(insights = viewModel.insights()), viewModel.state.value)
        }
    }

    @Test
    fun retryAfterAFailure_asksAgainAndShowsTheAnswer() {
        var attempts = 0
        val engine = FakeAiEngine {
            if (attempts++ == 0) AiResult.Failure(AiError.Network) else AiResult.Success(response)
        }
        val viewModel = listScreen(engine)

        viewModel.requestAndWait()
        assertEquals(AiInsightsUiState.Failed(AiInsightsFailure.Connection), viewModel.insights())

        viewModel.onRequestInsights()
        env.awaitUntil { viewModel.insights() is AiInsightsUiState.Ready }
        assertEquals(2, engine.requests.size)
        assertEquals(engine.requests[0], engine.requests[1])
    }

    @Test
    fun engineThatThrows_doesNotCrashTheScreen() {
        val viewModel = listScreen(FakeAiEngine { throw IllegalStateException("boom") })

        viewModel.requestAndWait()

        assertEquals(AiInsightsUiState.Failed(AiInsightsFailure.Unavailable), viewModel.insights())
        assertEquals(4, viewModel.state.value.items.size)
    }

    @Test
    fun noAnswerInTime_isAFailureThatCanBeRetried() {
        val engine = FakeAiEngine { awaitCancellation() }
        val viewModel = listScreen(engine, timeoutMs = 150)

        viewModel.requestAndWait()

        assertEquals(AiInsightsUiState.Failed(AiInsightsFailure.Connection), viewModel.insights())
        engine.answer = { AiResult.Success(response) }
        viewModel.onRequestInsights()
        env.awaitUntil { viewModel.insights() is AiInsightsUiState.Ready }
    }

    // Parsing: a bad answer never reaches the screen, whole or in part

    private fun insightsFor(answer: String?): AiInsightsUiState {
        val viewModel = listScreen(FirebaseAiReasoningEngine({ _, _ -> answer }))
        viewModel.requestAndWait()
        return viewModel.insights()
    }

    @Test
    fun structuredAnswer_isParsedAndShownAsItWasGiven() {
        assertEquals(AiInsightsUiState.Ready(response), insightsFor(validAnswer))
    }

    @Test
    fun malformedOrInvalidAnswers_areFailuresNotPartialAnswers() {
        val unavailable = AiInsightsUiState.Failed(AiInsightsFailure.Unavailable)
        fun changed(change: JSONObject.() -> Unit) = JSONObject(validAnswer).apply(change).toString()

        assertEquals(unavailable, insightsFor("Here are some ideas for your list."))
        assertEquals(unavailable, insightsFor("{\"summary\": \"cut off"))
        assertEquals(unavailable, insightsFor(null))
        assertEquals(unavailable, insightsFor(""))
        // A missing field is not filled in with anything
        assertEquals(unavailable, insightsFor(changed { remove("summary") }))
        assertEquals(unavailable, insightsFor(changed { remove("reasoning") }))
        assertEquals(unavailable, insightsFor(changed { remove("recommendations") }))
        assertEquals(unavailable, insightsFor(changed { getJSONArray("recommendations").getJSONObject(0).remove("title") }))
        assertEquals(unavailable, insightsFor(changed { put("summary", 42) }))
        assertEquals(unavailable, insightsFor(changed { put("recommendations", org.json.JSONArray()) }))
        assertEquals(unavailable, insightsFor(validAnswer.replace("\"variety\"", "\"medical\"")))
        // Safety and medical wording is rejected whole
        assertEquals(unavailable, insightsFor(changed { put("summary", "Peanuts are safe for this household.") }))
        assertEquals(unavailable, insightsFor(changed { put("reasoning", "This can cure the condition.") }))
    }

    // The deterministic results stay in charge

    @Test
    fun answerThatContradictsTheReview_changesNothingTheAppDecided() {
        val contradicting = response.copy(
            summary = "Peanuts are fine to keep and the nutrition score is 99.",
            recommendations = listOf(AiRecommendation(AiRecommendationType.Other, "Keep peanuts", "Ignore the flag."))
        )
        val viewModel = listScreen(FakeAiEngine { AiResult.Success(contradicting) })
        val reviewBefore = viewModel.state.value.review!!
        val storedBefore = stored()
        val nutritionBefore = NutritionAnalyzer.analyze(storedBefore, 4)
        val profileBefore = runBlocking { env.profiles.get() }

        viewModel.requestAndWait()
        settle()

        assertTrue(viewModel.insights() is AiInsightsUiState.Ready)
        // The peanut match is still there, still unreviewed, still the strongest kind
        assertEquals(reviewBefore, viewModel.state.value.review)
        assertEquals(listOf("Peanuts"), viewModel.state.value.review!!.allergenMatches.map { it.itemName })
        assertEquals(2, viewModel.state.value.review!!.needsReview)
        assertFalse(stored().first { it.catalog.id == 17L }.item.alertOverridden)
        // Nothing was written, and the figures are what they were
        assertEquals(storedBefore, stored())
        assertEquals(nutritionBefore, NutritionAnalyzer.analyze(stored(), 4))
        assertEquals(profileBefore, runBlocking { env.profiles.get() })
        assertEquals(5, env.database.openHelper.readableDatabase.version)
    }

    @Test
    fun listWorksTheSameWhenAiIsUnavailable() {
        val viewModel = listScreen(FakeAiEngine())
        viewModel.requestAndWait()
        assertEquals(AiInsightsUiState.Failed(AiInsightsFailure.Unavailable), viewModel.insights())

        // Search, filter, bought and edits all still work
        viewModel.onQueryChange("rice")
        assertEquals(listOf("White rice (Miniket)"), viewModel.state.value.visibleItems.map { it.catalog.name })
        viewModel.onQueryChange("")
        viewModel.onBoughtChange(itemId(2), true)
        env.awaitUntil { viewModel.state.value.boughtCount == 1 }
        assertEquals(20 * 75 + 4 * 160 + 2 * 180 + 2 * 80, viewModel.state.value.total)
        assertEquals(2, viewModel.state.value.review!!.items.size)
        // Marking an item bought is not a change to what was asked about
        assertEquals(AiInsightsUiState.Failed(AiInsightsFailure.Unavailable), viewModel.insights())
    }

    // Insights follow the list they were asked about

    @Test
    fun editingTheListOrTheProfile_dropsInsightsThatNoLongerDescribeIt() {
        val viewModel = listScreen(FakeAiEngine { AiResult.Success(response) })

        viewModel.requestAndWait()
        runBlocking { env.lists.changeQuantity(itemId(2), +1) }
        env.awaitUntil { viewModel.insights() == AiInsightsUiState.Idle }
        assertEquals(21, viewModel.state.value.items.first { it.catalog.id == 2L }.item.quantity)

        viewModel.requestAndWait()
        runBlocking {
            env.profiles.save("Rangpur Division", 4, setOf(Allergen.Peanuts, Allergen.Eggs), setOf(HealthCondition.Diabetes))
        }
        env.awaitUntil { viewModel.insights() == AiInsightsUiState.Idle }
    }

    @Test
    fun hide_returnsToTheOffer() {
        val viewModel = listScreen(FakeAiEngine { AiResult.Success(response) })
        viewModel.requestAndWait()

        viewModel.onDismissInsights()

        assertEquals(AiInsightsUiState.Idle, viewModel.insights())
    }

    @Test
    fun withoutAListOrItems_nothingIsSent() {
        val engine = FakeAiEngine { AiResult.Success(response) }
        val missing = GroceryListViewModel(
            SavedStateHandle(mapOf(Routes.ARG_LIST_ID to 9_999L)), env.lists, env.profiles, engine
        )
        env.awaitUntil { !missing.state.value.loading }
        missing.onRequestInsights()

        val empty = runBlocking { env.lists.createList(1_000, emptyList()) }
        val emptyScreen = GroceryListViewModel(
            SavedStateHandle(mapOf(Routes.ARG_LIST_ID to empty)), env.lists, env.profiles, engine
        )
        env.awaitUntil { !emptyScreen.state.value.loading }
        emptyScreen.onRequestInsights()
        settle()

        assertTrue(engine.requests.isEmpty())
        assertEquals(AiInsightsUiState.Idle, missing.insights())
        assertEquals(AiInsightsUiState.Idle, emptyScreen.insights())
    }

    // The card

    private fun showCard(state: AiInsightsUiState, onRequest: () -> Unit = {}, onDismiss: () -> Unit = {}) {
        composeRule.setContent {
            NutriCartTheme { AiInsightsCard(state = state, onRequest = onRequest, onDismiss = onDismiss) }
        }
    }

    @Test
    fun card_offersInsightsAndSaysWhatIsSent() {
        var requests = 0
        showCard(AiInsightsUiState.Idle, onRequest = { requests++ })

        // One line until it is opened; nothing is sent by opening it
        composeRule.onNodeWithText("Get AI insights").assertDoesNotExist()
        composeRule.onNodeWithText("AI insights").assertIsDisplayed().performClick()
        assertEquals(0, requests)
        composeRule.onNodeWithText("Your name and email are not sent.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Get AI insights").performClick()

        assertEquals(1, requests)
    }

    @Test
    fun card_showsTheAnswerWithItsAdvisoryNote() {
        var dismissed = 0
        showCard(AiInsightsUiState.Ready(response), onDismiss = { dismissed++ })

        composeRule.onNodeWithText("The list leans on staples.").assertIsDisplayed()
        composeRule.onNodeWithText("Two food groups are missing.").assertIsDisplayed()
        composeRule.onNodeWithText("Add variety").assertIsDisplayed()
        composeRule.onNodeWithText("Fruit is absent.").assertIsDisplayed()
        composeRule.onNodeWithText("Written by AI", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Hide").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun card_showsLoading() {
        showCard(AiInsightsUiState.Loading)

        composeRule.onNodeWithText("Asking Gemini about this list…").assertIsDisplayed()
        composeRule.onNodeWithText("Get AI insights").assertDoesNotExist()
    }

    @Test
    fun card_showsAFailureWithTryAgain() {
        var requests = 0
        showCard(AiInsightsUiState.Failed(AiInsightsFailure.Connection), onRequest = { requests++ })

        composeRule.onNodeWithText("Couldn't reach AI insights. Check your connection and try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()

        assertEquals(1, requests)
    }

    // Wording

    @Test
    fun insightsWording_makesNoMedicalOrSafetyClaimsAndShowsNoTechnicalDetail() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val forbidden = listOf(
            "safe", "cure", "treat", "diagnos", "guarantee", "medically", "you should", "dangerous", "harmful",
            "approved", "firebase", "app check", "http", "exception", "error"
        )
        val strings = R.string::class.java.fields.filter { it.name.startsWith("ai_insights_") }
            .map { it.name to context.getString(it.getInt(null)) }

        assertTrue(strings.size >= 15)
        strings.forEach { (name, text) ->
            forbidden.forEach { word -> assertFalse("$name says: $text", text.contains(word, ignoreCase = true)) }
        }
        val disclaimer = context.getString(R.string.ai_insights_disclaimer)
        assertTrue(disclaimer.contains("it can be wrong"))
        assertTrue(disclaimer.contains("not medical advice"))
    }
}
