package com.example.nutricart.ui.screens.grocerylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.example.nutricart.R
import com.example.nutricart.domain.ListTotals
import com.example.nutricart.ui.components.ProgressRail
import com.example.nutricart.ui.formatTk
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing

// A list's cost against its budget, used under the list and under the editor. Going over
// the budget is shown in the danger colour with the amount; nothing is hidden or clamped.
@Composable
fun BudgetSummary(
    totals: ListTotals,
    itemCount: Int,
    boughtCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = NutriCartTheme.colors
    val over = totals.overBudget
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SummaryRow(
            label = stringResource(R.string.list_estimated_total),
            detail = pluralStringResource(R.plurals.list_item_count, itemCount, itemCount) +
                if (boughtCount > 0) pluralStringResource(R.plurals.list_bought_suffix, boughtCount, boughtCount) else "",
            value = formatTk(totals.total),
            valueColor = colors.onSurface
        )
        SummaryRow(
            label = stringResource(if (over) R.string.list_over_budget else R.string.list_remaining_budget),
            detail = null,
            value = formatTk(if (over) totals.overBudgetBy else totals.remaining),
            valueColor = if (over) colors.danger else colors.primary
        )
        ProgressRail(
            progress = totals.usedFraction,
            color = if (over) colors.danger else colors.primary
        )
        Text(
            text = if (over) {
                stringResource(R.string.list_over_budget_note, formatTk(totals.overBudgetBy), formatTk(totals.budget))
            } else {
                stringResource(R.string.list_budget_used, totals.usedPercent, formatTk(totals.budget))
            },
            style = NutriCartTheme.typography.caption,
            color = if (over) colors.danger else colors.onSurfaceMuted
        )
    }
}

@Composable
private fun SummaryRow(label: String, detail: String?, value: String, valueColor: Color) {
    val colors = NutriCartTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = NutriCartTheme.typography.caption, color = colors.onSurfaceMuted)
            if (detail != null) {
                Text(text = detail, style = NutriCartTheme.typography.caption, color = colors.onSurfaceFaint)
            }
        }
        Text(
            text = value,
            style = NutriCartTheme.typography.stat.copy(fontSize = 24.sp, lineHeight = 28.sp),
            color = valueColor
        )
    }
}
