package com.example.nutricart.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

// Type roles from Screen Details, slide F-02
@Immutable
data class NutriCartTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val titleLarge: TextStyle,
    val title: TextStyle,
    val stat: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val label: TextStyle,
    val micro: TextStyle
)

private fun role(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp
)

val NutriCartType = NutriCartTypography(
    display = role(32, 38, FontWeight.Bold),
    headline = role(26, 32, FontWeight.Bold),
    titleLarge = role(22, 28, FontWeight.Bold),
    title = role(20, 26, FontWeight.Bold),
    stat = role(32, 36, FontWeight.Bold),
    body = role(16, 22, FontWeight.Normal),
    caption = role(14, 19, FontWeight.Normal),
    label = role(12, 16, FontWeight.Bold, tracking = 1.5),
    micro = role(11, 14, FontWeight.Bold)
)

val LocalNutriCartTypography = staticCompositionLocalOf { NutriCartType }
