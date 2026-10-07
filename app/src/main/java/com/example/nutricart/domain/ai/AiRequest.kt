package com.example.nutricart.domain.ai

import com.example.nutricart.domain.nutrition.NutritionAnalysis

// What the reasoning layer is asked to do. One task for now.
enum class AiTask { ReviewGroceryPlan }

// A provider-neutral request: a task and the facts it may reason about. Nothing here names
// Gemini, and nothing here identifies a person (no name, email, account id or region).
data class AiRequest(val task: AiTask, val context: AiGroceryContext)

/*
 * The facts handed to the model. Every value is produced by the app's own deterministic code
 * or typed by the user; the model is told to treat them as the only facts it has. Item
 * prices, per-item nutrition and the catalog's allergen data are deliberately not included.
 */
data class AiGroceryContext(
    val householdSize: Int,
    // The budget entered for this list, for the period below
    val budget: Int,
    val currency: String,
    val allergies: List<String>,
    val healthConditions: List<String>,
    val items: List<AiListItem>,
    // Null when the list could not be analysed for nutrition
    val nutrition: AiNutritionSummary?,
    // The list's estimated total, from the app's own sum. Null when it is not supplied.
    val listTotal: Int? = null,
    // What the profile review flagged. The model explains these; it never decides them.
    val flaggedItems: List<AiFlaggedItem> = emptyList(),
    // Findings about the list as a whole, such as "CarbohydrateShareHigh (Diabetes)"
    val listConsiderations: List<String> = emptyList(),
    // Allergies and conditions on the profile that no rule covers
    val notCheckedByRules: List<String> = emptyList(),
    // The days the list and its budget are for: 7, 14, 21 or 30
    val planningPeriodDays: Int = 30
)

// quantity is a number of packs and unit is the pack ("kg", "250 g", "pcs").
// mainNutrient is the catalog's own tag for the item ("Protein", "Carbs", "Fat", "Iron")
data class AiListItem(
    val name: String,
    val category: String,
    val quantity: Int,
    val unit: String,
    val mainNutrient: String? = null,
    // What quantity packs of that unit come to, in words: "750 g", "24 kg", "1.5 L", "48 pcs"
    val amount: String? = null
)

// One item the review flagged, with a line per reason such as "ContainsListedAllergen (Peanuts)"
data class AiFlaggedItem(val name: String, val reasons: List<String>, val keptByUser: Boolean)

// Figures copied from NutritionAnalysis. Coverage is the percent of NutriCart's daily
// reference per person, keyed by nutrient ("energy", "protein", "carbohydrate", "fat", "iron").
data class AiNutritionSummary(
    val score: Int,
    val scoreBand: String,
    val daysCovered: Int,
    val coveragePercent: Map<String, Int>,
    val missingFoodGroups: List<String>
) {
    companion object {
        // Copies existing figures; calculates nothing
        fun from(analysis: NutritionAnalysis) = AiNutritionSummary(
            score = analysis.score.value,
            scoreBand = analysis.score.band.name,
            daysCovered = analysis.days,
            coveragePercent = analysis.coverage.keys.associate { it.name.lowercase() to analysis.coveragePercent(it) },
            missingFoodGroups = analysis.missingGroups.map { it.name }
        )
    }
}
