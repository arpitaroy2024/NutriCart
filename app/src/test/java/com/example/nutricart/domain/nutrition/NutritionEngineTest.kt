package com.example.nutricart.domain.nutrition

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.domain.GenerationRequest
import com.example.nutricart.domain.GenerationResult
import com.example.nutricart.domain.GroceryGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Calculator, scorer, insights and analyzer: pure arithmetic, no Android and no database
class NutritionEngineTest {

    private val tolerance = 1e-6
    private var nextId = 1L

    private fun food(
        category: FoodCategory = FoodCategory.Grains,
        unit: String = "kg",
        gramsPerUnit: Int = 1000,
        kcal: Double = 0.0,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        fat: Double = 0.0,
        iron: Double = 0.0
    ) = CatalogItemEntity(
        id = nextId++, name = "Food", category = category, unit = unit, gramsPerUnit = gramsPerUnit,
        nutrientTag = NutrientTag.Carbs, caloriesPer100g = kcal, proteinPer100g = protein, carbsPer100g = carbs,
        fatPer100g = fat, ironMgPer100g = iron, allergens = emptySet(), flaggedConditions = emptySet()
    )

    private fun entry(item: CatalogItemEntity, quantity: Int) = ListItemWithCatalog(
        item = ListItemEntity(id = item.id, listId = 1, catalogItemId = item.id, quantity = quantity, unitPrice = 10),
        catalog = item
    )

    private val seed = DemoCatalogSeed.items.associateBy { it.id }

    private fun seeded(vararg quantities: Pair<Long, Int>) = quantities.map { (id, quantity) -> entry(seed.getValue(id), quantity) }

    // A list that gives one person exactly the daily reference for a month, from all five food groups
    private fun referenceList(): List<ListItemWithCatalog> {
        val days = NutritionReference.DAYS_COVERED
        val daily = NutritionReference.daily
        // One kilogram of a made-up food per group; together they hold a month's reference
        fun share(category: FoodCategory, part: Double) = entry(
            food(
                category = category, gramsPerUnit = 1000,
                kcal = daily.energyKcal * days * part / 10, protein = daily.proteinG * days * part / 10,
                carbs = daily.carbsG * days * part / 10, fat = daily.fatG * days * part / 10,
                iron = daily.ironMg * days * part / 10
            ),
            quantity = 1
        )
        return NutritionReference.foodGroups.map { share(it, 0.2) }
    }

    // Units and quantities

    @Test
    fun contribution_scalesPer100gValuesByTheWeight() {
        // 100 kcal per 100 g, sold by the gram: 500 g is 500 kcal
        val byGram = food(unit = "g", gramsPerUnit = 1, kcal = 100.0, protein = 10.0)

        val facts = NutritionCalculator.contribution(byGram, 500)!!

        assertEquals(500.0, facts.energyKcal, tolerance)
        assertEquals(50.0, facts.proteinG, tolerance)
    }

    @Test
    fun contribution_kilogramIsAThousandGramsNotOneUnit() {
        val byKilo = food(unit = "kg", gramsPerUnit = 1000, kcal = 365.0, protein = 7.1, carbs = 80.0, fat = 0.7, iron = 0.8)

        val facts = NutritionCalculator.contribution(byKilo, 2)!!

        assertEquals(2000.0, NutritionCalculator.gramsOf(byKilo, 2)!!, tolerance)
        assertEquals(7300.0, facts.energyKcal, tolerance)
        assertEquals(142.0, facts.proteinG, tolerance)
        assertEquals(1600.0, facts.carbsG, tolerance)
        assertEquals(14.0, facts.fatG, tolerance)
        assertEquals(16.0, facts.ironMg, tolerance)
    }

    @Test
    fun contribution_pieceUsesTheWeightOfOnePiece() {
        // Eggs in the demo catalog: 50 g each, 143 kcal and 12.6 g protein per 100 g
        val eggs = seed.getValue(8)
        assertEquals("pcs", eggs.unit)

        val dozen = NutritionCalculator.contribution(eggs, 12)!!

        assertEquals(600.0, NutritionCalculator.gramsOf(eggs, 12)!!, tolerance)
        assertEquals(858.0, dozen.energyKcal, tolerance)
        assertEquals(75.6, dozen.proteinG, tolerance)
    }

    @Test
    fun contribution_litreUsesTheItemsOwnWeight() {
        // A litre of oil weighs 920 g, a litre of milk 1030 g
        val oil = seed.getValue(33)
        val milk = seed.getValue(31)
        assertEquals("L", oil.unit)

        assertEquals(920 * 8.84, NutritionCalculator.contribution(oil, 1)!!.energyKcal, tolerance)
        assertEquals(920.0, NutritionCalculator.contribution(oil, 1)!!.fatG, tolerance)
        assertEquals(1030 * 0.61, NutritionCalculator.contribution(milk, 1)!!.energyKcal, tolerance)
    }

    @Test
    fun contribution_isProportionalToQuantity() {
        val item = food(kcal = 120.0, protein = 3.0, carbs = 20.0, fat = 2.0, iron = 1.5)

        val one = NutritionCalculator.contribution(item, 1)!!
        val seven = NutritionCalculator.contribution(item, 7)!!

        assertEquals(one * 7.0, seven)
    }

    @Test
    fun contribution_zeroOrNegativeQuantityCountsForNothing() {
        val item = food(kcal = 120.0)

        assertNull(NutritionCalculator.contribution(item, 0))
        assertNull(NutritionCalculator.contribution(item, -3))
    }

    @Test
    fun contribution_itemWithNoUsableWeightCountsForNothing() {
        assertNull(NutritionCalculator.contribution(food(gramsPerUnit = 0, kcal = 120.0), 2))
        assertNull(NutritionCalculator.contribution(food(gramsPerUnit = -5, kcal = 120.0), 2))
    }

    @Test
    fun contribution_itemWithNoNutritionDataCountsForNothing() {
        assertNull(NutritionCalculator.contribution(food(), 5))
        // Negative figures in the data are treated as zero, not subtracted
        assertNull(NutritionCalculator.contribution(food(kcal = -50.0, protein = -1.0), 5))
    }

    // Totals

    @Test
    fun totals_sumEveryNutrientOverTheList() {
        val rice = food(FoodCategory.Grains, kcal = 365.0, protein = 7.1, carbs = 80.0, fat = 0.7, iron = 0.8)
        val lentils = food(FoodCategory.Protein, kcal = 353.0, protein = 25.0, carbs = 60.0, fat = 1.0, iron = 7.0)
        val eggs = food(FoodCategory.Protein, unit = "pcs", gramsPerUnit = 50, kcal = 143.0, protein = 12.6, carbs = 0.7, fat = 9.5, iron = 1.8)

        val list = NutritionCalculator.totals(listOf(entry(rice, 2), entry(lentils, 1), entry(eggs, 10)))

        assertEquals(3, list.countedItems)
        assertEquals(0, list.skippedItems)
        assertEquals(7300 + 3530 + 715.0, list.total.energyKcal, tolerance)
        assertEquals(142 + 250 + 63.0, list.total.proteinG, tolerance)
        assertEquals(1600 + 600 + 3.5, list.total.carbsG, tolerance)
        assertEquals(14 + 10 + 47.5, list.total.fatG, tolerance)
        assertEquals(16 + 70 + 9.0, list.total.ironMg, tolerance)
        // Energy is tracked per category, with both protein items together
        assertEquals(7300.0, list.energyByCategory.getValue(FoodCategory.Grains), tolerance)
        assertEquals(4245.0, list.energyByCategory.getValue(FoodCategory.Protein), tolerance)
        assertEquals(setOf(FoodCategory.Grains, FoodCategory.Protein), list.categories)
    }

    @Test
    fun totals_skipUncountableItemsButKeepTheRest() {
        val good = food(FoodCategory.Grains, kcal = 100.0)
        val noData = food(FoodCategory.Veg)
        val noWeight = food(FoodCategory.Fruit, gramsPerUnit = 0, kcal = 50.0)

        val list = NutritionCalculator.totals(listOf(entry(good, 1), entry(noData, 3), entry(noWeight, 2)))

        assertEquals(1, list.countedItems)
        assertEquals(2, list.skippedItems)
        assertEquals(1000.0, list.total.energyKcal, tolerance)
        // The groups are still on the list
        assertEquals(setOf(FoodCategory.Grains, FoodCategory.Veg, FoodCategory.Fruit), list.categories)
    }

    @Test
    fun totals_ofAnEmptyListAreZero() {
        val list = NutritionCalculator.totals(emptyList())

        assertEquals(NutritionFacts(), list.total)
        assertEquals(0, list.countedItems)
        assertTrue(list.categories.isEmpty())
    }

    @Test
    fun perPersonPerDay_sharesTheListOverPeopleAndDays() {
        val list = NutritionCalculator.totals(listOf(entry(food(kcal = 100.0, protein = 10.0), 240)))
        assertEquals(240_000.0, list.total.energyKcal, tolerance)

        // Four people for thirty days
        val each = list.perPersonPerDay(4, 30)
        assertEquals(2000.0, each.energyKcal, tolerance)
        assertEquals(200.0, each.proteinG, tolerance)

        // The same list feeds each of eight people half as much
        assertEquals(1000.0, list.perPersonPerDay(8, 30).energyKcal, tolerance)
        assertEquals(NutritionFacts(), list.perPersonPerDay(0, 30))
    }

    // Score

    @Test
    fun score_listThatMeetsEveryReferenceScoresOneHundred() {
        val analysis = NutritionAnalyzer.analyze(referenceList(), householdSize = 1)!!

        Nutrient.entries.forEach { assertEquals(1.0, analysis.coverageOf(it), 1e-9) }
        assertEquals(1.0, analysis.score.adequacy, 1e-9)
        assertEquals(1.0, analysis.score.variety, 1e-9)
        // The reference amounts themselves give 14% protein, 59% carbohydrate, 27% fat: all in range
        assertEquals(1.0, analysis.score.balance, 1e-9)
        assertEquals(100, analysis.score.value)
        assertEquals(ScoreBand.Excellent, analysis.score.band)
        assertTrue(analysis.gaps.isEmpty())
        assertTrue(analysis.noMajorGaps)
    }

    @Test
    fun score_isTheWeightedSumOfItsThreeParts() {
        // Half the reference for two people instead of one: adequacy 0.5, balance and variety unchanged
        val analysis = NutritionAnalyzer.analyze(referenceList(), householdSize = 2)!!

        assertEquals(0.5, analysis.score.adequacy, 1e-9)
        assertEquals(60 * 0.5 + 25 + 15, analysis.score.value.toDouble(), 0.0)
        assertEquals(ScoreBand.Good, analysis.score.band)
        // Every nutrient is under 60% now
        assertEquals(5, analysis.gaps.size)
        assertFalse(analysis.noMajorGaps)
    }

    @Test
    fun score_surplusOfOneNutrientDoesNotHideAShortageOfAnother() {
        val coverage = mapOf(
            Nutrient.Energy to 3.0, Nutrient.Protein to 0.2, Nutrient.Carbohydrate to 3.0,
            Nutrient.Fat to 1.0, Nutrient.Iron to 1.0
        )

        // Each counts to 100% at most: (1 + 0.2 + 1 + 1 + 1) / 5
        assertEquals(0.84, NutritionScorer.adequacy(coverage), 1e-9)
    }

    @Test
    fun score_balanceFallsAsAMacroLeavesItsRange() {
        val range = NutritionReference.fatShare   // 20% to 35%
        assertEquals(1.0, NutritionScorer.rangeScore(0.20, range), 1e-9)
        assertEquals(1.0, NutritionScorer.rangeScore(0.35, range), 1e-9)
        assertEquals(0.5, NutritionScorer.rangeScore(0.45, range), 1e-9)
        assertEquals(0.5, NutritionScorer.rangeScore(0.10, range), 1e-9)
        assertEquals(0.0, NutritionScorer.rangeScore(0.55, range), 1e-9)
        assertEquals(0.0, NutritionScorer.rangeScore(0.90, range), 1e-9)
        assertEquals(0.0, NutritionScorer.balance(null), 0.0)
    }

    @Test
    fun score_macroSharesUseFourFourAndNineKilocaloriesPerGram() {
        val shares = NutritionScorer.macroShares(NutritionFacts(proteinG = 50.0, carbsG = 100.0, fatG = 200.0 / 9))!!

        // 200 + 400 + 200 kcal
        assertEquals(0.25, shares.protein, 1e-9)
        assertEquals(0.50, shares.carbohydrate, 1e-9)
        assertEquals(0.25, shares.fat, 1e-9)
        assertNull(NutritionScorer.macroShares(NutritionFacts(energyKcal = 500.0)))
    }

    @Test
    fun score_riceOnlyListScoresWellBelowABalancedOne() {
        // The same energy for one person, once from rice alone and once from the reference list
        val riceOnly = NutritionAnalyzer.analyze(seeded(2L to 16), householdSize = 1)!!
        val balanced = NutritionAnalyzer.analyze(referenceList(), householdSize = 1)!!

        assertEquals(0.2, riceOnly.score.variety, 1e-9)
        assertTrue(riceOnly.score.balance < 1.0)
        assertTrue(riceOnly.score.value < balanced.score.value - 20)
        assertTrue(Nutrient.Fat in riceOnly.gaps)
    }

    @Test
    fun score_bandsStartAtTheirThresholds() {
        assertEquals(ScoreBand.Low, NutritionScorer.bandFor(0))
        assertEquals(ScoreBand.Low, NutritionScorer.bandFor(49))
        assertEquals(ScoreBand.Fair, NutritionScorer.bandFor(50))
        assertEquals(ScoreBand.Fair, NutritionScorer.bandFor(69))
        assertEquals(ScoreBand.Good, NutritionScorer.bandFor(70))
        assertEquals(ScoreBand.Good, NutritionScorer.bandFor(84))
        assertEquals(ScoreBand.Excellent, NutritionScorer.bandFor(85))
        assertEquals(ScoreBand.Excellent, NutritionScorer.bandFor(100))
    }

    @Test
    fun score_staysBetweenZeroAndOneHundredForEveryGeneratedList() {
        fun prices(region: String) = DemoCatalogSeed.prices.filter { it.region == region }.associate { it.catalogItemId to it.price }
        for (household in listOf(1, 4, 12, 20)) for (budget in listOf(500, 3_000, 12_000, 60_000, 999_999)) {
            val result = GroceryGenerator.generate(
                GenerationRequest(budget, household, DemoCatalogSeed.items, prices("Rangpur Division"), DemoCatalogSeed.basket)
            ) as GenerationResult.Success
            val entries = result.items.map { entry(seed.getValue(it.catalogItemId), it.quantity) }

            val analysis = NutritionAnalyzer.analyze(entries, household)!!

            assertTrue("$household people, Tk $budget: ${analysis.score.value}", analysis.score.value in 0..100)
            listOf(analysis.score.adequacy, analysis.score.balance, analysis.score.variety)
                .forEach { assertTrue(it in 0.0..1.0) }
            assertTrue(analysis.total.energyKcal > 0)
        }
    }

    @Test
    fun score_sameListAlwaysGivesTheSameAnalysis() {
        val entries = seeded(2L to 24, 5L to 4, 8L to 48, 20L to 8, 18L to 4, 28L to 48, 31L to 16, 33L to 3)

        val first = NutritionAnalyzer.analyze(entries, 4)
        repeat(10) { assertEquals(first, NutritionAnalyzer.analyze(entries, 4)) }
        // The order of the list does not matter either
        assertEquals(first!!.score, NutritionAnalyzer.analyze(entries.reversed(), 4)!!.score)
    }

    @Test
    fun score_nothingToAnalyseGivesNoScoreInsteadOfZero() {
        assertNull(NutritionAnalyzer.analyze(emptyList(), 4))
        assertNull(NutritionAnalyzer.analyze(listOf(entry(food(), 3)), 4))
        assertNull(NutritionAnalyzer.analyze(seeded(2L to 5), 0))
    }

    // Food groups

    @Test
    fun variety_countsEachFoodGroupOnceAndIgnoresOilsAndPantry() {
        assertEquals(0.0, NutritionScorer.variety(emptySet()), 0.0)
        assertEquals(0.0, NutritionScorer.variety(setOf(FoodCategory.Oils, FoodCategory.Pantry)), 0.0)
        assertEquals(0.4, NutritionScorer.variety(setOf(FoodCategory.Grains, FoodCategory.Veg, FoodCategory.Oils)), 1e-9)
        assertEquals(1.0, NutritionScorer.variety(FoodCategory.entries.toSet()), 0.0)

        // Three protein items and two grains are still two groups
        val analysis = NutritionAnalyzer.analyze(seeded(5L to 1, 8L to 12, 9L to 1, 1L to 2, 2L to 2), 1)!!
        assertEquals(listOf(FoodCategory.Grains, FoodCategory.Protein), analysis.presentGroups)
        assertEquals(listOf(FoodCategory.Veg, FoodCategory.Fruit, FoodCategory.Dairy), analysis.missingGroups)
        assertEquals(0.4, analysis.score.variety, 1e-9)
    }

    // Gaps

    @Test
    fun gaps_areNutrientsUnderSixtyPercentWeakestFirst() {
        // Rice for one person: plenty of carbohydrate, little fat and iron
        val analysis = NutritionAnalyzer.analyze(seeded(2L to 16), householdSize = 1)!!

        assertTrue(analysis.coverageOf(Nutrient.Carbohydrate) > 1.0)
        assertEquals(analysis.gaps.sortedBy { analysis.coverageOf(it) }, analysis.gaps)
        assertTrue(analysis.gaps.all { analysis.coverageOf(it) < NutritionReference.GAP_THRESHOLD })
        assertEquals(analysis.gaps.first(), analysis.largestGap)
        assertEquals(100 - analysis.coveragePercent(analysis.largestGap!!), analysis.largestGapShortfallPercent)
        assertFalse(analysis.noMajorGaps)
    }

    @Test
    fun gaps_exactlySixtyPercentIsNotAGap() {
        val days = NutritionReference.DAYS_COVERED
        val item = food(FoodCategory.Grains, gramsPerUnit = 100, kcal = 2000.0 * days * 0.6, protein = 70.0 * days * 0.6,
            carbs = 300.0 * days * 0.6, fat = 60.0 * days * 0.6, iron = 18.0 * days * 0.6)

        val analysis = NutritionAnalyzer.analyze(listOf(entry(item, 1)), 1)!!

        assertEquals(0.6, analysis.coverageOf(Nutrient.Iron), 1e-9)
        assertTrue(analysis.gaps.isEmpty())
        assertNull(analysis.largestGap)
        // No gaps, but the score is too low for "no major gaps"
        assertFalse(analysis.noMajorGaps)
    }

    // Insights

    @Test
    fun insights_alwaysStartWithTheFoodGroups() {
        val analysis = NutritionAnalyzer.analyze(seeded(2L to 10, 5L to 2), 1)!!

        assertEquals(
            NutritionInsight.Variety(
                present = listOf(FoodCategory.Grains, FoodCategory.Protein),
                missing = listOf(FoodCategory.Veg, FoodCategory.Fruit, FoodCategory.Dairy)
            ),
            analysis.insights.first()
        )
    }

    @Test
    fun insights_riceHeavyListSaysGrainsDominateAndCarbohydrateIsHigh() {
        val analysis = NutritionAnalyzer.analyze(seeded(2L to 16, 18L to 1), 1)!!
        val insights = analysis.insights

        val grains = insights.filterIsInstance<NutritionInsight.GrainsDominant>().single()
        assertTrue(grains.percent > 90)
        // Of the macronutrients out of range, only the one furthest out is mentioned
        val macro = insights.filterIsInstance<NutritionInsight.MacroOutOfRange>().single()
        assertEquals(Nutrient.Carbohydrate, macro.nutrient)
        assertFalse(macro.below)
        assertTrue(macro.percent > 65)
        assertEquals(45, macro.rangeFromPercent)
        assertEquals(65, macro.rangeToPercent)
        // Nothing is said about protein being well covered, because it is not
        assertTrue(insights.none { it is NutritionInsight.ProteinWellCovered })
    }

    @Test
    fun insights_oilAndSugarHeavyListSaysSo() {
        val analysis = NutritionAnalyzer.analyze(seeded(33L to 3, 38L to 3, 2L to 2), 1)!!

        val oils = analysis.insights.filterIsInstance<NutritionInsight.OilsAndSugarHigh>().single()
        assertTrue(oils.percent > 30)
        assertTrue(analysis.insights.none { it is NutritionInsight.GrainsDominant })
    }

    @Test
    fun insights_balancedListGetsNoWarningsItHasNotEarned() {
        val insights = NutritionAnalyzer.analyze(referenceList(), 1)!!.insights

        assertEquals(
            listOf(
                NutritionInsight.Variety(NutritionReference.foodGroups, emptyList()),
                NutritionInsight.ProteinWellCovered(100)
            ),
            insights
        )
    }

    @Test
    fun insights_reportSurplusEnergyAndUncountedItems() {
        // The reference list for one person, doubled, plus an item with no data
        val doubled = referenceList().map { it.copy(item = it.item.copy(quantity = 2)) } + entry(food(FoodCategory.Veg), 1)

        val insights = NutritionAnalyzer.analyze(doubled, 1)!!.insights

        assertTrue(NutritionInsight.EnergyAboveReference(200) in insights)
        assertTrue(NutritionInsight.ItemsNotCounted(1) in insights)
    }

    @Test
    fun insights_areNeverMoreThanFour() {
        val heavy = seeded(33L to 30, 38L to 20, 2L to 1) + entry(food(), 1)

        val insights = NutritionAnalyzer.analyze(heavy, 1)!!.insights

        assertTrue(insights.size <= NutritionInsights.MAX_INSIGHTS)
        assertNotNull(insights.firstOrNull { it is NutritionInsight.Variety })
    }

    // A generated list

    @Test
    fun generatedListForFour_isAnalysedPerPersonPerDay() {
        val prices = DemoCatalogSeed.prices.filter { it.region == "Rangpur Division" }.associate { it.catalogItemId to it.price }
        val result = GroceryGenerator.generate(
            GenerationRequest(12_000, 4, DemoCatalogSeed.items, prices, DemoCatalogSeed.basket)
        ) as GenerationResult.Success
        val entries = result.items.map { entry(seed.getValue(it.catalogItemId), it.quantity) }

        val analysis = NutritionAnalyzer.analyze(entries, 4)!!

        assertEquals(4, analysis.householdSize)
        assertEquals(30, analysis.days)
        // total = per person per day x 4 people x 30 days
        Nutrient.entries.forEach {
            assertEquals(analysis.total.amountOf(it), analysis.perPersonPerDay.amountOf(it) * 120, 1e-6)
        }
        // A full basket for four gives each person a plausible day: between 1,200 and 2,800 kcal
        assertTrue("${analysis.perPersonPerDay.energyKcal}", analysis.perPersonPerDay.energyKcal in 1200.0..2800.0)
        assertEquals(NutritionReference.foodGroups, analysis.presentGroups)
        assertEquals(0, analysis.skippedItems)
    }
}
