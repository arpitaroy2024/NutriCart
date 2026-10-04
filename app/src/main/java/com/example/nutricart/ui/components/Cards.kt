package com.example.nutricart.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.example.nutricart.R
import com.example.nutricart.ui.theme.Elevation
import com.example.nutricart.ui.theme.NutriCartShapes
import com.example.nutricart.ui.theme.NutriCartTheme
import com.example.nutricart.ui.theme.Sizes
import com.example.nutricart.ui.theme.Spacing

// C-03 Card. leadingBarColor draws the 6dp severity bar on the leading edge.
@Composable
fun NutriCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = NutriCartTheme.colors.surfaceCard,
    shape: Shape = NutriCartShapes.card,
    border: BorderStroke? = BorderStroke(Sizes.outline, NutriCartTheme.colors.outline),
    leadingBarColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(Spacing.md),
    content: @Composable ColumnScope.() -> Unit
) {
    val cardColors = CardDefaults.cardColors(
        containerColor = containerColor,
        contentColor = NutriCartTheme.colors.onSurface
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = Elevation.card)
    val body: @Composable ColumnScope.() -> Unit = {
        if (leadingBarColor == null) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        } else {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(Sizes.alertBar)
                        .fillMaxHeight()
                        .background(leadingBarColor)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(contentPadding),
                    content = content
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = cardColors,
            elevation = elevation,
            border = border,
            content = body
        )
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = cardColors,
            elevation = elevation,
            border = border,
            content = body
        )
    }
}

enum class AlertTone { Danger, Warning, Positive }

// C-13 Alert card
@Composable
fun AlertCard(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    tone: AlertTone = AlertTone.Warning,
    showBar: Boolean = false,
    iconCircleSize: Dp = Sizes.alertIconCircle,
    titleStyle: TextStyle = NutriCartTheme.typography.title
) {
    val colors = NutriCartTheme.colors
    val container: Color
    val accent: Color
    val messageColor: Color
    val iconRes: Int
    when (tone) {
        AlertTone.Danger -> {
            container = colors.dangerContainer
            accent = colors.danger
            messageColor = colors.danger
            iconRes = R.drawable.ic_alert
        }
        AlertTone.Warning -> {
            container = colors.warningContainer
            accent = colors.warning
            messageColor = colors.warning
            iconRes = R.drawable.ic_alert
        }
        AlertTone.Positive -> {
            container = colors.primaryContainer
            accent = colors.primary
            messageColor = colors.onPrimaryContainer
            iconRes = R.drawable.ic_check
        }
    }

    NutriCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = container,
        border = null,
        leadingBarColor = if (showBar) accent else null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(iconCircleSize)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(iconCircleSize * 0.6f),
                    tint = colors.surfaceCard
                )
            }
            Column {
                Text(text = title, style = titleStyle, color = colors.onSurface)
                if (message != null) {
                    Text(
                        text = message,
                        style = NutriCartTheme.typography.caption,
                        color = messageColor
                    )
                }
            }
        }
    }
}
