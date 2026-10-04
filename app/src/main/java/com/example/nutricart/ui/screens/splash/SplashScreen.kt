package com.example.nutricart.ui.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nutricart.R
import com.example.nutricart.ui.AppViewModelFactory
import com.example.nutricart.ui.components.AppLogo
import com.example.nutricart.ui.theme.LightNutriCartColors
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Spacing
import kotlinx.coroutines.delay

private const val LOGO_SCALE_MS = 220
private const val INDICATOR_DELAY_MS = 400L

// SCR-01. Stays up for at least 600ms, then hands the routing decision to the nav host.
@Composable
fun SplashScreen(
    onResolved: (SplashResult) -> Unit,
    viewModel: SplashViewModel = viewModel(factory = AppViewModelFactory)
) {
    val result by viewModel.result.collectAsState()
    var minimumElapsed by remember { mutableStateOf(false) }
    var showIndicator by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(INDICATOR_DELAY_MS)
        showIndicator = true
        delay(SplashViewModel.MIN_MS - INDICATOR_DELAY_MS)
        minimumElapsed = true
    }
    LaunchedEffect(result, minimumElapsed) {
        val resolved = result
        if (resolved != null && minimumElapsed) onResolved(resolved)
    }

    // The indicator is skipped when the decision was ready within 400ms
    SplashContent(showIndicator = showIndicator && result == null)
}

@Composable
private fun SplashContent(showIndicator: Boolean) {
    // The splash is always dark, so it uses the light palette's values in both themes
    val colors = LightNutriCartColors
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, tween(LOGO_SCALE_MS)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.onSurface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = Spacing.gutter),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppLogo(
                modifier = Modifier.scale(scale.value),
                fillColor = colors.primary,
                ringColor = colors.primary,
                glyphColor = colors.onPrimary,
                leafColor = colors.primaryContainer
            )
            Spacer(modifier = Modifier.height(Spacing.xl))
            Text(
                text = stringResource(R.string.splash_app_name),
                style = NutriCartTheme.typography.display,
                color = colors.onPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                text = stringResource(R.string.splash_tagline),
                style = NutriCartTheme.typography.caption,
                color = colors.primary.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        if (showIndicator) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(80.dp)
                        .height(4.dp)
                        .clip(NutriCartShapes.pill),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp
                )
                Text(
                    text = stringResource(R.string.splash_preparing),
                    style = NutriCartTheme.typography.caption,
                    color = colors.primary.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Preview
@Composable
private fun SplashPreview() {
    NutriCartTheme {
        SplashContent(showIndicator = true)
    }
}
