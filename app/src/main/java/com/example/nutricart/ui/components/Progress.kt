package com.example.nutricart.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.nutricart.ui.theme.Motion
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes

// C-11 Progress rail. progress is 0..1 and is capped at full width.
// warnBelowTarget switches the bar to the warning token under 60%.
@Composable
fun ProgressRail(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = NutriCartTheme.colors.primary,
    trackColor: Color = NutriCartTheme.colors.surfaceSunken,
    height: Dp = Sizes.rail,
    warnBelowTarget: Boolean = false
) {
    val clamped = progress.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(Motion.progress),
        label = "rail"
    )
    val barColor = if (warnBelowTarget && clamped < 0.6f) NutriCartTheme.colors.warning else color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .progressSemantics(clamped)
            .clip(NutriCartShapes.pill)
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .clip(NutriCartShapes.pill)
                .background(barColor)
        )
    }
}

// Circular progress used for the nutrition score and the generation percentage.
// Sweeps from zero on entry.
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    diameter: Dp = 148.dp,
    strokeWidth: Dp = 17.dp,
    color: Color = NutriCartTheme.colors.primary,
    trackColor: Color = NutriCartTheme.colors.surfaceSunken,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val clamped = progress.coerceIn(0f, 1f)
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(clamped) {
        sweep.animateTo(clamped, tween(Motion.progress))
    }
    Box(
        modifier = modifier
            .size(diameter)
            .progressSemantics(clamped),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * sweep.value,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
        }
        content()
    }
}
