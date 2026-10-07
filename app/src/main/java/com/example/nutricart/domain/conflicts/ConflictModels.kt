package com.example.nutricart.domain.conflicts

import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.PackMeasure

// What a concern is about
enum class ConcernType {
    // The catalog says the item contains an allergen the profile lists
    Allergy,

    // The catalog's allergen data matches a listed condition directly (gluten with celiac disease)
    HealthCondition,

    // A nutrition figure crosses the threshold of a rule tied to a listed condition
    NutritionConsideration
}

/*
 * How firmly the data supports the concern and how prominently it is shown. It is not a
 * measure of medical risk: the app does not know how anyone reacts to anything.
 *
 * High    The catalog names an allergen on the item and the profile lists that allergen
 *         (or celiac disease, for gluten). An exact match of two stated facts.
 * Medium  One item's nutrition figure crosses a rule's threshold. A general consideration.
 * Low     A figure for the list as a whole crosses a rule's threshold. Information only.
 */
enum class Severity { High, Medium, Low }

// Why something was flagged. The screen has one fixed sentence per reason.
enum class ConcernReason {
    ContainsListedAllergen,
    ContainsGluten,
    ConcentratedCarbohydrate,
    CarbohydrateShareHigh,
    IronBelowReference
}

// The part of a profile this engine reads
data class ProfileSelections(
    val allergies: Set<Allergen> = emptySet(),
    val customAllergies: List<String> = emptyList(),
    val conditions: Set<HealthCondition> = emptySet(),
    val customConditions: List<String> = emptyList()
) {
    val isEmpty: Boolean
        get() = allergies.isEmpty() && customAllergies.isEmpty() && conditions.isEmpty() && customConditions.isEmpty()
}

// One reason one item was flagged
data class ItemConcern(
    val type: ConcernType,
    val severity: Severity,
    val reason: ConcernReason,
    // The catalog allergen that matched, for allergy and gluten concerns
    val allergen: Allergen? = null,
    // The typed allergy that was mapped to that allergen, when the match came from one
    val customEntry: String? = null,
    // The listed conditions the rule belongs to
    val conditions: List<HealthCondition> = emptyList(),
    val ruleId: String? = null,
    // The figure that was compared and what it was compared with, for nutrition rules
    val value: Double? = null,
    val threshold: Double? = null
)

// A catalog item that could take a flagged item's place
data class Alternative(
    val catalogItemId: Long,
    val name: String,
    val unit: String,
    val unitPrice: Int,
    // How many packs give about the same weight as the flagged item's quantity
    val quantity: Int,
    // The size of one pack, so the amount can be shown as a weight or volume
    val packAmount: Int = 1000,
    val packMeasure: PackMeasure = PackMeasure.Gram
) {
    val cost: Int get() = unitPrice * quantity
}

// Everything flagged about one item on the list, grouped
data class ItemConflict(
    val listItemId: Long,
    val catalogItemId: Long,
    val itemName: String,
    // Strongest first
    val concerns: List<ItemConcern>,
    // The user chose to keep the item. The concerns stay; only the prompt to review goes.
    val keptAnyway: Boolean,
    // Empty when nothing in the catalog qualifies
    val alternatives: List<Alternative>
) {
    val severity: Severity get() = concerns.minOf { it.severity }
}

// Something about the list as a whole, for a listed condition
data class ListConsideration(
    val ruleId: String,
    val reason: ConcernReason,
    val severity: Severity,
    val conditions: List<HealthCondition>,
    // Fractions: 0.72 is 72%
    val value: Double,
    val threshold: Double
)

// The result for one list and one profile. Worked out when needed, never stored.
data class ConflictAnalysis(
    val profileIsEmpty: Boolean,
    // In list order
    val items: List<ItemConflict>,
    val considerations: List<ListConsideration>,
    // Typed allergies with no mapping to catalog allergen data: kept on the profile, not checked
    val unmatchedCustomAllergies: List<String>,
    // Listed conditions the data cannot support any rule for
    val conditionsWithoutRules: List<HealthCondition>,
    // Typed conditions: never checked
    val customConditions: List<String>,
    // List-wide rules for listed conditions that could not be worked out (no nutrition analysis)
    val unevaluatedRuleIds: List<String>,
    // Items whose nutrition rules were skipped because the item has no nutrition data
    val itemsWithoutNutritionData: Int
) {
    val allergenMatches: List<ItemConflict> get() = items.filter { it.severity == Severity.High }

    val nutritionConsiderations: List<ItemConflict> get() = items.filter { it.severity != Severity.High }

    // Flagged items the user has not chosen to keep
    val needsReview: Int get() = items.count { !it.keptAnyway }

    val hasFindings: Boolean get() = items.isNotEmpty() || considerations.isNotEmpty()

    // Something on the profile that no rule covers
    val hasUnchecked: Boolean
        get() = unmatchedCustomAllergies.isNotEmpty() || conditionsWithoutRules.isNotEmpty() ||
            customConditions.isNotEmpty() || unevaluatedRuleIds.isNotEmpty()

    fun forItem(listItemId: Long): ItemConflict? = items.firstOrNull { it.listItemId == listItemId }
}
