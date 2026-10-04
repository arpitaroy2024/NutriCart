package com.example.nutricart.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Design tokens from Screen Details, slide F-01. Hex values live only in this file.
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
    val danger: Color,
    val dangerContainer: Color,
    val outline: Color
)

val LightNutriCartColors = NutriCartColors(
    surface = Color(0xFFF5F8F6),
    surfaceCard = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFE7EEEA),
    onSurface = Color(0xFF122019),
    onSurfaceMuted = Color(0xFF566B60),
    onSurfaceFaint = Color(0xFF90A398),
    primary = Color(0xFF0F7A55),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3EFE1),
    onPrimaryContainer = Color(0xFF0A5A3E),
    warning = Color(0xFFC2740A),
    warningContainer = Color(0xFFFDF1DC),
    danger = Color(0xFFD14343),
    dangerContainer = Color(0xFFFCECEC),
    outline = Color(0xFFE2EAE6)
)

val DarkNutriCartColors = NutriCartColors(
    surface = Color(0xFF0E1A15),
    surfaceCard = Color(0xFF16241E),
    surfaceSunken = Color(0xFF0A1310),
    onSurface = Color(0xFFE9F2EC),
    onSurfaceMuted = Color(0xFF9CB3A7),
    onSurfaceFaint = Color(0xFF6D857A),
    primary = Color(0xFF3BD495),
    onPrimary = Color(0xFF04231A),
    primaryContainer = Color(0xFF0E3B2B),
    onPrimaryContainer = Color(0xFFA5E9C7),
    warning = Color(0xFFF0A83C),
    warningContainer = Color(0xFF33260E),
    danger = Color(0xFFF08585),
    dangerContainer = Color(0xFF331E1E),
    outline = Color(0xFF24352E)
)

val LocalNutriCartColors = staticCompositionLocalOf { LightNutriCartColors }
