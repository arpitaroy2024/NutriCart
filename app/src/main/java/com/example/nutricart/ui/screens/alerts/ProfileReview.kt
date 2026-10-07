package com.example.nutricart.ui.screens.alerts

import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.data.repository.CatalogRepository
import com.example.nutricart.domain.PlanningPeriod
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
    // periodDays is the list's planning period, for the nutrition figures the rules read.
    fun analyze(
        items: List<ListItemWithCatalog>,
        profile: ProfileEntity?,
        periodDays: Int = PlanningPeriod.DEFAULT_DAYS
    ): ConflictAnalysis? {
        if (profile == null) return null
        return analyzer.analyze(
            items, profile.selections(), NutritionAnalyzer.analyze(items, profile.householdSize, periodDays)
        )
    }

    // The same, with alternatives priced for the profile's region
    suspend fun analyzeWithAlternatives(
        items: List<ListItemWithCatalog>,
        profile: ProfileEntity?,
        catalog: CatalogRepository,
        periodDays: Int = PlanningPeriod.DEFAULT_DAYS
    ): ConflictAnalysis? {
        if (profile == null) return null
        // Only packs that are offered now, and not another pack of something already on the
        // list (an older list may hold the kilo pack of a food now sold in a smaller one)
        val listed = items.map { it.catalog.name }.toSet()
        return analyzer.analyze(
            entries = items,
            profile = profile.selections(),
            nutrition = NutritionAnalyzer.analyze(items, profile.householdSize, periodDays),
            catalog = catalog.offeredItems().filterNot { it.name in listed },
            prices = catalog.prices(profile.region).mapValues { it.value.price }
        )
    }
}
