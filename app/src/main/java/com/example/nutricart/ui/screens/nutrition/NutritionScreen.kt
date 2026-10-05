package com.example.nutricart.ui.screens.nutrition

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.domain.nutrition.Nutrient
import com.example.nutricart.domain.nutrition.NutritionAnalysis
import com.example.nutricart.domain.nutrition.NutritionFacts
import com.example.nutricart.domain.nutrition.NutritionInsight
import com.example.nutricart.domain.nutrition.NutritionReference
import com.example.nutricart.domain.nutrition.NutritionScore
import com.example.nutricart.domain.nutrition.ScoreBand
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.AlertCard
import com.example.nutricart.ui.components.AlertTone
import com.example.nutricart.ui.components.EmptyState
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.NutriTopBar
import com.example.nutricart.ui.components.ProgressRail
import com.example.nutricart.ui.components.ProgressRing
import com.example.nutricart.ui.components.SectionLabel
import com.example.nutricart.ui.components.TagChip
import com.example.nutricart.ui.components.ThousandsVisualTransformation
import com.example.nutricart.ui.labelRes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing
import kotlin.math.roundToInt

@StringRes
private fun Nutrient.nameRes(): Int = when (this) {
    Nutrient.Energy -> R.string.nutrition_energy
    Nutrient.Protein -> R.string.nutrition_protein
    Nutrient.Carbohydrate -> R.string.nutrition_carbohydrate
    Nutrient.Fat -> R.string.nutrition_fat
    Nutrient.Iron -> R.string.nutrition_iron
}

@StringRes
private fun Nutrient.unitRes(): Int = when (this) {
    Nutrient.Energy -> R.string.unit_kcal
    Nutrient.Iron -> R.string.unit_mg
    else -> R.string.unit_g
}

@StringRes
private fun ScoreBand.labelRes(): Int = when (this) {
    ScoreBand.Excellent -> R.string.nutrition_band_excellent
    ScoreBand.Good -> R.string.nutrition_band_good
    ScoreBand.Fair -> R.string.nutrition_band_fair
    ScoreBand.Low -> R.string.nutrition_band_low
}

// Whole numbers only: the data is not precise enough for decimals
private fun amount(value: Double): String = ThousandsVisualTransformation.format(value.roundToInt().toString())

// SCR-07. onBack is set when pushed from a list; as the Nutrition tab it is null, there is
// no back button and the bottom bar is drawn by the nav host. onGoHome is offered when
// there is no list yet.
@Composable
fun NutritionScreen(
    onBack: (() -> Unit)?,
    onEditList: (Long) -> Unit,
    onGoHome: (() -> Unit)?,
    viewModel: NutritionViewModel = viewModel(factory = AppViewModelFactory)
) {
    val state by viewModel.state.collectAsState()
    NutritionContent(
        state = state,
        onBack = onBack,
        onEditList = { state.list?.let { onEditList(it.id) } },
        onGoHome = onGoHome
    )
}

@Composable
private fun NutritionContent(
    state: NutritionUiState,
    onBack: (() -> Unit)?,
    onEditList: () -> Unit,
    onGoHome: (() -> Unit)?
) {
    val colors = NutriCartTheme.colors
    Scaffold(
        containerColor = colors.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { NutriTopBar(title = stringResource(R.string.nutrition_title), onBack = onBack) }
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
                // No list: an explanation and a way to make one, never a zero score
                state.list == null -> EmptyState(
                    title = stringResource(R.string.nutrition_no_list_title),
                    message = stringResource(R.string.nutrition_no_list_message),
                    icon = painterResource(R.drawable.ic_chart),
                    modifier = Modifier.align(Alignment.Center),
                    action = {
                        if (onGoHome != null) {
                            NutriOutlinedButton(text = stringResource(R.string.nutrition_go_home), onClick = onGoHome)
                        }
                    }
                )
                analysis == null -> EmptyState(
                    title = stringResource(R.string.nutrition_nothing_title),
                    message = stringResource(R.string.nutrition_nothing_message),
                    icon = painterResource(R.drawable.ic_chart),
                    modifier = Modifier.align(Alignment.Center),
                    action = { NutriOutlinedButton(text = stringResource(R.string.list_edit), onClick = onEditList) }
                )
                else -> Analysis(analysis = analysis, itemCount = state.itemCount, onEditList = onEditList)
            }
        }
    }
}

@Composable
private fun Analysis(analysis: NutritionAnalysis, itemCount: Int, onEditList: () -> Unit) {
    val colors = NutriCartTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = Spacing.gutter, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        ScoreCard(analysis = analysis, itemCount = itemCount)

        // Only the single largest gap is called out, never a list of warnings
        val gap = analysis.largestGap
        if (gap != null) {
            AlertCard(
                title = stringResource(
                    R.string.nutrition_gap_title,
                    stringResource(gap.nameRes()),
                    analysis.largestGapShortfallPercent ?: 0
                ),
                message = stringResource(R.string.nutrition_gap_message),
                tone = AlertTone.Warning
            )
        } else if (analysis.noMajorGaps) {
            AlertCard(
                title = stringResource(R.string.nutrition_no_gaps_title),
                message = stringResource(R.string.nutrition_no_gaps_message),
                tone = AlertTone.Positive
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionLabel(text = stringResource(R.string.nutrition_per_person_label))
            NutriCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Nutrient.entries.forEach { nutrient -> NutrientRow(analysis = analysis, nutrient = nutrient) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionLabel(text = stringResource(R.string.nutrition_variety_label))
            VarietyCard(analysis)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            SectionLabel(text = stringResource(R.string.nutrition_highlights_label))
            NutriCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    analysis.insights.forEach { insight ->
                        Text(
                            text = insightText(insight),
                            style = NutriCartTheme.typography.body,
                            color = colors.onSurface
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.nutrition_disclaimer),
            style = NutriCartTheme.typography.caption,
            color = colors.onSurfaceMuted
        )
        NutriOutlinedButton(text = stringResource(R.string.list_edit), onClick = onEditList)
        Spacer(modifier = Modifier.height(Spacing.xs))
    }
}

@Composable
private fun ScoreCard(analysis: NutritionAnalysis, itemCount: Int) {
    val colors = NutriCartTheme.colors
    val score = analysis.score
    // The colour follows the band, not the number
    val strong = score.band == ScoreBand.Excellent || score.band == ScoreBand.Good
    NutriCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProgressRing(
                progress = score.value / 100f,
                color = if (strong) colors.primary else colors.warning
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = score.value.toString(),
                        style = NutriCartTheme.typography.stat,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.nutrition_out_of_100).uppercase(),
                        style = NutriCartTheme.typography.label,
                        color = colors.onSurfaceMuted
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.nutrition_score_title),
                style = NutriCartTheme.typography.caption,
                color = colors.onSurfaceMuted
            )
            Text(
                text = stringResource(score.band.labelRes()),
                style = NutriCartTheme.typography.titleLarge,
                color = colors.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (analysis.gaps.isEmpty()) {
                    stringResource(R.string.nutrition_gaps_none)
                } else {
                    pluralStringResource(R.plurals.nutrition_gap_count, analysis.gaps.size, analysis.gaps.size)
                },
                style = NutriCartTheme.typography.caption,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(
                    R.string.nutrition_basis,
                    pluralStringResource(R.plurals.list_item_count, itemCount, itemCount),
                    pluralStringResource(R.plurals.household_count, analysis.householdSize, analysis.householdSize),
                    analysis.days
                ),
                style = NutriCartTheme.typography.caption,
                color = colors.onSurfaceFaint,
                textAlign = TextAlign.Center
            )
        }
    }
}

// One nutrient: what the list gives each person a day, against the reference
@Composable
private fun NutrientRow(analysis: NutritionAnalysis, nutrient: Nutrient) {
    val colors = NutriCartTheme.colors
    val unit = stringResource(nutrient.unitRes())
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(nutrient.nameRes()),
                modifier = Modifier.weight(1f),
                style = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            Text(
                text = stringResource(
                    R.string.nutrition_amount_of_reference,
                    amount(analysis.perPersonPerDay.amountOf(nutrient)),
                    amount(NutritionReference.daily.amountOf(nutrient)),
                    unit
                ),
                style = NutriCartTheme.typography.caption,
                color = colors.onSurfaceMuted
            )
        }
        // Capped at full width; under 60% of the reference the bar turns to the warning colour
        ProgressRail(progress = analysis.coverageOf(nutrient).toFloat(), warnBelowTarget = true)
    }
}

@Composable
private fun VarietyCard(analysis: NutritionAnalysis) {
    val colors = NutriCartTheme.colors
    NutriCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = stringResource(
                    R.string.nutrition_variety_count,
                    analysis.presentGroups.size,
                    NutritionReference.foodGroups.size
                ),
                style = NutriCartTheme.typography.body.copy(fontWeight = FontWeight.Bold),
                color = colors.onSurface
            )
            if (analysis.presentGroups.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    analysis.presentGroups.forEach { TagChip(text = stringResource(it.labelRes())) }
                }
            }
            if (analysis.missingGroups.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.nutrition_variety_missing, groupNames(analysis.missingGroups)),
                    style = NutriCartTheme.typography.caption,
                    color = colors.onSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun groupNames(groups: List<FoodCategory>): String =
    groups.map { stringResource(it.labelRes()) }.joinToString(", ")

// Each observation is a fixed sentence filled with the calculated figures
@Composable
private fun insightText(insight: NutritionInsight): String = when (insight) {
    is NutritionInsight.Variety -> if (insight.missing.isEmpty()) {
        stringResource(R.string.insight_variety_all)
    } else {
        stringResource(
            R.string.insight_variety_some,
            insight.present.size,
            insight.present.size + insight.missing.size,
            groupNames(insight.missing)
        )
    }
    is NutritionInsight.GrainsDominant -> stringResource(R.string.insight_grains_dominant, insight.percent)
    is NutritionInsight.OilsAndSugarHigh -> stringResource(R.string.insight_oils_and_sugar, insight.percent)
    is NutritionInsight.MacroOutOfRange -> stringResource(
        if (insight.below) R.string.insight_macro_below else R.string.insight_macro_above,
        stringResource(insight.nutrient.nameRes()),
        insight.percent,
        insight.rangeFromPercent,
        insight.rangeToPercent
    )
    is NutritionInsight.ProteinWellCovered -> stringResource(R.string.insight_protein_covered, insight.percent)
    is NutritionInsight.EnergyAboveReference -> stringResource(R.string.insight_energy_above, insight.percent)
    is NutritionInsight.ItemsNotCounted ->
        pluralStringResource(R.plurals.insight_items_not_counted, insight.count, insight.count)
}

@PreviewLightDark
@Composable
private fun NutritionPreview() {
    val perDay = NutritionFacts(energyKcal = 1830.0, proteinG = 62.0, carbsG = 283.0, fatG = 44.0, ironMg = 9.0)
    val coverage = Nutrient.entries.associateWith { perDay.amountOf(it) / NutritionReference.daily.amountOf(it) }
    NutriCartTheme {
        NutritionContent(
            state = NutritionUiState(
                loading = false,
                list = GroceryListEntity(id = 1, accountId = 1, budget = 12000, createdAt = 0),
                itemCount = 22,
                analysis = NutritionAnalysis(
                    householdSize = 4,
                    days = 30,
                    total = perDay * 120.0,
                    perPersonPerDay = perDay,
                    coverage = coverage,
                    score = NutritionScore(82, ScoreBand.Good, 0.8, 1.0, 0.8),
                    presentGroups = listOf(FoodCategory.Grains, FoodCategory.Protein, FoodCategory.Veg, FoodCategory.Dairy),
                    missingGroups = listOf(FoodCategory.Fruit),
                    insights = listOf(
                        NutritionInsight.Variety(
                            listOf(FoodCategory.Grains, FoodCategory.Protein, FoodCategory.Veg, FoodCategory.Dairy),
                            listOf(FoodCategory.Fruit)
                        ),
                        NutritionInsight.GrainsDominant(64)
                    ),
                    skippedItems = 0
                )
            ),
            onBack = null,
            onEditList = {},
            onGoHome = {}
        )
    }
}
