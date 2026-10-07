package com.example.nutricart.ui

import androidx.annotation.StringRes
import com.example.nutricart.R
import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.NutrientTag
import com.example.nutricart.data.model.PackMeasure
import com.example.nutricart.ui.components.ThousandsVisualTransformation
import kotlin.math.abs
import java.util.Calendar

// Up to two initials for an avatar: "Arpita Roy" -> "AR"
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

// An amount in taka with thousands separators: "Tk 10,850", "-Tk 250"
fun formatTk(amount: Int): String =
    (if (amount < 0) "-" else "") + "Tk " + ThousandsVisualTransformation.format(abs(amount.toLong()).toString())

/*
 * A physical amount as a shopper would say it: "250 g", "1 kg", "1.5 kg", "500 ml", "1.5 L",
 * "6 pcs". Grams and millilitres switch to kilos and litres from a thousand up, with only
 * the decimals that are needed. The one place amounts are put into words.
 */
fun formatAmount(amount: Int, measure: PackMeasure): String = when (measure) {
    PackMeasure.Gram -> scaledAmount(amount, "g", "kg")
    PackMeasure.Millilitre -> scaledAmount(amount, "ml", "L")
    PackMeasure.Piece -> "$amount pcs"
}

private fun scaledAmount(amount: Int, small: String, large: String): String {
    if (amount < 1000) return "$amount $small"
    val fraction = (amount % 1000).toString().padStart(3, '0').trimEnd('0')
    return (amount / 1000).toString() + (if (fraction.isEmpty()) "" else ".$fraction") + " $large"
}

// How much a number of packs of this item comes to: 3 packs of 100 g is "300 g"
fun CatalogItemEntity.amountLabel(quantity: Int): String = formatAmount(quantity * packAmount, packMeasure)

// How much of the item the list holds
fun ListItemWithCatalog.amountLabel(): String = catalog.amountLabel(item.quantity)

// The name used in the greeting: "Arpita Roy" -> "Arpita"
fun firstNameOf(name: String): String = name.trim().substringBefore(' ')

enum class DayPeriod(@param:StringRes val greetingRes: Int) {
    Morning(R.string.greeting_morning),
    Afternoon(R.string.greeting_afternoon),
    Evening(R.string.greeting_evening),
    Night(R.string.greeting_night);

    companion object {
        // hour is 0..23. Morning 5 to 11, afternoon 12 to 16, evening 17 to 20, night 21 to 4.
        fun forHour(hour: Int): DayPeriod = when (hour) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            in 17..20 -> Evening
            else -> Night
        }

        // The period right now, by the device's own clock and time zone
        fun now(): DayPeriod = forHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
}

@StringRes
fun Allergen.labelRes(): Int = when (this) {
    Allergen.Dairy -> R.string.allergen_dairy
    Allergen.Eggs -> R.string.allergen_eggs
    Allergen.Fish -> R.string.allergen_fish
    Allergen.Shellfish -> R.string.allergen_shellfish
    Allergen.Poultry -> R.string.allergen_poultry
    Allergen.Beef -> R.string.allergen_beef
    Allergen.Soy -> R.string.allergen_soy
    Allergen.Peanuts -> R.string.allergen_peanuts
    Allergen.TreeNuts -> R.string.allergen_tree_nuts
    Allergen.Gluten -> R.string.allergen_gluten
    Allergen.Sesame -> R.string.allergen_sesame
}

@StringRes
fun HealthCondition.labelRes(): Int = when (this) {
    HealthCondition.Diabetes -> R.string.condition_diabetes
    HealthCondition.Prediabetes -> R.string.condition_prediabetes
    HealthCondition.Hypertension -> R.string.condition_hypertension
    HealthCondition.HighCholesterol -> R.string.condition_high_cholesterol
    HealthCondition.HeartDisease -> R.string.condition_heart_disease
    HealthCondition.KidneyDisease -> R.string.condition_kidney_disease
    HealthCondition.LiverDisease -> R.string.condition_liver_disease
    HealthCondition.Pcos -> R.string.condition_pcos
    HealthCondition.Thyroid -> R.string.condition_thyroid
    HealthCondition.Anemia -> R.string.condition_anemia
    HealthCondition.Celiac -> R.string.condition_celiac
}

@StringRes
fun FoodCategory.labelRes(): Int = when (this) {
    FoodCategory.Protein -> R.string.category_protein
    FoodCategory.Grains -> R.string.category_grains
    FoodCategory.Veg -> R.string.category_veg
    FoodCategory.Fruit -> R.string.category_fruit
    FoodCategory.Dairy -> R.string.category_dairy
    FoodCategory.Oils -> R.string.category_oils
    FoodCategory.Pantry -> R.string.category_pantry
}

@StringRes
fun NutrientTag.labelRes(): Int = when (this) {
    NutrientTag.Protein -> R.string.nutrient_protein
    NutrientTag.Carbs -> R.string.nutrient_carbs
    NutrientTag.Fat -> R.string.nutrient_fat
    NutrientTag.Iron -> R.string.nutrient_iron
}
