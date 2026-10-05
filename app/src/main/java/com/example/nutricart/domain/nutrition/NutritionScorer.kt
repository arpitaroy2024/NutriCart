package com.example.nutricart.domain.nutrition

import com.example.nutricart.data.model.FoodCategory
import kotlin.math.roundToInt

enum class ScoreBand { Excellent, Good, Fair, Low }

// Each macronutrient's share of the energy the three provide together, 0..1
data class MacroShares(val protein: Double, val carbohydrate: Double, val fat: Double)

// The score and the three parts it is made of, each 0..1
data class NutritionScore(
    val value: Int,
    val band: ScoreBand,
    val adequacy: Double,
    val balance: Double,
    val variety: Double
)

/*
 * NutriCart's nutrition balance score. This is a product heuristic, written so it can be
 * explained and tested. It is not a clinical or official measure, and it says nothing
 * about anyone's health.
 *
 *     score = 60 x adequacy + 25 x balance + 15 x variety        (0 to 100)
 *
 * adequacy  How much of the reference the list covers, averaged over the five nutrients.
 *           Each nutrient counts up to 100% and no further, so a surplus of one cannot
 *           hide a shortage of another.
 * balance   Whether protein, carbohydrate and fat each supply a share of energy inside
 *           its reference range. A share inside scores 1; outside it loses value in a
 *           straight line and reaches 0 at 20 percentage points beyond the range.
 * variety   How many of the five food groups are on the list.
 *
 * Adequacy has the most weight because the project describes the score as the distance
 * from the household's targets; balance and variety refine it.
 */
object NutritionScorer {
    const val ADEQUACY_WEIGHT = 60
    const val BALANCE_WEIGHT = 25
    const val VARIETY_WEIGHT = 15

    // The score at which each band starts
    const val EXCELLENT_FROM = 85
    const val GOOD_FROM = 70
    const val FAIR_FROM = 50

    private const val BALANCE_ZERO_AT = 0.20

    // Each nutrient as a share of the daily reference; 1.0 is exactly the reference, and it is not capped
    fun coverage(perPersonPerDay: NutritionFacts): Map<Nutrient, Double> =
        Nutrient.entries.associateWith { nutrient ->
            val reference = NutritionReference.daily.amountOf(nutrient)
            if (reference <= 0) 0.0 else perPersonPerDay.amountOf(nutrient) / reference
        }

    fun adequacy(coverage: Map<Nutrient, Double>): Double =
        Nutrient.entries.map { (coverage[it] ?: 0.0).coerceIn(0.0, 1.0) }.average()

    // Null when the list has no protein, carbohydrate or fat at all
    fun macroShares(facts: NutritionFacts): MacroShares? {
        val protein = facts.proteinG * NutritionReference.KCAL_PER_G_PROTEIN
        val carbohydrate = facts.carbsG * NutritionReference.KCAL_PER_G_CARBOHYDRATE
        val fat = facts.fatG * NutritionReference.KCAL_PER_G_FAT
        val sum = protein + carbohydrate + fat
        return if (sum <= 0) null else MacroShares(protein / sum, carbohydrate / sum, fat / sum)
    }

    fun rangeScore(share: Double, range: ClosedFloatingPointRange<Double>): Double {
        val outside = when {
            share < range.start -> range.start - share
            share > range.endInclusive -> share - range.endInclusive
            else -> 0.0
        }
        return (1 - outside / BALANCE_ZERO_AT).coerceIn(0.0, 1.0)
    }

    fun balance(shares: MacroShares?): Double =
        if (shares == null) {
            0.0
        } else {
            listOf(
                rangeScore(shares.protein, NutritionReference.proteinShare),
                rangeScore(shares.carbohydrate, NutritionReference.carbohydrateShare),
                rangeScore(shares.fat, NutritionReference.fatShare)
            ).average()
        }

    fun variety(categories: Set<FoodCategory>): Double =
        NutritionReference.foodGroups.count { it in categories }.toDouble() / NutritionReference.foodGroups.size

    fun bandFor(value: Int): ScoreBand = when {
        value >= EXCELLENT_FROM -> ScoreBand.Excellent
        value >= GOOD_FROM -> ScoreBand.Good
        value >= FAIR_FROM -> ScoreBand.Fair
        else -> ScoreBand.Low
    }

    fun score(perPersonPerDay: NutritionFacts, categories: Set<FoodCategory>): NutritionScore {
        val adequacy = adequacy(coverage(perPersonPerDay))
        val balance = balance(macroShares(perPersonPerDay))
        val variety = variety(categories)
        val value = (ADEQUACY_WEIGHT * adequacy + BALANCE_WEIGHT * balance + VARIETY_WEIGHT * variety)
            .roundToInt().coerceIn(0, 100)
        return NutritionScore(value, bandFor(value), adequacy, balance, variety)
    }
}
