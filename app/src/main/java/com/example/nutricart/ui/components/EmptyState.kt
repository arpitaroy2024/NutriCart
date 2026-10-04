package com.example.nutricart.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// Empty or error state: 40dp icon, a title, an optional explanation and an optional action
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: Painter? = null,
    action: (@Composable () -> Unit)? = null
) {
    val colors = NutriCartTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        if (icon != null) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconLarge),
                tint = colors.onSurfaceMuted
            )
        }
        Text(
            text = title,
            style = NutriCartTheme.typography.title,
            color = colors.onSurface,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Text(
                text = message,
                style = NutriCartTheme.typography.body,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center
            )
        }
        action?.invoke()
    }
}
