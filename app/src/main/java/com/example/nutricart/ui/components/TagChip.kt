package com.example.nutricart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

enum class TagTone { Nutrient, Allergy, Condition }

// C-09 Tag chip, display only
@Composable
fun TagChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: TagTone = TagTone.Nutrient,
    height: Dp = Sizes.chip,
    uppercase: Boolean = false
) {
    val colors = NutriCartTheme.colors
    val container: Color
    val content: Color
    when (tone) {
        TagTone.Nutrient -> {
            container = colors.primaryContainer
            content = colors.onPrimaryContainer
        }
        TagTone.Allergy -> {
            container = colors.dangerContainer
            content = colors.danger
        }
        TagTone.Condition -> {
            container = colors.warningContainer
            content = colors.warning
        }
    }
    Box(
        modifier = modifier
            .height(height)
            .background(container, NutriCartShapes.pill)
            .padding(horizontal = if (height < Sizes.chip) Spacing.xs else Spacing.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (uppercase) text.uppercase() else text,
            style = NutriCartTheme.typography.micro,
            color = content,
            maxLines = 1
        )
    }
}

// C-09 as a single-select filter: primary fill when selected
@Composable
fun FilterTagChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.filterChip
) {
    val colors = NutriCartTheme.colors
    Box(
        modifier = modifier
            .height(height)
            .clip(NutriCartShapes.pill)
            .background(if (selected) colors.primary else colors.surfaceCard)
            .then(
                if (selected) Modifier
                else Modifier.border(Sizes.outline, colors.outline, NutriCartShapes.pill)
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = Spacing.md),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = if (selected) colors.onPrimary else colors.onSurface,
            maxLines = 1
        )
    }
}
