package com.example.nutricart.data.ai

import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.domain.ai.AiGroceryContext
import com.example.nutricart.domain.ai.AiListItem
import com.example.nutricart.domain.ai.AiNutritionSummary
import com.example.nutricart.domain.ai.AiRequest
import com.example.nutricart.domain.ai.AiTask
import com.example.nutricart.domain.nutrition.NutritionAnalysis
import com.example.nutricart.domain.nutrition.NutritionAnalyzer

/*
 * The one controlled request of Phase 9A. A made-up household, not a user: 4 people,
 * 8,000 BDT, a peanut allergy, prediabetes, and a list of rice, lentils, eggs, vegetables
 * and sugar taken from the demo catalog. The nutrition figures are whatever the real
 * NutritionAnalyzer gives for that list.
 */
object AiSampleContext {
    const val HOUSEHOLD_SIZE = 4

    // Catalog id to quantity
    private val quantities = listOf(2L to 20, 5L to 4, 8L to 60, 18L to 4, 20L to 8, 21L to 4, 22L to 4, 38L to 2)

    val entries: List<ListItemWithCatalog> = quantities.map { (id, quantity) ->
        val catalog = DemoCatalogSeed.items.first { it.id == id }
        ListItemWithCatalog(
            item = ListItemEntity(id = id, listId = 1, catalogItemId = id, quantity = quantity, unitPrice = 10),
            catalog = catalog
        )
    }

    fun analysis(): NutritionAnalysis = checkNotNull(NutritionAnalyzer.analyze(entries, HOUSEHOLD_SIZE))

    fun request() = AiRequest(
        task = AiTask.ReviewGroceryPlan,
        context = AiGroceryContext(
            householdSize = HOUSEHOLD_SIZE,
            budget = 8000,
            currency = "BDT",
            allergies = listOf("Peanuts"),
            healthConditions = listOf("Prediabetes"),
            items = entries.map {
                AiListItem(it.catalog.name, it.catalog.category.name, it.item.quantity, it.catalog.unit)
            },
            nutrition = AiNutritionSummary.from(analysis())
        )
    )
}
