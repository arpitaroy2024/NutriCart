package com.example.nutricart.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.nutricart.data.model.FoodCategory

// The tint of one food category: a soft tile and the icon drawn on it
@Immutable
data class CategoryTone(val container: Color, val content: Color)

// Design tokens from Screen Details, slide F-01, warmed for the fresh-market look.
// Hex values live only in this file.
@Immutable
data class NutriCartColors(
    val surface: Color,
    val surfaceCard: Color,
    val surfaceSunken: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val onSurfaceFaint: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    // Warning text on its container; warning itself is for bars, icons and fills
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val outline: Color,
    // Turmeric, for money and budget figures
    val accent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val categoryTones: Map<FoodCategory, CategoryTone>
) {
    fun toneOf(category: FoodCategory): CategoryTone =
        categoryTones[category] ?: CategoryTone(primaryContainer, onPrimaryContainer)
}

val LightNutriCartColors = NutriCartColors(
    surface = Color(0xFFF7F6F1),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFECEBE3),
    onSurface = Color(0xFF122019),
    onSurfaceMuted = Color(0xFF566B60),
    onSurfaceFaint = Color(0xFF66776D),
    primary = Color(0xFF0F7A55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EFE1),
    onPrimaryContainer = Color(0xFF0A5A3E),
    warning = Color(0xFFC2740A),
    warningContainer = Color(0xFFFDF1DC),
    onWarningContainer = Color(0xFF8A4D00),
    danger = Color(0xFFD14343),
    dangerContainer = Color(0xFFFCECEC),
    outline = Color(0xFFE6E5DC),
    accent = Color(0xFFE0A100),
    accentContainer = Color(0xFFFFF3D1),
    onAccentContainer = Color(0xFF7A5200),
    categoryTones = mapOf(
        FoodCategory.Protein to CategoryTone(Color(0xFFFCE4DE), Color(0xFFB4472F)),
        FoodCategory.Grains to CategoryTone(Color(0xFFFBEBC8), Color(0xFF8A5D00)),
        FoodCategory.Veg to CategoryTone(Color(0xFFDDF0D6), Color(0xFF2F7A2B)),
        FoodCategory.Fruit to CategoryTone(Color(0xFFFFE3D0), Color(0xFFC2560C)),
        FoodCategory.Dairy to CategoryTone(Color(0xFFDDEBFA), Color(0xFF2B62A8)),
        FoodCategory.Oils to CategoryTone(Color(0xFFFFF1BF), Color(0xFF8F7200)),
        FoodCategory.Pantry to CategoryTone(Color(0xFFEBE4F5), Color(0xFF6A4BA3))
    )
)

val DarkNutriCartColors = NutriCartColors(
    surface = Color(0xFF0E1A15),
    surfaceCard = Color(0xFF16241E),
    surfaceSunken = Color(0xFF0A1310),
    onSurface = Color(0xFFE9F2EC),
    onSurfaceMuted = Color(0xFF9CB3A7),
    onSurfaceFaint = Color(0xFF8AA196),
    primary = Color(0xFF3BD495),
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF0E3B2B),
    onPrimaryContainer = Color(0xFFA5E9C7),
    warning = Color(0xFFF0A83C),
    warningContainer = Color(0xFF33260E),
    onWarningContainer = Color(0xFFF5C16C),
    danger = Color(0xFFF08585),
    dangerContainer = Color(0xFF331E1E),
    outline = Color(0xFF24352E),
    accent = Color(0xFFF2C14E),
    accentContainer = Color(0xFF3A2E0E),
    onAccentContainer = Color(0xFFF7D98A),
    categoryTones = mapOf(
        FoodCategory.Protein to CategoryTone(Color(0xFF3A1F19), Color(0xFFF2A492)),
        FoodCategory.Grains to CategoryTone(Color(0xFF3A2E10), Color(0xFFEFCB7A)),
        FoodCategory.Veg to CategoryTone(Color(0xFF1B3319), Color(0xFF9BD993)),
        FoodCategory.Fruit to CategoryTone(Color(0xFF3D2412), Color(0xFFF5B083)),
        FoodCategory.Dairy to CategoryTone(Color(0xFF172A40), Color(0xFF9DC3F2)),
        FoodCategory.Oils to CategoryTone(Color(0xFF383010), Color(0xFFE8D36B)),
        FoodCategory.Pantry to CategoryTone(Color(0xFF2A2140), Color(0xFFC7B2EE))
    )
)

val LocalNutriCartColors = staticCompositionLocalOf { LightNutriCartColors }
