package com.example.nutricart.data.model

// Options shown on the profile setup screen (SCR-03)
enum class Allergen { Peanuts, Shellfish, Dairy, Eggs, Gluten }

enum class HealthCondition { None, Diabetes, Hypertension }

// Protein, Grains and Veg are the filter chips drawn on SCR-05; the rest cover
// the other items the PDFs show (oils, sugar, milk, fruit).
enum class FoodCategory { Protein, Grains, Veg, Fruit, Dairy, Oils, Pantry }

// The dominant nutrient shown as an item's tag
enum class NutrientTag { Protein, Carbs, Fat, Iron }

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
