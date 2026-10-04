package com.example.nutricart.data.local

import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.data.model.Regions

// What the catalog tables are filled with on first use. To use a real dataset,
// provide another CatalogSeed to RoomCatalogRepository.
interface CatalogSeed {
    val items: List<CatalogItemEntity>
    val prices: List<RegionPriceEntity>
}

/*
 * DEMO DATA. Nothing in this object is a real or current market price.
 *
 * Prices are placeholders: the "Rangpur Division" column reuses the figures drawn
 * in the project's PDF mockups where one exists, the rest are invented, and other
 * regions are the same figure scaled by a made-up percentage. Nutrition values are
 * rounded approximations per 100 g and have not been checked against a food
 * composition table. Every price has updatedAt = null so the UI can tell it is
 * demo data and must not show it as recently updated.
 */
object DemoCatalogSeed : CatalogSeed {

    private class Row(
        val id: Long,
        val name: String,
        val category: FoodCategory,
        val unit: String,
        val gramsPerUnit: Int,
        val basePrice: Int,
        val kcal: Double,
        val protein: Double,
        val carbs: Double,
        val fat: Double,
        val ironMg: Double,
        val tag: NutrientTag,
        val allergens: Set<Allergen> = emptySet(),
        val conditions: Set<HealthCondition> = emptySet()
    )

    private const val KG = 1000
    private const val LITRE_OIL = 920
    private const val LITRE_MILK = 1030

    private val rows = listOf(
        // Grains
        Row(1, "Brown rice", FoodCategory.Grains, "kg", KG, 112, 370.0, 7.9, 77.0, 2.9, 1.5, NutrientTag.Carbs),
        Row(2, "White rice (Miniket)", FoodCategory.Grains, "kg", KG, 75, 365.0, 7.1, 80.0, 0.7, 0.8, NutrientTag.Carbs,
            conditions = setOf(HealthCondition.Diabetes)),
        Row(3, "Whole wheat flour (Atta)", FoodCategory.Grains, "kg", KG, 55, 340.0, 13.0, 72.0, 2.5, 3.9, NutrientTag.Carbs,
            allergens = setOf(Allergen.Gluten)),
        Row(4, "Oats", FoodCategory.Grains, "kg", KG, 220, 389.0, 16.9, 66.0, 6.9, 4.7, NutrientTag.Carbs,
            allergens = setOf(Allergen.Gluten)),

        // Protein
        Row(5, "Lentils (Masoor)", FoodCategory.Protein, "kg", KG, 160, 353.0, 25.0, 60.0, 1.0, 7.0, NutrientTag.Protein),
        Row(6, "Mung dal", FoodCategory.Protein, "kg", KG, 170, 347.0, 24.0, 63.0, 1.2, 6.7, NutrientTag.Protein),
        Row(7, "Chickpeas (Chola)", FoodCategory.Protein, "kg", KG, 110, 364.0, 19.0, 61.0, 6.0, 6.2, NutrientTag.Protein),
        Row(8, "Eggs", FoodCategory.Protein, "pcs", 50, 14, 143.0, 12.6, 0.7, 9.5, 1.8, NutrientTag.Protein,
            allergens = setOf(Allergen.Eggs)),
        Row(9, "Chicken (Broiler)", FoodCategory.Protein, "kg", KG, 200, 215.0, 18.6, 0.0, 15.0, 0.9, NutrientTag.Protein),
        Row(10, "Beef", FoodCategory.Protein, "kg", KG, 750, 250.0, 26.0, 0.0, 15.0, 2.6, NutrientTag.Protein),
        Row(11, "Rui fish", FoodCategory.Protein, "kg", KG, 350, 97.0, 16.6, 0.0, 1.4, 1.0, NutrientTag.Protein),
        Row(12, "Hilsa fish", FoodCategory.Protein, "kg", KG, 1200, 273.0, 21.8, 0.0, 19.4, 2.1, NutrientTag.Protein),
        Row(13, "Small fish (Mola)", FoodCategory.Protein, "kg", KG, 400, 113.0, 18.0, 0.0, 4.5, 5.7, NutrientTag.Iron),
        Row(14, "Shrimp", FoodCategory.Protein, "kg", KG, 700, 99.0, 24.0, 0.2, 0.3, 0.5, NutrientTag.Protein,
            allergens = setOf(Allergen.Shellfish)),
        Row(15, "Dried fish (Shutki)", FoodCategory.Protein, "kg", KG, 600, 285.0, 62.0, 0.0, 4.0, 4.0, NutrientTag.Protein,
            conditions = setOf(HealthCondition.Hypertension)),
        Row(16, "Chicken liver", FoodCategory.Protein, "kg", KG, 250, 119.0, 16.9, 0.7, 4.8, 9.0, NutrientTag.Iron),
        Row(17, "Peanuts", FoodCategory.Protein, "kg", KG, 180, 567.0, 25.8, 16.0, 49.0, 4.6, NutrientTag.Protein,
            allergens = setOf(Allergen.Peanuts)),

        // Veg
        Row(18, "Spinach (Palong)", FoodCategory.Veg, "kg", KG, 90, 23.0, 2.9, 3.6, 0.4, 2.7, NutrientTag.Iron),
        Row(19, "Red amaranth (Lal shak)", FoodCategory.Veg, "kg", KG, 60, 23.0, 2.5, 4.0, 0.3, 2.3, NutrientTag.Iron),
        Row(20, "Potato", FoodCategory.Veg, "kg", KG, 35, 77.0, 2.0, 17.0, 0.1, 0.8, NutrientTag.Carbs),
        Row(21, "Onion", FoodCategory.Veg, "kg", KG, 70, 40.0, 1.1, 9.3, 0.1, 0.2, NutrientTag.Carbs),
        Row(22, "Tomato", FoodCategory.Veg, "kg", KG, 60, 18.0, 0.9, 3.9, 0.2, 0.3, NutrientTag.Carbs),
        Row(23, "Eggplant (Begun)", FoodCategory.Veg, "kg", KG, 60, 25.0, 1.0, 6.0, 0.2, 0.2, NutrientTag.Carbs),
        Row(24, "Pumpkin (Mishti kumra)", FoodCategory.Veg, "kg", KG, 40, 26.0, 1.0, 6.5, 0.1, 0.8, NutrientTag.Carbs),
        Row(25, "Cauliflower", FoodCategory.Veg, "kg", KG, 50, 25.0, 1.9, 5.0, 0.3, 0.4, NutrientTag.Carbs),
        Row(26, "Bottle gourd (Lau)", FoodCategory.Veg, "kg", KG, 45, 14.0, 0.6, 3.4, 0.0, 0.2, NutrientTag.Carbs),
        Row(27, "Carrot", FoodCategory.Veg, "kg", KG, 60, 41.0, 0.9, 9.6, 0.2, 0.3, NutrientTag.Carbs),

        // Fruit
        Row(28, "Banana", FoodCategory.Fruit, "pcs", 100, 8, 89.0, 1.1, 23.0, 0.3, 0.3, NutrientTag.Carbs),
        Row(29, "Guava", FoodCategory.Fruit, "kg", KG, 80, 68.0, 2.6, 14.0, 1.0, 0.3, NutrientTag.Carbs),
        Row(30, "Papaya", FoodCategory.Fruit, "kg", KG, 50, 43.0, 0.5, 11.0, 0.3, 0.3, NutrientTag.Carbs),

        // Dairy
        Row(31, "Milk", FoodCategory.Dairy, "L", LITRE_MILK, 90, 61.0, 3.2, 4.8, 3.3, 0.0, NutrientTag.Protein,
            allergens = setOf(Allergen.Dairy)),
        Row(32, "Plain yogurt (Tok doi)", FoodCategory.Dairy, "kg", KG, 180, 61.0, 3.5, 4.7, 3.3, 0.1, NutrientTag.Protein,
            allergens = setOf(Allergen.Dairy)),

        // Oils
        Row(33, "Soybean oil", FoodCategory.Oils, "L", LITRE_OIL, 170, 884.0, 0.0, 0.0, 100.0, 0.0, NutrientTag.Fat),
        Row(34, "Mustard oil", FoodCategory.Oils, "L", LITRE_OIL, 195, 884.0, 0.0, 0.0, 100.0, 0.0, NutrientTag.Fat),
        Row(35, "Peanut oil", FoodCategory.Oils, "L", LITRE_OIL, 220, 884.0, 0.0, 0.0, 100.0, 0.0, NutrientTag.Fat,
            allergens = setOf(Allergen.Peanuts)),
        Row(36, "Rice bran oil", FoodCategory.Oils, "L", LITRE_OIL, 240, 884.0, 0.0, 0.0, 100.0, 0.0, NutrientTag.Fat),
        Row(37, "Sunflower oil", FoodCategory.Oils, "L", LITRE_OIL, 260, 884.0, 0.0, 0.0, 100.0, 0.0, NutrientTag.Fat),

        // Pantry
        Row(38, "White sugar", FoodCategory.Pantry, "kg", KG, 80, 387.0, 0.0, 100.0, 0.0, 0.0, NutrientTag.Carbs,
            conditions = setOf(HealthCondition.Diabetes)),
        Row(39, "Jaggery (Gur)", FoodCategory.Pantry, "kg", KG, 140, 383.0, 0.4, 98.0, 0.1, 5.0, NutrientTag.Carbs,
            conditions = setOf(HealthCondition.Diabetes)),
        Row(40, "Chanachur", FoodCategory.Pantry, "kg", KG, 250, 540.0, 15.0, 50.0, 32.0, 3.0, NutrientTag.Fat,
            allergens = setOf(Allergen.Peanuts), conditions = setOf(HealthCondition.Hypertension))
    )

    // Made-up percentage of the Rangpur figure, so regions differ in the demo
    private val regionPercent = mapOf(
        "Barishal Division" to 102,
        "Chattogram Division" to 106,
        "Dhaka Division" to 108,
        "Khulna Division" to 101,
        "Mymensingh Division" to 99,
        "Rajshahi Division" to 98,
        "Rangpur Division" to 100,
        "Sylhet Division" to 105
    )

    override val items: List<CatalogItemEntity> = rows.map {
        CatalogItemEntity(
            id = it.id,
            name = it.name,
            category = it.category,
            unit = it.unit,
            gramsPerUnit = it.gramsPerUnit,
            nutrientTag = it.tag,
            caloriesPer100g = it.kcal,
            proteinPer100g = it.protein,
            carbsPer100g = it.carbs,
            fatPer100g = it.fat,
            ironMgPer100g = it.ironMg,
            allergens = it.allergens,
            flaggedConditions = it.conditions
        )
    }

    override val prices: List<RegionPriceEntity> = rows.flatMap { row ->
        Regions.all.map { region ->
            RegionPriceEntity(
                catalogItemId = row.id,
                region = region,
                price = maxOf(1, (row.basePrice * regionPercent.getValue(region) + 50) / 100),
                updatedAt = null
            )
        }
    }
}
