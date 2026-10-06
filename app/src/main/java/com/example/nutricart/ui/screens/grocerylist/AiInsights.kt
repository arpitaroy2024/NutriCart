package com.example.nutricart.ui.screens.grocerylist

import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.domain.ai.AiError
import com.example.nutricart.domain.ai.AiFlaggedItem
import com.example.nutricart.domain.ai.AiGroceryContext
import com.example.nutricart.domain.ai.AiListItem
import com.example.nutricart.domain.ai.AiNutritionSummary
import com.example.nutricart.domain.ai.AiPicks
import com.example.nutricart.domain.ai.AiPrioritizedItem
import com.example.nutricart.domain.ai.AiResponse
import com.example.nutricart.domain.ai.AiResult
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.domain.conflicts.ItemConcern
import com.example.nutricart.domain.nutrition.NutritionAnalyzer

// The optional AI explanation of the list on screen. It is never stored, and nothing else
// on the screen depends on it.
sealed interface AiInsightsUiState {
    data object Idle : AiInsightsUiState
    data object Loading : AiInsightsUiState

    // An answer that passed AiResponseValidator. picks are the model's prioritised items
    // after AiPicks has checked them against the list: only items that are on it.
    data class Ready(
        val response: AiResponse,
        val picks: List<AiPrioritizedItem> = emptyList()
    ) : AiInsightsUiState
    data class Failed(val reason: AiInsightsFailure) : AiInsightsUiState
}

// What the user is told. The technical cause stays in AiResult.Failure.
enum class AiInsightsFailure { Connection, Unavailable }

// context is what the answer was asked about; the picks are checked against it
fun AiResult?.toInsightsState(context: AiGroceryContext): AiInsightsUiState = when (this) {
    is AiResult.Success -> AiInsightsUiState.Ready(response, AiPicks.accepted(response, context))
    // Null is a timeout
    null -> AiInsightsUiState.Failed(AiInsightsFailure.Connection)
    is AiResult.Failure -> AiInsightsUiState.Failed(
        if (error == AiError.Network) AiInsightsFailure.Connection else AiInsightsFailure.Unavailable
    )
}

/*
 * Builds what is sent to the model for one list. Only what the explanation needs: the
 * household size, the budget and total, the allergy and condition names, the items, and
 * what the app's own nutrition analysis and profile review found. No name, email, account
 * id, region, list id, unit price or bought state, and no database object.
 */
object AiInsightsContext {
    const val CURRENCY = "BDT"

    fun from(
        list: GroceryListEntity,
        items: List<ListItemWithCatalog>,
        profile: ProfileEntity?,
        review: ConflictAnalysis?
    ) = AiGroceryContext(
        householdSize = profile?.householdSize ?: 0,
        monthlyBudget = list.budget,
        currency = CURRENCY,
        allergies = profile?.let { p -> p.allergies.map { it.name } + p.customAllergies }.orEmpty(),
        healthConditions = profile?.let { p -> p.conditions.map { it.name } + p.customConditions }.orEmpty(),
        items = items.map {
            AiListItem(
                it.catalog.name, it.catalog.category.name, it.item.quantity, it.catalog.unit, it.catalog.nutrientTag.name
            )
        },
        nutrition = NutritionAnalyzer.analyze(items, profile?.householdSize ?: 0)?.let { AiNutritionSummary.from(it) },
        listTotal = items.sumOf { it.item.quantity * it.item.unitPrice },
        flaggedItems = review?.items.orEmpty().map { conflict ->
            AiFlaggedItem(conflict.itemName, conflict.concerns.map { it.label() }, conflict.keptAnyway)
        },
        listConsiderations = review?.considerations.orEmpty().map { consideration ->
            consideration.reason.name + consideration.conditions.joinToString(prefix = " (", postfix = ")") { it.name }
        },
        notCheckedByRules = review?.let { r ->
            r.unmatchedCustomAllergies + r.conditionsWithoutRules.map { it.name } + r.customConditions
        }.orEmpty()
    )

    // The reason and what it is tied to, without the figure that was compared
    private fun ItemConcern.label(): String {
        val subjects = listOfNotNull(allergen?.name) + conditions.map { it.name }
        return if (subjects.isEmpty()) reason.name else reason.name + subjects.joinToString(prefix = " (", postfix = ")")
    }
}
