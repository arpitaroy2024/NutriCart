package com.example.nutricart.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes

// App logo: a circle with a ring, a cart glyph and a leaf mark. Decorative.
@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    diameter: Dp = Sizes.logo,
    fillColor: Color = NutriCartTheme.colors.primary,
    ringColor: Color = NutriCartTheme.colors.primary,
    glyphColor: Color = NutriCartTheme.colors.onPrimary,
    leafColor: Color = NutriCartTheme.colors.primaryContainer
) {
    Canvas(modifier = modifier.size(diameter)) {
        val d = size.minDimension
        val ring = Sizes.logoRing.toPx()
        drawCircle(color = fillColor, radius = d / 2)
        drawCircle(color = ringColor, radius = d / 2 - ring / 2, style = Stroke(ring))

        // Cart: handle, then the basket outline
        val cart = Path().apply {
            moveTo(d * 0.30f, d * 0.36f)
            lineTo(d * 0.36f, d * 0.36f)
            lineTo(d * 0.42f, d * 0.58f)
            lineTo(d * 0.64f, d * 0.58f)
            lineTo(d * 0.69f, d * 0.42f)
            lineTo(d * 0.38f, d * 0.42f)
        }
        drawPath(
            path = cart,
            color = glyphColor,
            style = Stroke(width = d * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color = glyphColor, radius = d * 0.025f, center = Offset(d * 0.45f, d * 0.66f))
        drawCircle(color = glyphColor, radius = d * 0.025f, center = Offset(d * 0.61f, d * 0.66f))

        val leaf = Path().apply {
            moveTo(d * 0.60f, d * 0.36f)
            quadraticTo(d * 0.60f, d * 0.25f, d * 0.72f, d * 0.25f)
            quadraticTo(d * 0.72f, d * 0.36f, d * 0.60f, d * 0.36f)
            close()
        }
        drawPath(path = leaf, color = leafColor)
    }
}
