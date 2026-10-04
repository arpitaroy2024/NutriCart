package com.example.nutricart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.example.nutricart.R
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

// C-09 as one of several choices that can be on at the same time. A selected chip is filled
// and shows a check mark, so selection does not rely on colour alone.
@Composable
fun MultiSelectChip(
    text: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = Sizes.selectChip
) {
    val colors = NutriCartTheme.colors
    val content = if (selected) colors.onPrimary else colors.onSurface
    Row(
        modifier = modifier
            .height(height)
            .clip(NutriCartShapes.pill)
            .background(if (selected) colors.primary else colors.surfaceCard)
            .then(
                if (selected) Modifier
                else Modifier.border(Sizes.outline, colors.onSurfaceFaint, NutriCartShapes.pill)
            )
            .toggleable(
                value = selected,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onSelectedChange
            )
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(Spacing.md),
                tint = content
            )
        }
        Text(
            text = text,
            style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = content,
            maxLines = 1
        )
    }
}

// A value the user added, shown in the selected style with a cross. Tapping it removes it.
// Long text is cut with an ellipsis.
@Composable
fun RemovableChip(
    text: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Sizes.selectChip
) {
    val colors = NutriCartTheme.colors
    val description = stringResource(R.string.cd_remove, text)
    Row(
        modifier = modifier
            .height(height)
            .clip(NutriCartShapes.pill)
            .background(colors.primary)
            .clickable(role = Role.Button, onClick = onRemove)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f, fill = false),
            style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = null,
            modifier = Modifier.size(Spacing.md),
            tint = colors.onPrimary
        )
    }
}
