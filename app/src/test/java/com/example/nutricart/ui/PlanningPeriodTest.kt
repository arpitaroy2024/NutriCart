package com.example.nutricart.ui

import androidx.lifecycle.SavedStateHandle
import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.ai.AiRequestJson
import com.example.nutricart.data.ai.FakeAiEngine
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.repository.NewListItem
import com.example.nutricart.domain.BudgetError
import com.example.nutricart.domain.BudgetRules
import com.example.nutricart.domain.GenerationRequest
import com.example.nutricart.domain.GenerationResult
import com.example.nutricart.domain.GroceryGenerator
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.domain.PlanningPeriod
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiPrioritizedItem
import com.example.nutricart.domain.ai.AiPriority
import com.example.nutricart.domain.ai.AiReasoningEngine
import com.example.nutricart.domain.ai.AiRecommendation
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiResponse
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.nutrition.Nutrient
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.navigation.Routes
import com.example.nutricart.ui.screens.generate.GenerateViewModel
import com.example.nutricart.ui.screens.grocerylist.AiInsightsFailure
import com.example.nutricart.ui.screens.grocerylist.AiInsightsUiState
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import com.example.nutricart.ui.screens.home.HomeViewModel
import com.example.nutricart.ui.screens.nutrition.NutritionViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.math.ceil

// The planning period: a list can be for 7, 14, 21 or 30 days. A month is the default and
// behaves exactly as before. Catalog ids: 1 brown rice, 2 white rice, 5 lentils, 8 eggs.
@RunWith(RobolectricTestRunner::class)
class PlanningPeriodTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment

    private val rangpur = DemoCatalogSeed.prices.filter { it.region == "Rangpur Division" }.associate { it.catalogItemId to it.price }

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
        runBlocking {
            env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
            env.profiles.save("Rangpur Division", 4, emptySet(), emptySet())
        }
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun request(budget: Int, days: Int? = null, household: Int = 4): GenerationRequest {
        val base = GenerationRequest(budget, household, DemoCatalogSeed.items, rangpur, DemoCatalogSeed.basket)
        return if (days == null) base else base.copy(days = days)
    }

    private fun generated(budget: Int, days: Int? = null, household: Int = 4) =
        (GroceryGenerator.generate(request(budget, days, household)) as GenerationResult.Success).items

    // Runs the real generation screen and returns the saved list's id
    private fun generateList(budget: Int, days: Int? = null): Long {
        val args = buildMap<String, Any> {
            put(Routes.ARG_BUDGET, budget)
            if (days != null) put(Routes.ARG_DAYS, days)
        }
        val viewModel = GenerateViewModel(SavedStateHandle(args), env.profiles, env.catalog, env.lists, 0)
        env.awaitUntil { !viewModel.state.value.running }
        return viewModel.state.value.listId ?: error("generation failed: ${viewModel.state.value.error}")
    }

    private fun list(id: Long): GroceryListEntity = runBlocking { env.lists.observeList(id).first() }!!

    private fun items(id: Long) = runBlocking { env.lists.getItems(id) }

    private fun lines(id: Long) = items(id).map { Triple(it.catalog.id, it.item.quantity, it.item.unitPrice) }

    private fun listScreen(id: Long, engine: AiReasoningEngine = FakeAiEngine()) =
        GroceryListViewModel(SavedStateHandle(mapOf(Routes.ARG_LIST_ID to id)), env.lists, env.profiles, engine, 5_000)
            .also { viewModel -> env.awaitUntil { !viewModel.state.value.loading && viewModel.state.value.review != null } }

    private fun GroceryListViewModel.insightsAfterRequest(): AiInsightsUiState {
        onRequestInsights()
        env.awaitUntil { state.value.insights.let { it != AiInsightsUiState.Loading && it != AiInsightsUiState.Idle } }
        return state.value.insights
    }

    // 1: the default

    @Test
    fun default_isAMonthEverywhere() {
        assertEquals(30, PlanningPeriod.DEFAULT_DAYS)
        assertEquals(listOf(7, 14, 21, 30), PlanningPeriod.options)
        assertEquals(30, PlanningPeriod.normalize(null))
        assertEquals(30, PlanningPeriod.normalize(0))
        assertEquals(30, PlanningPeriod.normalize(10))
        assertEquals(14, PlanningPeriod.normalize(14))

        assertEquals(30, request(8_000).days)
        assertEquals("generate?budget=8000&days=30", Routes.generate(8_000))
        assertEquals("generate?budget=8000&days=7", Routes.generate(8_000, 7))

        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        assertEquals(30, home.state.value.periodDays)

        // A list made without a period, or with one that is not offered, is a month's list
        assertEquals(30, list(generateList(8_000)).periodDays)
        assertEquals(30, list(generateList(8_000, 10)).periodDays)
        val direct = runBlocking { env.lists.createList(1_000, listOf(NewListItem(5, 1, 160))) }
        assertEquals(30, list(direct).periodDays)
    }

    // 2 to 4: shorter periods scale the monthly target

    @Test
    fun targetQuantity_isTheMonthlyTargetInProportionToTheDays() {
        // Rice: 6,000 g per person a month, four people, 24,000 g a month
        assertEquals(24_000.0, GroceryGenerator.requiredAmount(6_000.0, 4, 30), 0.0)
        assertEquals(5_600.0, GroceryGenerator.requiredAmount(6_000.0, 4, 7), 1e-6)
        assertEquals(11_200.0, GroceryGenerator.requiredAmount(6_000.0, 4, 14), 1e-6)
        assertEquals(16_800.0, GroceryGenerator.requiredAmount(6_000.0, 4, 21), 1e-6)
        // In half-kilo packs, rounded up: 12, 23, 34 and 48 packs
        assertEquals(listOf(12, 23, 34, 48), listOf(7, 14, 21, 30).map { GroceryGenerator.targetQuantity(6_000.0, 4, it, 500) })
        // Eggs are counted in pieces: 48 a month, and 11.2 for a week is 12
        assertEquals(listOf(12, 23, 34, 48), listOf(7, 14, 21, 30).map { GroceryGenerator.targetQuantity(12.0, 4, it, 1) })
        // The large-household rule still applies before the days do: 6 people count as 5.7
        assertEquals(6_000.0 * 5.7 * 14 / 30, GroceryGenerator.requiredAmount(6_000.0, 6, 14), 1e-6)

        // Every slot of the real basket, for every household size and period: the amount is
        // scaled first and only then turned into packs, never the other way round
        val packs = DemoCatalogSeed.items.associate { it.id to it.packAmount }
        DemoCatalogSeed.basket.forEach { slot ->
            val pack = packs.getValue(slot.itemIds.first())
            (1..20).forEach { household ->
                val monthly = slot.amountPerPerson * GroceryGenerator.effectivePeople(household)
                listOf(7, 14, 21).forEach { days ->
                    val expected = ceil(monthly * days / 30 / pack - 1e-9).toInt().coerceAtLeast(1)
                    assertEquals(expected, GroceryGenerator.targetQuantity(slot.amountPerPerson, household, days, pack))
                    // Enough for the period, and less than one pack over
                    assertTrue(expected * pack >= monthly * days / 30 - 1e-6)
                    assertTrue(expected == 1 || (expected - 1) * pack < monthly * days / 30)
                }
            }
        }
    }

    @Test
    fun shortPeriod_keepsWholePacksAndTheMinimumOfOne() {
        // 58 g for the week is still one pack, of whatever size the item comes in
        assertEquals(1, GroceryGenerator.targetQuantity(250.0, 1, 7, 250))
        assertEquals(1, GroceryGenerator.targetQuantity(250.0, 1, 7, 1000))
        assertEquals(1, GroceryGenerator.targetQuantity(250.0, 1, 30, 250))

        listOf(7, 14, 21, 30).forEach { days ->
            assertTrue(generated(50_000, days, household = 1).all { it.quantity >= 1 })
        }
    }

    @Test
    fun generatedQuantities_followThePeriodWhenTheBudgetDoesNotBind() {
        fun quantity(days: Int, vararg ids: Long) = generated(200_000, days).first { it.catalogItemId in ids }.quantity

        // With plenty of money every line is at its target (rice may have been upgraded to brown).
        // Rice in 500 g packs, eggs by the piece, lentils in 250 g packs.
        assertEquals(listOf(12, 23, 34, 48), listOf(7, 14, 21, 30).map { quantity(it, 101, 102) })
        assertEquals(listOf(12, 23, 34, 48), listOf(7, 14, 21, 30).map { quantity(it, 8) })
        assertEquals(listOf(4, 8, 12, 16), listOf(7, 14, 21, 30).map { quantity(it, 105) })

        // A shorter list never holds more of anything, and costs less
        val month = generated(200_000, 30).associate { it.catalogItemId to it.quantity }
        listOf(7, 14, 21).forEach { days ->
            val shorter = generated(200_000, days)
            shorter.forEach { assertTrue(it.quantity <= (month[it.catalogItemId] ?: Int.MAX_VALUE)) }
            assertTrue(shorter.sumOf { it.cost } < generated(200_000, 30).sumOf { it.cost })
        }
    }

    // 5: a month is the default, and holds the month's amounts

    @Test
    fun thirtyDays_isTheDefaultAndCoversTheMonthlyAmounts() {
        val packs = DemoCatalogSeed.items.associate { it.id to it.packAmount }
        DemoCatalogSeed.basket.forEach { slot ->
            val pack = packs.getValue(slot.itemIds.first())
            (1..20).forEach { household ->
                val monthly = slot.amountPerPerson * GroceryGenerator.effectivePeople(household)
                val quantity = GroceryGenerator.targetQuantity(slot.amountPerPerson, household, packAmount = pack)
                assertEquals(quantity, GroceryGenerator.targetQuantity(slot.amountPerPerson, household, 30, pack))
                // Never short of the month's amount, and less than one pack over it
                assertTrue(quantity * pack >= monthly - 1e-6)
                assertTrue(quantity == 1 || (quantity - 1) * pack < monthly)
            }
        }
        listOf(900, 2_000, 8_000, 12_000, 60_000).forEach { budget ->
            listOf(1, 4, 9).forEach { household ->
                assertEquals(
                    GroceryGenerator.generate(request(budget, household = household)),
                    GroceryGenerator.generate(request(budget, 30, household))
                )
            }
        }
        // And through the screens
        assertEquals(lines(generateList(8_000)), lines(generateList(8_000, 30)))
    }

    // 6: the period is saved with the list

    @Test
    fun chosenPeriod_isSavedOnTheListAndUsedToBuildIt() {
        listOf(7, 14, 21, 30).forEach { days ->
            val id = generateList(60_000, days)

            assertEquals(days, list(id).periodDays)
            assertEquals(60_000, list(id).budget)
            assertEquals(generated(60_000, days).map { Triple(it.catalogItemId, it.quantity, it.unitPrice) }, lines(id))
        }
        // The latest list carries the period it was made with, not the one before it
        generateList(60_000, 21)
        assertEquals(21, runBlocking { env.lists.latestList() }!!.periodDays)
    }

    @Test
    fun home_remembersTheSelectedPeriodAndOnlyAcceptsTheOfferedOnes() {
        val saved = SavedStateHandle()
        val home = HomeViewModel(env.accounts, env.profiles, env.lists, saved)

        home.onPeriodSelected(7)
        assertEquals(7, home.state.value.periodDays)
        home.onPeriodSelected(10)
        home.onPeriodSelected(0)
        assertEquals(7, home.state.value.periodDays)

        // Survives the screen being recreated
        assertEquals(7, HomeViewModel(env.accounts, env.profiles, env.lists, saved).state.value.periodDays)
        home.onPeriodSelected(30)
        assertEquals(30, home.state.value.periodDays)
    }

    // 9: nutrition is worked out over the list's own days

    @Test
    fun nutrition_usesTheListsPeriodNotAlwaysThirtyDays() {
        val week = generateList(60_000, 7)
        val entries = items(week)

        val overAWeek = NutritionAnalyzer.analyze(entries, 4, 7)!!
        val overAMonth = NutritionAnalyzer.analyze(entries, 4, 30)!!
        assertEquals(7, overAWeek.days)
        assertEquals(30, overAMonth.days)
        // The same food shared over fewer days is more per day, by exactly 30/7
        assertEquals(overAMonth.total, overAWeek.total)
        assertEquals(
            overAMonth.perPersonPerDay.energyKcal * 30 / 7, overAWeek.perPersonPerDay.energyKcal, 1e-6
        )
        assertEquals(overAMonth.coverageOf(Nutrient.Iron) * 30 / 7, overAWeek.coverageOf(Nutrient.Iron), 1e-9)
        // Leaving the days out is still a month
        assertEquals(overAMonth, NutritionAnalyzer.analyze(entries, 4))

        // The screens read the list's period
        val nutrition = NutritionViewModel(SavedStateHandle(mapOf(Routes.ARG_LIST_ID to week)), env.lists, env.profiles)
        env.awaitUntil { !nutrition.state.value.loading }
        assertEquals(overAWeek, nutrition.state.value.analysis)

        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        env.awaitUntil { home.state.value.currentList?.listId == week }
        assertEquals(overAWeek.score.value, home.state.value.currentList!!.nutritionScore)

        // A week's list scores like a week's list, not like a month's list a quarter the size
        assertTrue(overAWeek.score.value > overAMonth.score.value)
    }

    // 10: budget arithmetic is unchanged

    @Test
    fun budgetArithmetic_isUnchangedForEveryPeriod() {
        listOf(7, 14, 21, 30).forEach { days ->
            listOf(900, 3_000, 8_000).forEach { budget ->
                val result = GroceryGenerator.generate(request(budget, days))
                if (result is GenerationResult.Success) {
                    // The reserve is kept back and the total is the plain sum
                    assertTrue(result.total <= budget - budget * GroceryGenerator.RESERVE_PERCENT / 100)
                    assertEquals(result.items.sumOf { it.quantity * it.unitPrice }, result.total)
                    result.items.forEach { assertEquals(rangpur.getValue(it.catalogItemId), it.unitPrice) }
                }
            }
        }

        val id = generateList(3_000, 7)
        val state = listScreen(id).state.value
        val sum = items(id).sumOf { it.item.quantity * it.item.unitPrice }
        assertEquals(sum, state.total)
        assertEquals(ListTotals(sum, 3_000), state.totals)
        assertEquals(3_000 - sum, state.remaining)
    }

    @Test
    fun minimumBudget_isInProportionToThePeriod() {
        assertEquals(500, BudgetRules.minimum())
        assertEquals(listOf(150, 250, 350, 500), listOf(7, 14, 21, 30).map { BudgetRules.minimum(it) })

        // A month is judged as before
        assertEquals(BudgetError.BelowMinimum, BudgetRules.validate("499"))
        assertNull(BudgetRules.validate("500"))
        // Tk 200 is too little for a month and enough to ask for a week
        assertEquals(BudgetError.BelowMinimum, BudgetRules.validate("200", 30))
        assertNull(BudgetRules.validate("200", 7))
        assertEquals(BudgetError.BelowMinimum, BudgetRules.validate("149", 7))
        assertEquals(BudgetError.AboveMaximum, BudgetRules.validate("1000000", 7))

        val home = HomeViewModel(env.accounts, env.profiles, env.lists, SavedStateHandle())
        home.onBudgetChange("200")
        home.onGenerate()
        assertNull(home.state.value.generateRequest)
        assertEquals(BudgetError.BelowMinimum, home.state.value.budgetError)
        assertEquals(500, home.state.value.minimumBudget)

        home.onPeriodSelected(7)
        assertNull(home.state.value.budgetError)
        assertEquals(150, home.state.value.minimumBudget)
        home.onGenerate()
        assertEquals(200, home.state.value.generateRequest)
    }

    // 11 to 13: allergy and health-condition filtering are the same for every period

    @Test
    fun allergyAndConditionFiltering_applyToAShortList() {
        runBlocking {
            env.profiles.save(
                "Rangpur Division", 4, setOf(Allergen.Eggs), setOf(HealthCondition.Diabetes),
                customAllergies = listOf("groundnut", "Kiwi")
            )
        }

        listOf(7, 14, 21, 30).forEach { days ->
            val saved = items(generateList(60_000, days))

            assertTrue(saved.isNotEmpty())
            assertTrue(saved.none { Allergen.Eggs in it.catalog.allergens })
            // The typed allergy the matcher knows; the unknown one leaves nothing out
            assertTrue(saved.none { Allergen.Peanuts in it.catalog.allergens })
            // The diabetes rule: no almost-pure carbohydrate
            assertTrue(saved.none { it.catalog.carbsPer100g >= 90.0 })
        }
        // The same items are chosen whatever the period; only the amounts differ
        assertEquals(
            items(generateList(200_000, 30)).map { it.catalog.id },
            items(generateList(200_000, 7)).map { it.catalog.id }
        )
    }

    // 14 to 17: the AI layer sees the real period and otherwise behaves as before

    private val answer = AiResponse(
        summary = "The list leans on staples.",
        reasoning = "Two food groups are missing.",
        recommendations = listOf(AiRecommendation(AiRecommendationType.Variety, "Add variety", "Fruit is absent.")),
        prioritizedItems = listOf(
            AiPrioritizedItem("Lentils (Masoor)", AiPriority.High, "Covers protein."),
            AiPrioritizedItem("Dragon fruit", AiPriority.High, "Not on the list.")
        )
    )

    @Test
    fun aiContext_reportsTheListsPeriodAndBudget() {
        val engine = FakeAiEngine { AiResult.Success(answer) }
        val week = generateList(3_000, 7)

        val ready = listScreen(week, engine).insightsAfterRequest() as AiInsightsUiState.Ready

        val context = engine.requests.single().context
        assertEquals(7, context.planningPeriodDays)
        assertEquals(3_000, context.budget)
        assertEquals(7, context.nutrition!!.daysCovered)
        assertEquals(items(week).sumOf { it.item.quantity * it.item.unitPrice }, context.listTotal)

        val json = JSONObject(AiRequestJson.prompt(engine.requests.single()).substringAfter("Context (JSON):\n"))
        assertEquals(7, json.getInt("planningPeriodDays"))
        assertEquals(3_000, json.getInt("budget"))
        assertEquals(7, json.getJSONObject("nutrition").getInt("daysCovered"))
        assertFalse(json.has("monthlyBudget"))

        // Phase 10 and 11 still work on it: the answer is shown and only the real item is a pick
        assertEquals("The list leans on staples.", ready.response.summary)
        assertEquals(listOf("Lentils (Masoor)"), ready.picks.map { it.itemName })

        // A month's list says thirty
        val monthEngine = FakeAiEngine { AiResult.Success(answer) }
        listScreen(generateList(8_000), monthEngine).insightsAfterRequest()
        assertEquals(30, monthEngine.requests.single().context.planningPeriodDays)
        assertEquals(30, monthEngine.requests.single().context.nutrition!!.daysCovered)
    }

    @Test
    fun aiFailure_leavesAShortListWorking() {
        val week = generateList(3_000, 7)
        val before = items(week)
        val viewModel = listScreen(week, FakeAiEngine { AiResult.Failure(AiError.Network) })

        assertEquals(AiInsightsUiState.Failed(AiInsightsFailure.Connection), viewModel.insightsAfterRequest())

        assertEquals(before, viewModel.state.value.items)
        assertEquals(before, items(week))
        assertEquals(7, viewModel.state.value.list!!.periodDays)
    }
}
