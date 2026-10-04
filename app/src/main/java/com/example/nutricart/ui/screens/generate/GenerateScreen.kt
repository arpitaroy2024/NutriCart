package com.example.nutricart.ui.screens.generate

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriFilledButton
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.ProgressRing
import com.example.nutricart.ui.components.TagChip
import com.example.nutricart.ui.formatTk
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

@StringRes
private fun GenerateStep.labelRes(): Int = when (this) {
    GenerateStep.ReadingPrices -> R.string.generate_step_prices
    GenerateStep.BalancingFoodGroups -> R.string.generate_step_balance
    GenerateStep.FittingBudget -> R.string.generate_step_budget
    GenerateStep.ApplyingProfile -> R.string.generate_step_profile
}

// SCR-10. A real destination, so generation survives rotation. Back and Cancel both stop it.
@Composable
fun GenerateScreen(
    onListReady: (Long) -> Unit,
    onClosed: () -> Unit,
    viewModel: GenerateViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.listId) { state.listId?.let(onListReady) }
    LaunchedEffect(state.cancelled) { if (state.cancelled) onClosed() }

    // After a failure nothing is running, so back simply leaves
    BackHandler { if (state.error != null) onClosed() else viewModel.cancel() }

    GenerateContent(
        state = state,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onClose = onClosed
    )
}

@Composable
private fun GenerateContent(
    state: GenerateUiState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val colors = NutriCartTheme.colors
    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        Box(modifier = Modifier.systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.gutter, vertical = Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (state.error != null) {
                    Failure(state = state, onRetry = onRetry, onClose = onClose)
                } else {
                    Progress(state = state, onCancel = onCancel)
                }
            }
        }
    }
}

@Composable
private fun Progress(state: GenerateUiState, onCancel: () -> Unit) {
    val colors = NutriCartTheme.colors
    Text(
        text = stringResource(R.string.generate_title),
        style = NutriCartTheme.typography.headline,
        color = colors.onSurface,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(Spacing.xxs))
    Text(
        text = stringResource(R.string.generate_subtitle),
        style = NutriCartTheme.typography.caption,
        color = colors.onSurfaceMuted,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(Spacing.xl))

    ProgressRing(progress = state.progress) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.generate_percent, (state.progress * 100).toInt()),
                style = NutriCartTheme.typography.stat,
                color = colors.onSurface
            )
            Text(
                text = stringResource(R.string.generate_ring_label).uppercase(),
                style = NutriCartTheme.typography.label,
                color = colors.onSurfaceMuted
            )
        }
    }
    Spacer(modifier = Modifier.height(Spacing.xl))

    NutriCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.md)
    ) {
        GenerateStep.entries.forEachIndexed { index, step ->
            if (index > 0) HorizontalDivider(thickness = Sizes.outline, color = colors.outline)
            StepRow(
                label = stringResource(step.labelRes()),
                done = index < state.completedSteps,
                active = step == state.activeStep
            )
        }
    }
    Spacer(modifier = Modifier.height(Spacing.md))

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        TagChip(text = stringResource(R.string.generate_budget_chip, formatTk(state.budget)))
        if (state.householdSize != null) {
            TagChip(text = pluralStringResource(R.plurals.household_count, state.householdSize, state.householdSize))
        }
    }
    Spacer(modifier = Modifier.height(Spacing.xl))

    NutriOutlinedButton(
        text = stringResource(R.string.action_cancel),
        onClick = onCancel,
        enabled = !state.saving,
        color = colors.onSurfaceMuted
    )
}

@Composable
private fun StepRow(label: String, done: Boolean, active: Boolean) {
    val colors = NutriCartTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(modifier = Modifier.size(Spacing.xl), contentAlignment = Alignment.Center) {
            when {
                done -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(Spacing.md),
                        tint = colors.onPrimary
                    )
                }
                active -> CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.primary,
                    trackColor = colors.surfaceSunken,
                    strokeWidth = 3.dp
                )
                else -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(Sizes.focusedOutline, colors.surfaceSunken, CircleShape)
                )
            }
        }
        Text(
            text = label,
            style = NutriCartTheme.typography.body.copy(
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (done || active) colors.onSurface else colors.onSurfaceFaint
        )
    }
}

@Composable
private fun Failure(state: GenerateUiState, onRetry: () -> Unit, onClose: () -> Unit) {
    val message = when (state.error) {
        GenerateError.BudgetTooSmall -> if (state.minimumBudget != null) {
            stringResource(R.string.generate_error_budget_with_amount, formatTk(state.minimumBudget))
        } else {
            stringResource(R.string.generate_error_budget)
        }
        GenerateError.NoPrices -> stringResource(R.string.generate_error_no_prices, state.region.orEmpty())
        GenerateError.NothingEligible -> stringResource(R.string.generate_error_nothing_eligible)
        else -> stringResource(R.string.generate_error_unexpected)
    }
    EmptyState(
        title = stringResource(R.string.generate_error_title),
        message = message,
        icon = painterResource(R.drawable.ic_alert),
        action = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                // Trying again only helps when something went wrong, not when the inputs cannot work
                if (state.error == GenerateError.Unexpected) {
                    NutriFilledButton(text = stringResource(R.string.action_try_again), onClick = onRetry)
                }
                NutriOutlinedButton(text = stringResource(R.string.generate_back_home), onClick = onClose)
            }
        }
    )
}

@PreviewLightDark
@Composable
private fun GeneratePreview() {
    NutriCartTheme {
        GenerateContent(
            state = GenerateUiState(budget = 12000, householdSize = 4, completedSteps = 2),
            onCancel = {},
            onRetry = {},
            onClose = {}
        )
    }
}

@PreviewLightDark
@Composable
private fun GenerateFailurePreview() {
    NutriCartTheme {
        GenerateContent(
            state = GenerateUiState(
                budget = 500,
                householdSize = 4,
                error = GenerateError.BudgetTooSmall,
                minimumBudget = 950
            ),
            onCancel = {},
            onRetry = {},
            onClose = {}
        )
    }
}
