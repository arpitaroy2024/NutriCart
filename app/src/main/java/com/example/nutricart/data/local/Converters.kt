package com.example.nutricart.data.local

import androidx.room.TypeConverter
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import org.json.JSONArray

// Enum sets are stored as comma-separated names. Typed text is stored as a JSON array,
// so a value may contain any character.
class Converters {
    @TypeConverter
    fun allergensToString(value: Set<Allergen>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToAllergens(value: String): Set<Allergen> =
        value.split(",").filter { it.isNotEmpty() }.map { Allergen.valueOf(it) }.toSet()

    @TypeConverter
    fun conditionsToString(value: Set<HealthCondition>): String = value.joinToString(",") { it.name }

    @TypeConverter
    fun stringToConditions(value: String): Set<HealthCondition> =
        value.split(",").filter { it.isNotEmpty() }.map { HealthCondition.valueOf(it) }.toSet()

    @TypeConverter
    fun textListToJson(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter
    fun jsonToTextList(value: String): List<String> {
        val array = JSONArray(value)
        return List(array.length()) { array.getString(it) }
    }
}
