package com.example.nutricart.ui

import androidx.annotation.StringRes
import com.example.nutricart.R
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition

// Up to two initials for an avatar: "Arpita Roy" -> "AR"
fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

// The name used in the greeting: "Arpita Roy" -> "Arpita"
fun firstNameOf(name: String): String = name.trim().substringBefore(' ')

enum class DayPeriod(@param:StringRes val greetingRes: Int) {
    Morning(R.string.greeting_morning),
    Afternoon(R.string.greeting_afternoon),
    Evening(R.string.greeting_evening);

    companion object {
        // hour is 0..23
        fun forHour(hour: Int): DayPeriod = when (hour) {
            in 5..11 -> Morning
            in 12..16 -> Afternoon
            else -> Evening
        }
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
