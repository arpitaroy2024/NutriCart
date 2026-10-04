package com.example.nutricart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nutricart.R
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// C-07 Checkbox. With onCheckedChange it is a 48dp tap target of its own;
// without, it is only the visual box for use inside a larger toggleable row.
@Composable
fun NutriCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = Sizes.checkbox,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    val colors = NutriCartTheme.colors
    val toggle = if (onCheckedChange != null) {
        Modifier
            .size(Sizes.touchTarget)
            .clip(CircleShape)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
    } else {
        Modifier
    }
    Box(modifier = modifier.then(toggle), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .background(if (checked) colors.primary else colors.surfaceCard, shape)
                .border(
                    Sizes.focusedOutline,
                    if (checked) colors.primary else colors.onSurfaceFaint,
                    shape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.7f),
                    tint = colors.onPrimary
                )
            }
        }
    }
}

// C-07 with a label: the whole 48dp row is the target
@Composable
fun NutriCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        NutriCheckbox(checked = checked, onCheckedChange = null)
        Text(
            text = label,
            style = NutriCartTheme.typography.body,
            color = NutriCartTheme.colors.onSurface
        )
    }
}

// C-08 Radio, visual only
@Composable
fun NutriRadio(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = Sizes.radio
) {
    val colors = NutriCartTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .border(
                Sizes.focusedOutline,
                if (selected) colors.primary else colors.onSurfaceFaint,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(size * 0.45f)
                    .background(colors.primary, CircleShape)
            )
        }
    }
}

// C-08 with a label, one option per row. Put the rows in a Modifier.selectableGroup() parent.
@Composable
fun NutriRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.touchTarget)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        NutriRadio(selected = selected)
        Text(
            text = label,
            style = NutriCartTheme.typography.body,
            color = NutriCartTheme.colors.onSurface
        )
    }
}
