package com.example.nutricart.data.model

// Profile choices. The constant names are what the database stores, so they must not be
// renamed; the wording shown to the user lives in strings.xml.
// A free-text "other" entry is stored beside each set, not as a constant.
enum class Allergen { Dairy, Eggs, Fish, Shellfish, Poultry, Beef, Soy, Peanuts, TreeNuts, Gluten, Sesame }

// Dietary-planning inputs only; nothing here diagnoses or treats anything.
// Having no condition is an empty set.
enum class HealthCondition {
    Diabetes, Prediabetes, Hypertension, HighCholesterol, HeartDisease, KidneyDisease,
    LiverDisease, Pcos, Thyroid, Anemia, Celiac
}

// Protein, Grains and Veg are the filter chips drawn on SCR-05; the rest cover
// the other items the PDFs show (oils, sugar, milk, fruit).
enum class FoodCategory { Protein, Grains, Veg, Fruit, Dairy, Oils, Pantry }

// The dominant nutrient shown as an item's tag
enum class NutrientTag { Protein, Carbs, Fat, Iron }

// What one catalog pack is measured in. The constant names are stored, so do not rename them.
enum class PackMeasure { Gram, Millilitre, Piece }

object Regions {
    val all = listOf(
        "Barishal Division",
        "Chattogram Division",
        "Dhaka Division",
        "Khulna Division",
        "Mymensingh Division",
        "Rajshahi Division",
        "Rangpur Division",
        "Sylhet Division"
    )
}
