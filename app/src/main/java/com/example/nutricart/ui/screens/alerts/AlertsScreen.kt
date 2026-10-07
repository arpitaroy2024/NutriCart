package com.example.nutricart.ui.screens.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.domain.conflicts.Alternative
import com.example.nutricart.domain.conflicts.ConcernReason
import com.example.nutricart.domain.conflicts.ConcernType
import com.example.nutricart.domain.conflicts.ConflictAnalysis
import com.example.nutricart.domain.conflicts.ItemConcern
import com.example.nutricart.domain.conflicts.ItemConflict
import com.example.nutricart.domain.conflicts.ListConsideration
import com.example.nutricart.domain.conflicts.Severity
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.AlertCard
import com.example.nutricart.ui.components.AlertTone
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.NutriTopBar
import com.example.nutricart.ui.components.SectionLabel
import com.example.nutricart.ui.components.TagChip
import com.example.nutricart.ui.components.TagTone
import com.example.nutricart.ui.formatAmount
import com.example.nutricart.ui.formatTk
import com.example.nutricart.ui.labelRes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing
import kotlin.math.roundToInt

// SCR-08, with SCR-12's detail folded into each card. Reached from Home, the list and the
// nutrition screen; always for the list in the route.
@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    viewModel: AlertsViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    AlertsContent(
        state = state,
        onBack = onBack,
        onKeepAnyway = viewModel::onKeepAnyway,
        onReviewAgain = viewModel::onReviewAgain,
        onReplace = viewModel::onReplace
    )
}

@Composable
private fun AlertsContent(
    state: AlertsUiState,
    onBack: () -> Unit,
    onKeepAnyway: (Long) -> Unit,
    onReviewAgain: (Long) -> Unit,
    onReplace: (Long, Alternative) -> Unit
) {
    val colors = NutriCartTheme.colors
    Scaffold(
        containerColor = colors.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { NutriTopBar(title = stringResource(R.string.review_title), onBack = onBack) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val analysis = state.analysis
            when {
                state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = colors.primary
                )
                state.list == null -> EmptyState(
                    title = stringResource(R.string.review_no_list_title),
                    message = stringResource(R.string.review_no_list_message),
                    icon = painterResource(R.drawable.ic_alert),
                    modifier = Modifier.align(Alignment.Center)
                )
                analysis == null || analysis.profileIsEmpty -> EmptyState(
                    title = stringResource(R.string.review_profile_empty_title),
                    message = stringResource(R.string.review_profile_empty_message),
                    icon = painterResource(R.drawable.ic_check),
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> Review(
                    analysis = analysis,
                    onKeepAnyway = onKeepAnyway,
                    onReviewAgain = onReviewAgain,
                    onReplace = onReplace
                )
            }
        }
    }
}

@Composable
private fun Review(
    analysis: ConflictAnalysis,
    onKeepAnyway: (Long) -> Unit,
    onReviewAgain: (Long) -> Unit,
    onReplace: (Long, Alternative) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Summary(analysis)

        // Allergen matches and nutrition considerations are never mixed in one section
        Section(
            titleRes = R.string.review_section_allergens,
            conflicts = analysis.allergenMatches,
            onKeepAnyway = onKeepAnyway,
            onReviewAgain = onReviewAgain,
            onReplace = onReplace
        )
        Section(
            titleRes = R.string.review_section_nutrition,
            conflicts = analysis.nutritionConsiderations,
            onKeepAnyway = onKeepAnyway,
            onReviewAgain = onReviewAgain,
            onReplace = onReplace
        )

        if (analysis.considerations.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SectionLabel(text = stringResource(R.string.review_section_list))
                analysis.considerations.forEach { consideration ->
                    AlertCard(
                        title = considerationTitle(consideration),
                        message = considerationText(consideration),
                        tone = AlertTone.Warning,
                        titleStyle = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        if (analysis.hasUnchecked || analysis.itemsWithoutNutritionData > 0) Unchecked(analysis)

        Text(
            text = stringResource(R.string.review_disclaimer),
            style = NutriCartTheme.typography.caption,
            color = NutriCartTheme.colors.onSurfaceMuted
        )
        Spacer(modifier = Modifier.height(Spacing.xs))
    }
}

@Composable
private fun Summary(analysis: ConflictAnalysis) {
    val titleStyle = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold)
    when {
        analysis.needsReview > 0 -> AlertCard(
            title = pluralStringResource(R.plurals.review_needs_review, analysis.needsReview, analysis.needsReview),
            message = stringResource(R.string.review_summary_message),
            // Red only for a direct allergen match that is still open
            tone = if (analysis.allergenMatches.any { !it.keptAnyway }) AlertTone.Danger else AlertTone.Warning,
            titleStyle = titleStyle
        )
        analysis.items.isNotEmpty() -> AlertCard(
            title = stringResource(R.string.review_all_kept_title),
            message = stringResource(R.string.review_all_kept_message),
            tone = AlertTone.Warning,
            titleStyle = titleStyle
        )
        // Nothing flagged is not the same as nothing to worry about, and the wording says so
        else -> AlertCard(
            title = stringResource(R.string.review_none_title),
            message = stringResource(R.string.review_none_message),
            tone = AlertTone.Positive,
            titleStyle = titleStyle
        )
    }
}

@Composable
private fun Section(
    titleRes: Int,
    conflicts: List<ItemConflict>,
    onKeepAnyway: (Long) -> Unit,
    onReviewAgain: (Long) -> Unit,
    onReplace: (Long, Alternative) -> Unit
) {
    if (conflicts.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionLabel(text = stringResource(titleRes))
        conflicts.forEach { conflict ->
            ConflictCard(
                conflict = conflict,
                onKeepAnyway = { onKeepAnyway(conflict.listItemId) },
                onReviewAgain = { onReviewAgain(conflict.listItemId) },
                onReplace = { onReplace(conflict.listItemId, it) }
            )
        }
    }
}

// One card per item, however many concerns it has
@Composable
private fun ConflictCard(
    conflict: ItemConflict,
    onKeepAnyway: () -> Unit,
    onReviewAgain: () -> Unit,
    onReplace: (Alternative) -> Unit
) {
    val colors = NutriCartTheme.colors
    val direct = conflict.severity == Severity.High
    NutriCard(
        modifier = Modifier.fillMaxWidth(),
        leadingBarColor = if (direct) colors.danger else colors.warning
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = conflict.itemName,
                    modifier = Modifier.weight(1f),
                    style = NutriCartTheme.typography.title,
                    color = colors.onSurface
                )
                TagChip(
                    text = stringResource(if (direct) R.string.review_badge_allergen else R.string.review_badge_nutrition),
                    tone = if (direct) TagTone.Allergy else TagTone.Condition,
                    uppercase = true
                )
            }
            if (conflict.concerns.size > 1) {
                Text(
                    text = pluralStringResource(R.plurals.review_concern_count, conflict.concerns.size, conflict.concerns.size),
                    style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurfaceMuted
                )
            }
            conflict.concerns.forEach { concern ->
                Text(
                    text = concernText(concern),
                    style = NutriCartTheme.typography.body,
                    color = colors.onSurface
                )
            }

            if (conflict.keptAnyway) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.review_kept),
                        modifier = Modifier.weight(1f),
                        style = NutriCartTheme.typography.caption,
                        color = colors.onSurfaceMuted
                    )
                    NutriOutlinedButton(
                        text = stringResource(R.string.review_review_again),
                        onClick = onReviewAgain,
                        height = Sizes.touchTarget,
                        fillWidth = false,
                        textStyle = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                    )
                }
            } else {
                Alternatives(conflict = conflict, onReplace = onReplace)
                NutriOutlinedButton(
                    text = stringResource(R.string.review_keep_anyway),
                    onClick = onKeepAnyway,
                    color = colors.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun Alternatives(conflict: ItemConflict, onReplace: (Alternative) -> Unit) {
    val colors = NutriCartTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = stringResource(R.string.review_alternatives_label),
            style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            color = colors.onSurfaceMuted
        )
        if (conflict.alternatives.isEmpty()) {
            Text(
                text = stringResource(R.string.review_no_alternative),
                style = NutriCartTheme.typography.caption,
                color = colors.onSurfaceMuted
            )
        }
        conflict.alternatives.forEach { alternative ->
            val replaceLabel = stringResource(R.string.cd_replace_with, alternative.name)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = alternative.name,
                        style = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(
                            R.string.review_alternative_detail,
                            formatAmount(alternative.quantity * alternative.packAmount, alternative.packMeasure),
                            formatTk(alternative.cost)
                        ),
                        style = NutriCartTheme.typography.caption,
                        color = colors.onSurfaceMuted
                    )
                }
                NutriOutlinedButton(
                    text = stringResource(R.string.review_replace),
                    onClick = { onReplace(alternative) },
                    modifier = Modifier.semantics { contentDescription = replaceLabel },
                    height = Sizes.touchTarget,
                    fillWidth = false,
                    textStyle = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

// What is on the profile that nothing here could check. Said plainly instead of left out.
@Composable
private fun Unchecked(analysis: ConflictAnalysis) {
    val colors = NutriCartTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SectionLabel(text = stringResource(R.string.review_section_unchecked))
        NutriCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val lines = buildList {
                    if (analysis.unmatchedCustomAllergies.isNotEmpty()) {
                        add(stringResource(R.string.review_unchecked_allergies, analysis.unmatchedCustomAllergies.joinToString(", ")))
                    }
                    if (analysis.conditionsWithoutRules.isNotEmpty()) {
                        add(stringResource(R.string.review_unchecked_conditions, conditionNames(analysis.conditionsWithoutRules)))
                    }
                    if (analysis.customConditions.isNotEmpty()) {
                        add(stringResource(R.string.review_unchecked_custom_conditions, analysis.customConditions.joinToString(", ")))
                    }
                    if (analysis.unevaluatedRuleIds.isNotEmpty()) {
                        add(stringResource(R.string.review_unchecked_rules))
                    }
                    if (analysis.itemsWithoutNutritionData > 0) {
                        add(
                            pluralStringResource(
                                R.plurals.review_items_without_data,
                                analysis.itemsWithoutNutritionData,
                                analysis.itemsWithoutNutritionData
                            )
                        )
                    }
                }
                lines.forEach {
                    Text(text = it, style = NutriCartTheme.typography.body, color = colors.onSurface)
                }
            }
        }
    }
}

@Composable
private fun conditionNames(conditions: List<HealthCondition>): String =
    conditions.map { stringResource(it.labelRes()) }.joinToString(", ")

// Each reason is a fixed sentence filled with the matched profile value and the figure
@Composable
private fun concernText(concern: ItemConcern): String = when (concern.reason) {
    ConcernReason.ContainsListedAllergen -> {
        val allergen = stringResource(concern.allergen?.labelRes() ?: R.string.review_badge_allergen)
        if (concern.customEntry != null) {
            stringResource(R.string.concern_allergen_custom, allergen, concern.customEntry)
        } else {
            stringResource(R.string.concern_allergen, allergen)
        }
    }
    ConcernReason.ContainsGluten -> stringResource(
        R.string.concern_gluten,
        stringResource(concern.allergen?.labelRes() ?: R.string.allergen_gluten),
        conditionNames(concern.conditions)
    )
    else -> stringResource(
        R.string.concern_carbohydrate,
        (concern.value ?: 0.0).roundToInt(),
        conditionNames(concern.conditions)
    )
}

@Composable
private fun considerationTitle(consideration: ListConsideration): String = stringResource(
    if (consideration.reason == ConcernReason.IronBelowReference) {
        R.string.consideration_iron_title
    } else {
        R.string.consideration_carbohydrate_title
    }
)

@Composable
private fun considerationText(consideration: ListConsideration): String = stringResource(
    if (consideration.reason == ConcernReason.IronBelowReference) {
        R.string.consideration_iron_message
    } else {
        R.string.consideration_carbohydrate_message
    },
    (consideration.value * 100).roundToInt(),
    (consideration.threshold * 100).roundToInt(),
    conditionNames(consideration.conditions)
)

@PreviewLightDark
@Composable
private fun AlertsPreview() {
    NutriCartTheme {
        AlertsContent(
            state = AlertsUiState(
                loading = false,
                list = GroceryListEntity(id = 1, accountId = 1, budget = 12000, createdAt = 0),
                itemCount = 12,
                analysis = ConflictAnalysis(
                    profileIsEmpty = false,
                    items = listOf(
                        ItemConflict(
                            listItemId = 1, catalogItemId = 1, itemName = "Sample item",
                            concerns = listOf(
                                ItemConcern(ConcernType.Allergy, Severity.High, ConcernReason.ContainsListedAllergen, allergen = Allergen.Peanuts)
                            ),
                            keptAnyway = false,
                            alternatives = listOf(Alternative(2, "Another item", "kg", 160, 2))
                        ),
                        ItemConflict(
                            listItemId = 2, catalogItemId = 3, itemName = "Sample item",
                            concerns = listOf(
                                ItemConcern(
                                    ConcernType.NutritionConsideration, Severity.Medium, ConcernReason.ConcentratedCarbohydrate,
                                    conditions = listOf(HealthCondition.Prediabetes), value = 100.0, threshold = 90.0
                                )
                            ),
                            keptAnyway = true,
                            alternatives = emptyList()
                        )
                    ),
                    considerations = emptyList(),
                    unmatchedCustomAllergies = listOf("Kiwi"),
                    conditionsWithoutRules = listOf(HealthCondition.Hypertension),
                    customConditions = emptyList(),
                    unevaluatedRuleIds = emptyList(),
                    itemsWithoutNutritionData = 0
                )
            ),
            onBack = {},
            onKeepAnyway = {},
            onReviewAgain = {},
            onReplace = { _, _ -> }
        )
    }
}
