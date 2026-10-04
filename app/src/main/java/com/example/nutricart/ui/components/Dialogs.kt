package com.example.nutricart.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.nutricart.ui.theme.Elevation
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme

// A two-button confirmation. Dismissing it any other way counts as the dismiss action.
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmColor: Color = NutriCartTheme.colors.primary
) {
    val colors = NutriCartTheme.colors
    val buttonStyle = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = NutriCartTheme.typography.titleLarge) },
        text = { Text(text = message, style = NutriCartTheme.typography.body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = confirmLabel, style = buttonStyle, color = confirmColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel, style = buttonStyle, color = colors.onSurfaceMuted)
            }
        },
        shape = NutriCartShapes.card,
        containerColor = colors.surfaceCard,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceMuted,
        tonalElevation = Elevation.dialog
    )
}
