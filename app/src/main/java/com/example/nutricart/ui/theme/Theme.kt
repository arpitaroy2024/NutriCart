package com.example.nutricart.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// Material's outline slot is left at its default until Login is restyled;
// components read the outline token from NutriCartTheme.colors instead.
private fun NutriCartColors.toColorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        background = surface,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceSunken,
        onSurfaceVariant = onSurfaceMuted,
        surfaceContainerLowest = surfaceCard,
        surfaceContainerLow = surfaceCard,
        surfaceContainer = surfaceCard,
        surfaceContainerHigh = surfaceCard,
        surfaceContainerHighest = surfaceCard,
        error = danger,
        errorContainer = dangerContainer,
        outlineVariant = outline
    )
}

@Composable
fun NutriCartTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkNutriCartColors else LightNutriCartColors

    CompositionLocalProvider(
        LocalNutriCartColors provides colors,
        LocalNutriCartTypography provides NutriCartType
    ) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = Typography,
            content = content
        )
    }
}

object NutriCartTheme {
    val colors: NutriCartColors
        @Composable
        @ReadOnlyComposable
        get() = LocalNutriCartColors.current

    val typography: NutriCartTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalNutriCartTypography.current
}
