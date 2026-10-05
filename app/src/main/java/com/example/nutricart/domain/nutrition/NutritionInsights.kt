package com.example.nutricart.domain.nutrition

import com.example.nutricart.data.model.FoodCategory
import kotlin.math.roundToInt

// An observation about a list, as data. The screen turns it into a sentence; a later layer
// could reword or rank these, but it would start from the same figures.
sealed interface NutritionInsight {
    // Which of the five food groups are on the list and which are not
    data class Variety(val present: List<FoodCategory>, val missing: List<FoodCategory>) : NutritionInsight

    // Grains supply this percentage of the list's energy
    data class GrainsDominant(val percent: Int) : NutritionInsight

    // Oils and pantry items (sugar, snacks) together supply this percentage of the energy
    data class OilsAndSugarHigh(val percent: Int) : NutritionInsight

    // A macronutrient supplies a share of energy outside its reference range
    data class MacroOutOfRange(
        val nutrient: Nutrient,
        val percent: Int,
        val below: Boolean,
        val rangeFromPercent: Int,
        val rangeToPercent: Int
    ) : NutritionInsight

    data class ProteinWellCovered(val percent: Int) : NutritionInsight

    // The list holds clearly more energy than the household's reference for the period
    data class EnergyAboveReference(val percent: Int) : NutritionInsight

    // Some items could not be counted
    data class ItemsNotCounted(val count: Int) : NutritionInsight
}

/*
 * Picks the observations worth showing. Every rule compares a calculated figure with a
 * fixed threshold, so the same list always gives the same observations, and nothing is
 * said that the figures do not support. The rules describe the list; they do not refer
 * to the profile's health conditions or allergies, and they give no advice.
 */
object NutritionInsights {
    const val MAX_INSIGHTS = 4

    const val GRAINS_DOMINANT_ABOVE = 0.60
    const val OILS_AND_SUGAR_HIGH_ABOVE = 0.30
    const val PROTEIN_WELL_COVERED_FROM = 0.90
    const val ENERGY_ABOVE_REFERENCE_FROM = 1.30

    fun derive(list: ListNutrition, coverage: Map<Nutrient, Double>, shares: MacroShares?): List<NutritionInsight> {
        val insights = mutableListOf<NutritionInsight>()

        insights += NutritionInsight.Variety(
            present = NutritionReference.foodGroups.filter { it in list.categories },
            missing = NutritionReference.foodGroups.filterNot { it in list.categories }
        )

        val energy = list.total.energyKcal
        if (energy > 0) {
            val grains = (list.energyByCategory[FoodCategory.Grains] ?: 0.0) / energy
            if (grains > GRAINS_DOMINANT_ABOVE) insights += NutritionInsight.GrainsDominant(percent(grains))

            val oilsAndSugar = ((list.energyByCategory[FoodCategory.Oils] ?: 0.0) +
                (list.energyByCategory[FoodCategory.Pantry] ?: 0.0)) / energy
            if (oilsAndSugar > OILS_AND_SUGAR_HIGH_ABOVE) insights += NutritionInsight.OilsAndSugarHigh(percent(oilsAndSugar))
        }

        // Only the macronutrient furthest outside its range is mentioned: when one share is
        // too high another is necessarily too low, and saying both is the same fact twice
        if (shares != null) {
            listOf(
                Triple(Nutrient.Protein, shares.protein, NutritionReference.proteinShare),
                Triple(Nutrient.Carbohydrate, shares.carbohydrate, NutritionReference.carbohydrateShare),
                Triple(Nutrient.Fat, shares.fat, NutritionReference.fatShare)
            )
                .filter { (_, share, range) -> share !in range }
                .maxByOrNull { (_, share, range) -> distanceOutside(share, range) }
                ?.let { (nutrient, share, range) -> insights += outOfRange(nutrient, share, range) }
        }

        val protein = coverage[Nutrient.Protein] ?: 0.0
        if (protein >= PROTEIN_WELL_COVERED_FROM) insights += NutritionInsight.ProteinWellCovered(percent(protein))

        val energyCoverage = coverage[Nutrient.Energy] ?: 0.0
        if (energyCoverage >= ENERGY_ABOVE_REFERENCE_FROM) {
            insights += NutritionInsight.EnergyAboveReference(percent(energyCoverage))
        }

        if (list.skippedItems > 0) insights += NutritionInsight.ItemsNotCounted(list.skippedItems)

        return insights.take(MAX_INSIGHTS)
    }

    private fun distanceOutside(share: Double, range: ClosedFloatingPointRange<Double>): Double =
        if (share < range.start) range.start - share else share - range.endInclusive

    private fun outOfRange(
        nutrient: Nutrient,
        share: Double,
        range: ClosedFloatingPointRange<Double>
    ): NutritionInsight.MacroOutOfRange {
        return NutritionInsight.MacroOutOfRange(
            nutrient = nutrient,
            percent = percent(share),
            below = share < range.start,
            rangeFromPercent = percent(range.start),
            rangeToPercent = percent(range.endInclusive)
        )
    }

    private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()
}
