package com.example.nutricart.domain.nutrition

import com.example.nutricart.data.model.FoodCategory

// The five things the catalog can account for. Energy is in kilocalories, iron in
// milligrams, the rest in grams. The catalog has no fibre or other micronutrients.
enum class Nutrient { Energy, Protein, Carbohydrate, Fat, Iron }

// An amount of each nutrient. What the amount is "of" depends on where it comes from:
// 100 g of an item, a quantity of an item, a whole list, or one person for one day.
data class NutritionFacts(
    val energyKcal: Double = 0.0,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val ironMg: Double = 0.0
) {
    operator fun plus(other: NutritionFacts) = NutritionFacts(
        energyKcal + other.energyKcal,
        proteinG + other.proteinG,
        carbsG + other.carbsG,
        fatG + other.fatG,
        ironMg + other.ironMg
    )

    operator fun times(factor: Double) =
        NutritionFacts(energyKcal * factor, proteinG * factor, carbsG * factor, fatG * factor, ironMg * factor)

    fun amountOf(nutrient: Nutrient): Double = when (nutrient) {
        Nutrient.Energy -> energyKcal
        Nutrient.Protein -> proteinG
        Nutrient.Carbohydrate -> carbsG
        Nutrient.Fat -> fatG
        Nutrient.Iron -> ironMg
    }

    // False when every figure is zero, which is how an item with no nutrition data looks
    val hasData: Boolean
        get() = energyKcal > 0 || proteinG > 0 || carbsG > 0 || fatG > 0 || ironMg > 0
}

/*
 * NutriCart's reference values. These are general figures for one adult, used so a list can
 * be compared with something; they are not adjusted for age, sex, activity or any health
 * condition, and they are not a clinical standard or dietary advice.
 *
 * The daily amounts for protein, carbohydrate, fat and iron are the "required" figures drawn
 * on the project's nutrition-analysis mockup (SCR-07); energy is a round 2,000 kcal. The
 * energy shares are the commonly quoted acceptable ranges for the three macronutrients.
 */
object NutritionReference {
    // For one person for one day
    val daily = NutritionFacts(energyKcal = 2000.0, proteinG = 70.0, carbsG = 300.0, fatG = 60.0, ironMg = 18.0)

    // The days a list covers when its own planning period is not given: a month
    const val DAYS_COVERED = 30

    // Below this share of the reference a nutrient counts as a gap
    const val GAP_THRESHOLD = 0.60

    // Kilocalories per gram, used to work out each macronutrient's share of energy
    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBOHYDRATE = 4.0
    const val KCAL_PER_G_FAT = 9.0

    // Share of energy
    val proteinShare = 0.10..0.35
    val carbohydrateShare = 0.45..0.65
    val fatShare = 0.20..0.35

    // The groups that count towards variety. Oils and pantry items (sugar, snacks) are
    // on a list for other reasons and do not add to it.
    val foodGroups = listOf(
        FoodCategory.Grains, FoodCategory.Protein, FoodCategory.Veg, FoodCategory.Fruit, FoodCategory.Dairy
    )
}
