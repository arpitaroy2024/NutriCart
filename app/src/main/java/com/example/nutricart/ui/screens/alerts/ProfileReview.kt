package com.example.nutricart.ui.screens.alerts

import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.data.repository.CatalogRepository
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.domain.conflicts.ConflictAnalyzer
import com.example.nutricart.domain.conflicts.ProfileSelections
import com.example.nutricart.domain.nutrition.NutritionAnalyzer

fun ProfileEntity.selections() = ProfileSelections(
    allergies = allergies,
    customAllergies = customAllergies,
    conditions = conditions,
    customConditions = customConditions
)

// The one place screens turn a list and a profile into a ConflictAnalysis. Nothing is
// cached or stored: each call works from the items and profile it is given, so a list
// edit or a profile change is reflected the next time the flows emit.
object ProfileReview {
    private val analyzer = ConflictAnalyzer()

    // Which items are flagged and why, without alternatives. Null without a profile.
    fun analyze(items: List<ListItemWithCatalog>, profile: ProfileEntity?): ConflictAnalysis? {
        if (profile == null) return null
        return analyzer.analyze(items, profile.selections(), NutritionAnalyzer.analyze(items, profile.householdSize))
    }

    // The same, with alternatives priced for the profile's region
    suspend fun analyzeWithAlternatives(
        items: List<ListItemWithCatalog>,
        profile: ProfileEntity?,
        catalog: CatalogRepository
    ): ConflictAnalysis? {
        if (profile == null) return null
        return analyzer.analyze(
            entries = items,
            profile = profile.selections(),
            nutrition = NutritionAnalyzer.analyze(items, profile.householdSize),
            catalog = catalog.items(),
            prices = catalog.prices(profile.region).mapValues { it.value.price }
        )
    }
}
