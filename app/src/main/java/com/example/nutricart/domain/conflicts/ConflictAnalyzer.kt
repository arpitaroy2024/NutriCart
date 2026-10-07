package com.example.nutricart.domain.conflicts

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.domain.ListRules
import com.example.nutricart.domain.nutrition.NutritionAnalysis
import com.example.nutricart.domain.nutrition.NutritionCalculator
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Chooses catalog items that could take a flagged item's place. A candidate must:
 *
 *   - be in the same food category and carry the same main-nutrient tag, so it plays the
 *     same part in the list;
 *   - have a price in the user's region;
 *   - have nutrition data and a known weight per unit;
 *   - raise no concern of its own for this profile;
 *   - not be on the list already.
 *
 * Candidates are ordered by how close their price per 100 g is to the flagged item's, then
 * by catalog id, and at most MAX_ALTERNATIVES are kept. When nothing qualifies the result
 * is empty and the screen says so. This is a like-for-like grocery swap, not a statement
 * that the alternative suits anyone's health.
 */
object AlternativeFinder {
    const val MAX_ALTERNATIVES = 3

    fun find(
        flagged: ListItemWithCatalog,
        catalog: List<CatalogItemEntity>,
        // Regional price per unit, by catalog item id
        prices: Map<Long, Int>,
        onList: Set<Long>,
        hasConcern: (CatalogItemEntity) -> Boolean
    ): List<Alternative> {
        val grams = NutritionCalculator.gramsOf(flagged.catalog, flagged.item.quantity) ?: return emptyList()
        val flaggedPer100g = flagged.item.unitPrice * 100.0 / flagged.catalog.gramsPerUnit
        return catalog.asSequence()
            .filter { it.id != flagged.catalog.id && it.id !in onList }
            .filter { it.category == flagged.catalog.category && it.nutrientTag == flagged.catalog.nutrientTag }
            .filter { (prices[it.id] ?: 0) > 0 }
            .filter { it.gramsPerUnit > 0 && NutritionCalculator.per100g(it).hasData }
            .filterNot(hasConcern)
            .distinctBy { it.id }
            .sortedWith(
                compareBy<CatalogItemEntity> {
                    abs(prices.getValue(it.id) * 100.0 / it.gramsPerUnit - flaggedPer100g)
                }.thenBy { it.id }
            )
            .take(MAX_ALTERNATIVES)
            .map {
                Alternative(
                    catalogItemId = it.id,
                    name = it.name,
                    unit = it.unit,
                    unitPrice = prices.getValue(it.id),
                    quantity = (grams / it.gramsPerUnit).roundToInt()
                        .coerceIn(ListRules.MIN_QUANTITY, ListRules.MAX_QUANTITY),
                    packAmount = it.packAmount,
                    packMeasure = it.packMeasure
                )
            }
            .toList()
    }
}

/*
 * Profile + list + catalog data + nutrition analysis in, ConflictAnalysis out. Runs the
 * allergy matcher, the health-condition rules and the alternative finder; holds no state
 * and reads nothing itself, so the same inputs always give the same result.
 *
 * It is the source of truth for what is flagged. Anything built on top (a model that
 * rewords or ranks) should start from the ConflictAnalysis, not from what the screen says.
 */
class ConflictAnalyzer(private val health: HealthConditionAnalyzer = HealthConditionAnalyzer()) {

    fun analyze(
        entries: List<ListItemWithCatalog>,
        profile: ProfileSelections,
        // Null when the list cannot be analysed for nutrition
        nutrition: NutritionAnalysis?,
        // Pass the catalog and the region's prices to get alternatives; without them none are offered
        catalog: List<CatalogItemEntity> = emptyList(),
        prices: Map<Long, Int> = emptyMap()
    ): ConflictAnalysis {
        val allergies = AllergyMatcher.resolve(profile.allergies, profile.customAllergies)

        fun concernsFor(item: CatalogItemEntity): List<ItemConcern> =
            (AllergyMatcher.match(item, allergies) + health.itemConcerns(item, profile.conditions))
                .sortedBy { it.severity }

        val onList = entries.map { it.catalog.id }.toSet()
        val items = entries.mapNotNull { entry ->
            val concerns = concernsFor(entry.catalog)
            if (concerns.isEmpty()) {
                null
            } else {
                ItemConflict(
                    listItemId = entry.item.id,
                    catalogItemId = entry.catalog.id,
                    itemName = entry.catalog.name,
                    concerns = concerns,
                    keptAnyway = entry.item.alertOverridden,
                    alternatives = AlternativeFinder.find(entry, catalog, prices, onList) { concernsFor(it).isNotEmpty() }
                )
            }
        }

        val listRules = health.listConsiderations(nutrition, profile.conditions)
        return ConflictAnalysis(
            profileIsEmpty = profile.isEmpty,
            items = items,
            considerations = listRules.considerations,
            unmatchedCustomAllergies = allergies.unmatchedCustom,
            conditionsWithoutRules = health.conditionsWithoutRules(profile.conditions),
            customConditions = profile.customConditions,
            unevaluatedRuleIds = listRules.unevaluatedRuleIds,
            itemsWithoutNutritionData = entries.count { health.skippedForMissingData(it.catalog, profile.conditions) }
        )
    }
}
