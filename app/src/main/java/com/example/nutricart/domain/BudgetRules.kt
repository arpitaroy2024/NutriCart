package com.example.nutricart.domain

enum class BudgetError { BelowMinimum, AboveMaximum }

// Budget limits from SCR-04: whole Tk, numeric only, 500 to 999,999 for a month
object BudgetRules {
    const val MIN = 500
    const val MAX = 999_999
    const val MAX_DIGITS = 7

    // The smallest budget accepted for a list of that many days: the monthly minimum in
    // proportion, rounded up to Tk 50 (150, 250 and 350 for one, two and three weeks)
    fun minimum(days: Int = PlanningPeriod.DEFAULT_DAYS): Int =
        if (days >= PlanningPeriod.MONTH_DAYS) MIN else (MIN * days / PlanningPeriod.MONTH_DAYS + 49) / 50 * 50

    // Keeps digits only and caps the length, so anything typed stays a number
    fun sanitize(input: String): String = input.filter { it.isDigit() }.take(MAX_DIGITS)

    // Null for a blank or valid amount
    fun validate(text: String, days: Int = PlanningPeriod.DEFAULT_DAYS): BudgetError? {
        val amount = text.toIntOrNull() ?: return null
        return when {
            amount < minimum(days) -> BudgetError.BelowMinimum
            amount > MAX -> BudgetError.AboveMaximum
            else -> null
        }
    }

    fun isValid(text: String, days: Int = PlanningPeriod.DEFAULT_DAYS): Boolean =
        text.toIntOrNull() != null && validate(text, days) == null
}
