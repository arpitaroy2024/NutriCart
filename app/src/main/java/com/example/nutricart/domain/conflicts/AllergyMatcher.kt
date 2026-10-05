package com.example.nutricart.domain.conflicts

import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.model.Allergen

/*
 * The only typed allergies that are checked. Each is another name for one of the catalog's
 * allergen groups, compared as a whole entry after trimming and ignoring case. Nothing is
 * matched on part of a word and nothing is matched against an item's name: "Kiwi" or
 * "Mustard" has no catalog data behind it and is reported as not checked.
 */
object CustomAllergenAliases {
    val aliases: Map<String, Allergen> = mapOf(
        "milk" to Allergen.Dairy,
        "dairy" to Allergen.Dairy,
        "cow's milk" to Allergen.Dairy,
        "egg" to Allergen.Eggs,
        "eggs" to Allergen.Eggs,
        "fish" to Allergen.Fish,
        "shellfish" to Allergen.Shellfish,
        "shrimp" to Allergen.Shellfish,
        "prawn" to Allergen.Shellfish,
        "prawns" to Allergen.Shellfish,
        "chicken" to Allergen.Poultry,
        "poultry" to Allergen.Poultry,
        "beef" to Allergen.Beef,
        "soy" to Allergen.Soy,
        "soya" to Allergen.Soy,
        "soybean" to Allergen.Soy,
        "soybeans" to Allergen.Soy,
        "peanut" to Allergen.Peanuts,
        "peanuts" to Allergen.Peanuts,
        "groundnut" to Allergen.Peanuts,
        "groundnuts" to Allergen.Peanuts,
        "tree nut" to Allergen.TreeNuts,
        "tree nuts" to Allergen.TreeNuts,
        "gluten" to Allergen.Gluten,
        "wheat" to Allergen.Gluten,
        "sesame" to Allergen.Sesame,
        "sesame seeds" to Allergen.Sesame
    )

    fun normalize(entry: String): String = entry.trim().lowercase().replace(Regex("\\s+"), " ")

    fun resolve(entry: String): Allergen? = aliases[normalize(entry)]
}

// The allergens to look for, and the typed entries that could not be turned into one
data class ResolvedAllergies(
    // Allergen to the typed entry it came from, or null when it was picked from the list
    val allergens: Map<Allergen, String?>,
    val unmatchedCustom: List<String>
)

/*
 * Compares the profile's allergies with the allergens the catalog states for an item.
 * Only that stated data is used. An item with no allergen data produces no match, which
 * means "nothing known", not "contains none".
 */
object AllergyMatcher {

    fun resolve(listed: Set<Allergen>, custom: List<String>): ResolvedAllergies {
        val allergens = linkedMapOf<Allergen, String?>()
        // Enum order, so the result does not depend on how the set was built
        Allergen.entries.filter { it in listed }.forEach { allergens[it] = null }
        val unmatched = mutableListOf<String>()
        for (entry in custom) {
            val allergen = CustomAllergenAliases.resolve(entry)
            when {
                allergen == null -> unmatched += entry
                // A listed allergen already covers it
                allergen !in allergens -> allergens[allergen] = entry
            }
        }
        return ResolvedAllergies(allergens, unmatched)
    }

    fun match(item: CatalogItemEntity, allergies: ResolvedAllergies): List<ItemConcern> =
        allergies.allergens
            .filterKeys { it in item.allergens }
            .map { (allergen, customEntry) ->
                ItemConcern(
                    type = ConcernType.Allergy,
                    severity = Severity.High,
                    reason = ConcernReason.ContainsListedAllergen,
                    allergen = allergen,
                    customEntry = customEntry
                )
            }
}
