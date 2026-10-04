package com.example.nutricart.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.nutricart.ui.theme.NutriCartTheme

// C-10 Section label
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = NutriCartTheme.typography.label,
        color = NutriCartTheme.colors.onSurfaceMuted
    )
}
