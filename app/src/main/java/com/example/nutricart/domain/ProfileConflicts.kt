package com.example.nutricart.domain

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.domain.conflicts.HealthConditionAnalyzer

private val healthRules = HealthConditionAnalyzer()

/*
 * True when the grocery generator should leave the item out for this profile: the catalog
 * states an allergen the profile lists, or one of the documented health-condition rules
 * (domain/conflicts/HealthRules) flags the item for a listed condition.
 *
 * The condition half is the same rule set the profile review uses, so the two cannot
 * disagree: a condition with no rule leaves nothing out. The catalog's flaggedConditions
 * tags are not read. They are hand-set demo values with no figure behind them.
 *
 * Typed (custom) allergies and conditions are not considered here, and an item that is
 * not left out is not thereby known to be free of anything.
 */
fun CatalogItemEntity.conflictsWith(allergies: Set<Allergen>, conditions: Set<HealthCondition>): Boolean =
    allergens.any { it in allergies } || healthRules.itemConcerns(this, conditions).isNotEmpty()
