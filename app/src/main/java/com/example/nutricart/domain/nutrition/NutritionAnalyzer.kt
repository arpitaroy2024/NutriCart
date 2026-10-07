package com.example.nutricart.domain.nutrition

import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.FoodCategory
import kotlin.math.roundToInt

// Everything the nutrition screen shows about one list, as plain figures
data class NutritionAnalysis(
    val householdSize: Int,
    val days: Int,
    val total: NutritionFacts,
    // The list shared evenly between the household over the days it covers
    val perPersonPerDay: NutritionFacts,
    // Share of the daily reference per nutrient; 1.0 is the reference, and it may exceed 1
    val coverage: Map<Nutrient, Double>,
    val score: NutritionScore,
    val presentGroups: List<FoodCategory>,
    val missingGroups: List<FoodCategory>,
    val insights: List<NutritionInsight>,
    val skippedItems: Int
) {
    fun coverageOf(nutrient: Nutrient): Double = coverage[nutrient] ?: 0.0

    fun coveragePercent(nutrient: Nutrient): Int = (coverageOf(nutrient) * 100).roundToInt()

    // Nutrients under the gap threshold, weakest first
    val gaps: List<Nutrient>
        get() = Nutrient.entries
            .filter { coverageOf(it) < NutritionReference.GAP_THRESHOLD }
            .sortedBy { coverageOf(it) }

    // The single largest gap, which is the only one called out
    val largestGap: Nutrient? get() = gaps.firstOrNull()

    // How far below the reference that nutrient is, in percent
    val largestGapShortfallPercent: Int?
        get() = largestGap?.let { 100 - coveragePercent(it) }

    val noMajorGaps: Boolean get() = gaps.isEmpty() && score.value >= NO_MAJOR_GAPS_SCORE

    companion object {
        const val NO_MAJOR_GAPS_SCORE = 90
    }
}

// Calculator, scorer and insights run in order. Kept as three separate parts so each can
// be tested, changed or built upon on its own.
object NutritionAnalyzer {

    // Null when there is nothing to analyse: an empty list, no household, or no item with usable data
    // days is how long the list is meant to last: the list's own planning period
    fun analyze(
        entries: List<ListItemWithCatalog>,
        householdSize: Int,
        days: Int = NutritionReference.DAYS_COVERED
    ): NutritionAnalysis? {
        if (householdSize <= 0 || days <= 0) return null
        val list = NutritionCalculator.totals(entries)
        if (list.countedItems == 0) return null

        val perPersonPerDay = list.perPersonPerDay(householdSize, days)
        val coverage = NutritionScorer.coverage(perPersonPerDay)
        val shares = NutritionScorer.macroShares(perPersonPerDay)
        return NutritionAnalysis(
            householdSize = householdSize,
            days = days,
            total = list.total,
            perPersonPerDay = perPersonPerDay,
            coverage = coverage,
            score = NutritionScorer.score(perPersonPerDay, list.categories),
            presentGroups = NutritionReference.foodGroups.filter { it in list.categories },
            missingGroups = NutritionReference.foodGroups.filterNot { it in list.categories },
            insights = NutritionInsights.derive(list, coverage, shares),
            skippedItems = list.skippedItems
        )
    }
}
