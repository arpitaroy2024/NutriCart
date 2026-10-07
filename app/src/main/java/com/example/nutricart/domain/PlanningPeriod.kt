package com.example.nutricart.domain

// How many days a grocery list is planned for. A month counts as 30 days, which is what the
// basket template's per-person quantities and the nutrition reference were written for.
object PlanningPeriod {
    const val MONTH_DAYS = 30
    const val DEFAULT_DAYS = MONTH_DAYS

    // One, two and three weeks, and a month
    val options = listOf(7, 14, 21, MONTH_DAYS)

    // Anything that is not one of the options is treated as a month
    fun normalize(days: Int?): Int = days?.takeIf { it in options } ?: DEFAULT_DAYS
}
