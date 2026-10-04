package com.example.nutricart.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// Layout rules from Screen Details, slide F-02, and component metrics from slide F-03

object Spacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    // Left and right on every screen
    val gutter = 20.dp
}

object NutriCartShapes {
    val input = RoundedCornerShape(10.dp)
    val card = RoundedCornerShape(14.dp)
    val sheet = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val pill = CircleShape
}

object Elevation {
    val card = 0.dp
    val dockedBar = 6.dp
    val dialog = 12.dp
}

object Sizes {
    val touchTarget = 48.dp

    val iconInline = 20.dp
    val iconNav = 24.dp
    val iconLarge = 40.dp

    val outline = 1.dp
    val focusedOutline = 2.dp

    val filledButton = 56.dp
    val outlinedButton = 52.dp
    val input = 50.dp
    val searchField = 44.dp
    val stepperButton = 36.dp
    val checkbox = 22.dp
    val radio = 22.dp
    val chip = 32.dp
    val filterChip = 36.dp
    val selectChip = 40.dp
    val rail = 8.dp
    val bottomNav = 72.dp
    val topBar = 56.dp
    val alertBar = 6.dp
    val alertIconCircle = 28.dp
    val logo = 200.dp
    val logoRing = 4.dp
}

// Durations in milliseconds, standard easing
object Motion {
    const val progress = 400
    const val sheet = 250
    const val navFade = 150
}
