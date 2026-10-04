package com.example.nutricart.ui.screens.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.AppLogo
import com.example.nutricart.ui.components.NutriFilledButton
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing
import kotlinx.coroutines.launch

private class Pane(
    @param:StringRes val heading: Int,
    @param:StringRes val description: Int,
    @param:StringRes val leftChip: Int,
    @param:StringRes val rightChip: Int
)

// Pane 1 is from the PDF. Panes 2 and 3 are provisional and need product-owner approval.
private val panes = listOf(
    Pane(R.string.onboarding_1_heading, R.string.onboarding_1_description, R.string.onboarding_1_chip_left, R.string.onboarding_1_chip_right),
    Pane(R.string.onboarding_2_heading, R.string.onboarding_2_description, R.string.onboarding_2_chip_left, R.string.onboarding_2_chip_right),
    Pane(R.string.onboarding_3_heading, R.string.onboarding_3_description, R.string.onboarding_3_chip_left, R.string.onboarding_3_chip_right)
)

private val BannerHeight = 216.dp
private val BannerShape = RoundedCornerShape(20.dp)
private val BannerLogoSize = 88.dp
private val DotSize = 8.dp
private val ActiveDotWidth = 28.dp
private val DotGap = 10.dp

// SCR-02
@Composable
fun OnboardingScreen(
    storageError: Boolean,
    onFinished: () -> Unit,
    onRetry: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelFactory)
) {
    val finished by viewModel.finished.collectAsState()
    LaunchedEffect(finished) { if (finished) onFinished() }

    OnboardingContent(
        storageError = storageError,
        onComplete = viewModel::complete,
        onRetry = onRetry
    )
}

@Composable
private fun OnboardingContent(
    storageError: Boolean,
    onComplete: () -> Unit,
    onRetry: () -> Unit
) {
    val colors = NutriCartTheme.colors
    val pagerState = rememberPagerState(pageCount = { panes.size })
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Back steps to the previous pane; on pane 1 it is left to the system and exits the app
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    if (storageError) {
        val message = stringResource(R.string.onboarding_storage_error)
        val retry = stringResource(R.string.action_retry)
        LaunchedEffect(Unit) {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = retry,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) onRetry()
        }
    }

    Scaffold(
        containerColor = colors.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            TextButton(
                onClick = onComplete,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = Spacing.xs)
                    .heightIn(min = Sizes.touchTarget)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_skip),
                    style = NutriCartTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
                    color = colors.onSurfaceMuted
                )
            }

            HorizontalPager(state = pagerState) { page ->
                PaneContent(panes[page])
            }

            Spacer(modifier = Modifier.height(Spacing.xl))
            PagerDots(
                count = panes.size,
                current = pagerState.currentPage,
                modifier = Modifier.padding(horizontal = Spacing.gutter)
            )
            Spacer(modifier = Modifier.height(Spacing.xl))

            NutriFilledButton(
                text = stringResource(R.string.onboarding_get_started),
                onClick = {
                    if (pagerState.currentPage < panes.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier.padding(horizontal = Spacing.gutter)
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = stringResource(R.string.onboarding_footnote),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.gutter),
                style = NutriCartTheme.typography.caption,
                color = colors.onSurfaceMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun PaneContent(pane: Pane) {
    val colors = NutriCartTheme.colors
    Column(modifier = Modifier.padding(horizontal = Spacing.gutter)) {
        // Decorative, so it is hidden from accessibility services
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight)
                .background(colors.primaryContainer, BannerShape)
                .clearAndSetSemantics { },
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BannerChip(stringResource(pane.leftChip))
            AppLogo(
                diameter = BannerLogoSize,
                fillColor = colors.surfaceCard,
                ringColor = colors.surfaceCard,
                glyphColor = colors.primary,
                leafColor = colors.primary
            )
            BannerChip(stringResource(pane.rightChip))
        }
        Spacer(modifier = Modifier.height(Spacing.xl))
        // Reserved line counts keep every pane the same height
        Text(
            text = stringResource(pane.heading),
            style = NutriCartTheme.typography.headline,
            color = colors.onSurface,
            minLines = 2,
            maxLines = 2
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = stringResource(pane.description),
            style = NutriCartTheme.typography.body.copy(lineHeight = 24.sp),
            color = colors.onSurfaceMuted,
            minLines = 3,
            maxLines = 3
        )
    }
}

@Composable
private fun BannerChip(text: String) {
    val colors = NutriCartTheme.colors
    Box(
        modifier = Modifier
            .size(Sizes.touchTarget)
            .background(colors.surfaceCard, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = NutriCartTheme.typography.micro,
            color = colors.onPrimaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun PagerDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val colors = NutriCartTheme.colors
    Row(
        modifier = modifier.clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(DotGap)
    ) {
        repeat(count) { index ->
            val active = index == current
            val width by animateDpAsState(if (active) ActiveDotWidth else DotSize, label = "dot")
            Box(
                modifier = Modifier
                    .width(width)
                    .height(DotSize)
                    .background(
                        if (active) colors.primary else colors.onSurfaceFaint.copy(alpha = 0.5f),
                        NutriCartShapes.pill
                    )
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun OnboardingPreview() {
    NutriCartTheme {
        OnboardingContent(storageError = false, onComplete = {}, onRetry = {})
    }
}
