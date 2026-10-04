package com.example.nutricart.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// C-01 Filled button
@Composable
fun NutriFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    height: Dp = Sizes.filledButton
) {
    val colors = NutriCartTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        shape = NutriCartShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            // A loading button keeps its colours so the spinner stays visible
            disabledContainerColor = if (loading) colors.primary else colors.surfaceSunken,
            disabledContentColor = if (loading) colors.onPrimary else colors.onSurfaceFaint
        ),
        contentPadding = PaddingValues(horizontal = Spacing.md)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Sizes.iconNav),
                color = colors.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text = text, style = NutriCartTheme.typography.title, maxLines = 1)
        }
    }
}

// C-02 Outlined button. Pass the danger token as color for the log out variant.
@Composable
fun NutriOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = NutriCartTheme.colors.primary,
    height: Dp = Sizes.outlinedButton,
    fillWidth: Boolean = true,
    leadingIcon: Painter? = null,
    textStyle: TextStyle = NutriCartTheme.typography.title
) {
    val colors = NutriCartTheme.colors
    val contentColor = if (enabled) color else colors.onSurfaceFaint
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(height),
        shape = NutriCartShapes.pill,
        border = BorderStroke(Sizes.focusedOutline, contentColor),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            disabledContentColor = colors.onSurfaceFaint
        ),
        contentPadding = PaddingValues(horizontal = Spacing.md)
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconInline)
            )
            Spacer(modifier = Modifier.width(Spacing.xs))
        }
        Text(text = text, style = textStyle, maxLines = 1)
    }
}

// Round icon button used by the stepper (C-06) and row actions such as delete.
// The visible circle is smaller than the 48dp hit area.
@Composable
fun CircleIconButton(
    icon: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = NutriCartTheme.colors.surfaceSunken,
    tint: Color = NutriCartTheme.colors.primary,
    size: Dp = Sizes.stepperButton
) {
    val colors = NutriCartTheme.colors
    Box(
        modifier = modifier
            .size(Sizes.touchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .background(containerColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(Sizes.iconInline),
                tint = if (enabled) tint else colors.onSurfaceFaint
            )
        }
    }
}
