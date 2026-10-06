package com.example.nutricart.ui.screens.grocerylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.example.nutricart.R
import com.example.nutricart.domain.ai.AiRecommendation
import com.example.nutricart.domain.ai.AiRecommendationType
import com.example.nutricart.domain.ai.AiResponse
import com.example.nutricart.ui.components.NutriCard
import com.example.nutricart.ui.components.NutriOutlinedButton
import com.example.nutricart.ui.components.TagChip
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

const val AiInsightsCardTag = "ai_insights_card"

private val TypeTagHeight = 24.dp

// The optional AI explanation of the list, shown above the items. It offers, waits, shows
// an answer or shows a failure; the list below it is the same in every case.
@Composable
fun AiInsightsCard(
    state: AiInsightsUiState,
    onRequest: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = NutriCartTheme.colors
    val typography = NutriCartTheme.typography
    // The offer is one line until it is opened, so it takes little room from the list
    var expanded by rememberSaveable { mutableStateOf(false) }
    val collapsed = state is AiInsightsUiState.Idle && !expanded
    NutriCard(
        modifier = modifier
            .fillMaxWidth()
            .testTag(AiInsightsCardTag),
        onClick = if (state is AiInsightsUiState.Idle) ({ expanded = !expanded }) else null,
        containerColor = colors.primaryContainer,
        border = null,
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = if (collapsed) Spacing.sm else Spacing.md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_sparkle),
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconInline),
                    tint = colors.primary
                )
                Text(
                    text = stringResource(R.string.ai_insights_title),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() },
                    style = typography.title,
                    color = colors.onSurface
                )
                if (state is AiInsightsUiState.Idle) {
                    Text(
                        text = stringResource(R.string.ai_insights_badge),
                        style = typography.micro,
                        color = colors.onPrimaryContainer
                    )
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_down),
                        contentDescription = null,
                        modifier = Modifier
                            .size(Sizes.iconInline)
                            .rotate(if (expanded) 180f else 0f),
                        tint = colors.onPrimaryContainer
                    )
                }
            }
            when (state) {
                AiInsightsUiState.Idle -> if (expanded) {
                    Text(
                        text = stringResource(R.string.ai_insights_intro),
                        style = typography.body,
                        color = colors.onSurface
                    )
                    Text(
                        text = stringResource(R.string.ai_insights_data_note),
                        style = typography.caption,
                        color = colors.onSurfaceMuted
                    )
                    NutriOutlinedButton(
                        text = stringResource(R.string.ai_insights_action),
                        onClick = onRequest,
                        leadingIcon = painterResource(R.drawable.ic_sparkle)
                    )
                }
                AiInsightsUiState.Loading -> Row(
                    modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Sizes.iconInline),
                        color = colors.primary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = stringResource(R.string.ai_insights_loading),
                        style = typography.body,
                        color = colors.onSurface
                    )
                }
                is AiInsightsUiState.Ready -> Answer(
                    response = state.response,
                    onDismiss = {
                        expanded = false
                        onDismiss()
                    }
                )
                is AiInsightsUiState.Failed -> {
                    Text(
                        text = stringResource(
                            when (state.reason) {
                                AiInsightsFailure.Connection -> R.string.ai_insights_error_connection
                                AiInsightsFailure.Unavailable -> R.string.ai_insights_error_unavailable
                            }
                        ),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = typography.body,
                        color = colors.onSurface
                    )
                    NutriOutlinedButton(text = stringResource(R.string.action_try_again), onClick = onRequest)
                }
            }
        }
    }
}

@Composable
private fun Answer(response: AiResponse, onDismiss: () -> Unit) {
    val colors = NutriCartTheme.colors
    val typography = NutriCartTheme.typography
    Text(
        text = response.summary,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        style = typography.body,
        color = colors.onSurface
    )
    Label(stringResource(R.string.ai_insights_reasoning_label))
    Text(text = response.reasoning, style = typography.caption, color = colors.onSurface)
    Label(stringResource(R.string.ai_insights_suggestions_label))
    // In the model's order, which it is asked to make most important first
    response.recommendations.forEach { recommendation ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    text = recommendation.title,
                    modifier = Modifier.weight(1f),
                    style = typography.body.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurface
                )
                TagChip(
                    text = stringResource(recommendation.type.labelRes()),
                    height = TypeTagHeight,
                    uppercase = true
                )
            }
            Text(text = recommendation.explanation, style = typography.caption, color = colors.onSurface)
        }
    }
    Text(
        text = stringResource(R.string.ai_insights_disclaimer),
        style = typography.caption,
        color = colors.onSurfaceMuted
    )
    NutriOutlinedButton(text = stringResource(R.string.ai_insights_hide), onClick = onDismiss)
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = NutriCartTheme.typography.micro,
        color = NutriCartTheme.colors.onSurfaceMuted
    )
}

private fun AiRecommendationType.labelRes(): Int = when (this) {
    AiRecommendationType.Budget -> R.string.ai_insights_type_budget
    AiRecommendationType.Nutrition -> R.string.ai_insights_type_nutrition
    AiRecommendationType.Variety -> R.string.ai_insights_type_variety
    AiRecommendationType.Other -> R.string.ai_insights_type_other
}

@PreviewLightDark
@Composable
private fun AiInsightsCardPreview() {
    NutriCartTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AiInsightsCard(state = AiInsightsUiState.Idle, onRequest = {}, onDismiss = {})
            AiInsightsCard(state = AiInsightsUiState.Loading, onRequest = {}, onDismiss = {})
            AiInsightsCard(
                state = AiInsightsUiState.Ready(
                    AiResponse(
                        summary = "Sample summary of the list.",
                        reasoning = "Sample reasoning from the figures supplied.",
                        recommendations = listOf(
                            AiRecommendation(AiRecommendationType.Variety, "Sample suggestion", "Sample explanation.")
                        )
                    )
                ),
                onRequest = {},
                onDismiss = {}
            )
            AiInsightsCard(
                state = AiInsightsUiState.Failed(AiInsightsFailure.Unavailable),
                onRequest = {},
                onDismiss = {}
            )
        }
    }
}
