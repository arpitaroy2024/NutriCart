package com.example.nutricart.domain

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition

// True when the item is tagged with one of the listed allergies or flagged for one of the
// listed conditions. Only the catalog's own tags are compared: typed (custom) allergies and
// conditions cannot be matched, and an untagged item is not thereby known to be safe.
fun CatalogItemEntity.conflictsWith(allergies: Set<Allergen>, conditions: Set<HealthCondition>): Boolean =
    allergens.any { it in allergies } || flaggedConditions.any { it in conditions }
