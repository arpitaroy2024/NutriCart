package com.example.nutricart.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.example.nutricart.R
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing

// Sample states for checking the component library in Android Studio.
// The text and numbers here are placeholders, not app content.

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    NutriCartTheme {
        Surface(color = NutriCartTheme.colors.surface) {
            Column(
                modifier = Modifier.padding(Spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                content()
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun ButtonsPreview() {
    PreviewSurface {
        NutriFilledButton(text = "Filled button", onClick = {})
        NutriFilledButton(text = "Disabled", onClick = {}, enabled = false)
        NutriFilledButton(text = "Loading", onClick = {}, loading = true)
        NutriOutlinedButton(text = "Outlined button", onClick = {})
        NutriOutlinedButton(
            text = "With icon",
            onClick = {},
            leadingIcon = painterResource(R.drawable.ic_plus)
        )
        NutriOutlinedButton(
            text = "Danger variant",
            onClick = {},
            color = NutriCartTheme.colors.danger
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(
                icon = painterResource(R.drawable.ic_close),
                contentDescription = null,
                onClick = {},
                containerColor = NutriCartTheme.colors.dangerContainer,
                tint = NutriCartTheme.colors.danger
            )
            NutriOutlinedButton(
                text = "Small",
                onClick = {},
                fillWidth = false,
                textStyle = NutriCartTheme.typography.caption
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun InputsPreview() {
    PreviewSurface {
        var text by remember { mutableStateOf("12,000") }
        var search by remember { mutableStateOf("") }
        var option by remember { mutableStateOf("Option two") }
        SectionLabel(text = "Section label")
        NutriTextField(value = text, onValueChange = { text = it }, prefix = "Tk")
        NutriTextField(value = "", onValueChange = {}, placeholder = "Placeholder")
        NutriTextField(
            value = "",
            onValueChange = {},
            isError = true,
            errorMessage = "Error message shown below the field"
        )
        NutriSearchField(value = search, onValueChange = { search = it }, placeholder = "Search")
        NutriDropdown(
            value = option,
            options = listOf("Option one", "Option two", "Option three"),
            onSelect = { option = it }
        )
    }
}

@PreviewLightDark
@Composable
private fun SelectionPreview() {
    PreviewSurface {
        var count by remember { mutableIntStateOf(4) }
        var checked by remember { mutableStateOf(true) }
        var picked by remember { mutableIntStateOf(1) }
        var filter by remember { mutableIntStateOf(0) }
        NutriStepper(
            value = count,
            onValueChange = { count = it },
            modifier = Modifier.fillMaxWidth(),
            min = 1,
            max = 12,
            caption = "caption",
            horizontalArrangement = Arrangement.SpaceBetween
        )
        NutriStepper(
            value = count,
            onValueChange = { count = it },
            valueStyle = NutriCartTheme.typography.title
        )
        NutriCheckboxRow(label = "Checked", checked = checked, onCheckedChange = { checked = it })
        NutriCheckboxRow(label = "Unchecked", checked = false, onCheckedChange = {})
        Column(modifier = Modifier.selectableGroup()) {
            listOf("First", "Second").forEachIndexed { index, label ->
                NutriRadioRow(label = label, selected = picked == index, onSelect = { picked = index })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            TagChip(text = "Nutrient")
            TagChip(text = "Allergy", tone = TagTone.Allergy)
            TagChip(text = "Condition", tone = TagTone.Condition)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            listOf("All", "One", "Two").forEachIndexed { index, label ->
                FilterTagChip(text = label, selected = filter == index, onClick = { filter = index })
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun CardsPreview() {
    PreviewSurface {
        NutriCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Card", style = NutriCartTheme.typography.title)
            Text(
                text = "Supporting text",
                style = NutriCartTheme.typography.caption,
                color = NutriCartTheme.colors.onSurfaceMuted
            )
        }
        NutriCard(
            modifier = Modifier.fillMaxWidth(),
            leadingBarColor = NutriCartTheme.colors.danger
        ) {
            Text(text = "Card with severity bar", style = NutriCartTheme.typography.title)
        }
        AlertCard(title = "Danger alert", message = "Supporting line", tone = AlertTone.Danger)
        AlertCard(title = "Warning alert", message = "Supporting line", tone = AlertTone.Warning)
        AlertCard(title = "Positive alert", message = "Supporting line", tone = AlertTone.Positive)
    }
}

@PreviewLightDark
@Composable
private fun ProgressPreview() {
    PreviewSurface {
        ProgressRail(progress = 0.9f)
        ProgressRail(progress = 0.4f, warnBelowTarget = true)
        ProgressRail(progress = 1f, color = NutriCartTheme.colors.danger)
        ProgressRing(progress = 0.78f) {
            Text(
                text = "78",
                style = NutriCartTheme.typography.stat,
                color = NutriCartTheme.colors.onSurface
            )
        }
        EmptyState(
            title = "Empty state title",
            message = "One or two lines that explain what happened.",
            icon = painterResource(R.drawable.ic_alert)
        )
    }
}

@PreviewLightDark
@Composable
private fun BarsAndLogoPreview() {
    NutriCartTheme {
        Surface(color = NutriCartTheme.colors.surface) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NutriTopBar(title = "Pushed screen", onBack = {})
                NutriTopBar(title = "Root screen")
                AppLogo(modifier = Modifier.padding(Spacing.md))
                NutriBottomNav(selected = BottomNavTab.Home, onSelect = {})
            }
        }
    }
}
