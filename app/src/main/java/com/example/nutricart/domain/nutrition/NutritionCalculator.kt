package com.example.nutricart.domain.nutrition

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.FoodCategory

// What a whole list provides
data class ListNutrition(
    val total: NutritionFacts,
    val energyByCategory: Map<FoodCategory, Double>,
    // Every category on the list, including items that could not be counted
    val categories: Set<FoodCategory>,
    val countedItems: Int,
    // Items left out because their quantity, weight per unit or nutrition data was unusable
    val skippedItems: Int
) {
    // The list shared evenly between the household over the days it is meant to cover
    fun perPersonPerDay(householdSize: Int, days: Int = NutritionReference.DAYS_COVERED): NutritionFacts =
        if (householdSize <= 0 || days <= 0) NutritionFacts() else total * (1.0 / (householdSize * days))
}

/*
 * Turns list items into nutrition. Pure arithmetic: no database, no UI, no profile.
 *
 * The catalog states nutrition per 100 g, and each catalog item states how many grams one
 * of its units weighs (a kilogram is 1000, a litre of oil 920, one egg 50). So:
 *
 *     grams        = quantity x grams per unit
 *     contribution = nutrition per 100 g x grams / 100
 *
 * Nothing is guessed. An item whose quantity or weight per unit is not positive, or that
 * has no nutrition figures at all, contributes nothing and is counted as skipped.
 */
object NutritionCalculator {

    fun per100g(item: CatalogItemEntity) = NutritionFacts(
        energyKcal = item.caloriesPer100g.coerceAtLeast(0.0),
        proteinG = item.proteinPer100g.coerceAtLeast(0.0),
        carbsG = item.carbsPer100g.coerceAtLeast(0.0),
        fatG = item.fatPer100g.coerceAtLeast(0.0),
        ironMg = item.ironMgPer100g.coerceAtLeast(0.0)
    )

    // Null when the weight cannot be worked out
    fun gramsOf(item: CatalogItemEntity, quantity: Int): Double? =
        if (quantity <= 0 || item.gramsPerUnit <= 0) null else quantity.toDouble() * item.gramsPerUnit

    // Null when the item cannot be counted
    fun contribution(item: CatalogItemEntity, quantity: Int): NutritionFacts? {
        val grams = gramsOf(item, quantity) ?: return null
        val facts = per100g(item)
        return if (facts.hasData) facts * (grams / 100.0) else null
    }

    fun totals(entries: List<ListItemWithCatalog>): ListNutrition {
        var total = NutritionFacts()
        val energyByCategory = mutableMapOf<FoodCategory, Double>()
        var counted = 0
        var skipped = 0
        for (entry in entries) {
            val contribution = contribution(entry.catalog, entry.item.quantity)
            if (contribution == null) {
                skipped++
                continue
            }
            counted++
            total += contribution
            energyByCategory[entry.catalog.category] =
                (energyByCategory[entry.catalog.category] ?: 0.0) + contribution.energyKcal
        }
        return ListNutrition(
            total = total,
            energyByCategory = energyByCategory,
            categories = entries.map { it.catalog.category }.toSet(),
            countedItems = counted,
            skippedItems = skipped
        )
    }
}
