package com.example.nutricart.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.domain.BudgetError
import com.example.nutricart.domain.BudgetRules
import com.example.nutricart.domain.PlanningPeriod
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.DayPeriod
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.FilterTagChip
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriFilledButton
import com.example.nutricart.ui.components.NutriTextField
import com.example.nutricart.ui.components.ProgressRail
import com.example.nutricart.ui.components.SectionLabel
import com.example.nutricart.ui.components.ThousandsVisualTransformation
import com.example.nutricart.ui.firstNameOf
import com.example.nutricart.ui.formatTk
import com.example.nutricart.ui.initialsOf
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// SCR-04. A bottom-navigation root: the bar is drawn by the nav host, below this content.
@Composable
fun HomeScreen(
    onOpenProfile: () -> Unit,
    // The budget and the days the list is planned for
    onGenerate: (Int, Int) -> Unit,
    onOpenList: () -> Unit,
    onOpenReview: (Long) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.generateRequest) {
        state.generateRequest?.let { budget ->
            viewModel.onGenerateHandled()
            onGenerate(budget, state.periodDays)
        }
    }

    // Read again each time the screen comes back to the foreground, so a greeting worked out
    // hours ago is not still showing
    var period by remember { mutableStateOf(DayPeriod.now()) }
    LifecycleResumeEffect(Unit) {
        period = DayPeriod.now()
        onPauseOrDispose {}
    }

    HomeContent(
        state = state,
        period = period,
        onBudgetChange = viewModel::onBudgetChange,
        onBudgetFocusLost = viewModel::onBudgetFocusLost,
        onPeriodSelected = viewModel::onPeriodSelected,
        onGenerate = viewModel::onGenerate,
        onOpenProfile = onOpenProfile,
        onOpenList = onOpenList,
        onOpenReview = onOpenReview
    )
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    period: DayPeriod,
    onBudgetChange: (String) -> Unit,
    onBudgetFocusLost: () -> Unit,
    onPeriodSelected: (Int) -> Unit,
    onGenerate: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenList: () -> Unit,
    onOpenReview: (Long) -> Unit
) {
    val colors = NutriCartTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.gutter, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(period.greetingRes),
                        style = NutriCartTheme.typography.caption,
                        color = colors.onSurfaceMuted
                    )
                    Text(
                        text = firstNameOf(state.accountName),
                        style = NutriCartTheme.typography.display,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Avatar(initials = initialsOf(state.accountName), onClick = onOpenProfile)
            }

            BudgetCard(
                state = state,
                onBudgetChange = onBudgetChange,
                onBudgetFocusLost = onBudgetFocusLost,
                onPeriodSelected = onPeriodSelected,
                onGenerate = onGenerate
            )

            val summary = state.currentList
            if (summary == null) {
                // Shown in place of the list summary until a list exists
                NutriCard(modifier = Modifier.fillMaxWidth()) {
                    EmptyState(
                        title = stringResource(R.string.home_empty_title),
                        message = stringResource(R.string.home_empty_message),
                        icon = painterResource(R.drawable.ic_list)
                    )
                }
            } else {
                CurrentListCard(
                    summary = summary,
                    onOpenList = onOpenList,
                    onOpenReview = { onOpenReview(summary.listId) }
                )
            }
        }
    }
}

@Composable
private fun Avatar(initials: String, onClick: () -> Unit) {
    val colors = NutriCartTheme.colors
    val description = stringResource(R.string.cd_open_profile)
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .clip(CircleShape)
            .background(colors.primaryContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = NutriCartTheme.typography.title,
            color = colors.onPrimaryContainer
        )
    }
}

@Composable
private fun BudgetCard(
    state: HomeUiState,
    onBudgetChange: (String) -> Unit,
    onBudgetFocusLost: () -> Unit,
    onPeriodSelected: (Int) -> Unit,
    onGenerate: () -> Unit
) {
    val colors = NutriCartTheme.colors
    val focusManager = LocalFocusManager.current
    var hadFocus by remember { mutableStateOf(false) }

    NutriCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                SectionLabel(text = stringResource(R.string.label_budget))
                // What the list will be sized and priced for
                if (state.householdSize != null && state.region != null) {
                    Text(
                        text = stringResource(
                            R.string.home_budget_context,
                            pluralStringResource(R.plurals.household_count, state.householdSize, state.householdSize),
                            state.region
                        ),
                        style = NutriCartTheme.typography.caption,
                        color = colors.onSurfaceMuted
                    )
                }
            }
            NutriTextField(
                value = state.budget,
                onValueChange = onBudgetChange,
                fieldModifier = Modifier.onFocusChanged {
                    if (hadFocus && !it.isFocused) onBudgetFocusLost()
                    hadFocus = it.isFocused
                },
                placeholder = stringResource(R.string.budget_placeholder),
                prefix = stringResource(R.string.budget_prefix),
                containerColor = colors.surfaceSunken,
                isError = state.budgetError != null,
                errorMessage = when (state.budgetError) {
                    BudgetError.BelowMinimum -> stringResource(
                        R.string.error_budget_min,
                        ThousandsVisualTransformation.format(state.minimumBudget.toString())
                    )
                    BudgetError.AboveMaximum -> stringResource(
                        R.string.error_budget_max,
                        ThousandsVisualTransformation.format(BudgetRules.MAX.toString())
                    )
                    null -> null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                visualTransformation = ThousandsVisualTransformation
            )
            // How long the list, and the budget above, are for
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionLabel(text = stringResource(R.string.home_period_label))
                // Wraps on a narrow screen, so the selected period is never off the edge
                FlowRow(
                    modifier = Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    PlanningPeriod.options.forEach { days ->
                        FilterTagChip(
                            text = planningPeriodLabel(days),
                            selected = state.periodDays == days,
                            onClick = { onPeriodSelected(days) }
                        )
                    }
                }
            }
            NutriFilledButton(
                text = stringResource(R.string.home_generate),
                onClick = {
                    focusManager.clearFocus()
                    onGenerate()
                },
                enabled = state.canGenerate
            )
        }
    }
}

// A month is offered as a month; anything shorter in weeks
@Composable
private fun planningPeriodLabel(days: Int): String =
    if (days == PlanningPeriod.MONTH_DAYS) {
        stringResource(R.string.period_one_month)
    } else {
        pluralStringResource(R.plurals.period_weeks, days / 7, days / 7)
    }

// The latest list at a glance. Tapping it opens the List tab.
@Composable
private fun CurrentListCard(summary: ListSummary, onOpenList: () -> Unit, onOpenReview: () -> Unit) {
    val colors = NutriCartTheme.colors
    NutriCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenList) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionLabel(text = stringResource(R.string.home_current_list))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = pluralStringResource(R.plurals.list_item_count, summary.itemCount, summary.itemCount),
                    modifier = Modifier.weight(1f),
                    style = NutriCartTheme.typography.titleLarge,
                    color = colors.onSurface
                )
                Text(
                    text = stringResource(R.string.home_list_total, formatTk(summary.total), formatTk(summary.budget)),
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
            }
            ProgressRail(progress = summary.usedFraction)
            if (summary.nutritionScore != null) {
                Text(
                    text = stringResource(R.string.home_nutrition_score, summary.nutritionScore),
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
            }
            // Shown only when something is waiting; opens the review, not the list
            if (summary.reviewCount > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_review_count, summary.reviewCount, summary.reviewCount),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onOpenReview),
                    style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.warning
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_list_used, summary.usedPercent),
                    modifier = Modifier.weight(1f),
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
                Text(
                    text = stringResource(R.string.home_view_list),
                    style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomePreview() {
    NutriCartTheme {
        HomeContent(
            state = HomeUiState(
                accountName = "Name Surname",
                region = "Rangpur Division",
                householdSize = 4,
                budget = "12000",
                currentList = ListSummary(itemCount = 23, total = 10941, budget = 12000)
            ),
            period = DayPeriod.Evening,
            onBudgetChange = {},
            onBudgetFocusLost = {},
            onPeriodSelected = {},
            onGenerate = {},
            onOpenProfile = {},
            onOpenList = {},
            onOpenReview = {}
        )
    }
}
