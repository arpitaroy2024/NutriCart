package com.example.nutricart.data.local

import androidx.room.TypeConverter
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition

// Enum sets are stored as comma-separated names
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
}
