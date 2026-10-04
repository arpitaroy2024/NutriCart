package com.example.nutricart.domain

enum class BudgetError { BelowMinimum, AboveMaximum }

// Monthly budget limits from SCR-04: whole Tk, numeric only, 500 to 999,999
object BudgetRules {
    const val MIN = 500
    const val MAX = 999_999
    const val MAX_DIGITS = 7

    // Keeps digits only and caps the length, so anything typed stays a number
    fun sanitize(input: String): String = input.filter { it.isDigit() }.take(MAX_DIGITS)

    // Null for a blank or valid amount
    fun validate(text: String): BudgetError? {
        val amount = text.toIntOrNull() ?: return null
        return when {
            amount < MIN -> BudgetError.BelowMinimum
            amount > MAX -> BudgetError.AboveMaximum
            else -> null
        }
    }

    fun isValid(text: String): Boolean = text.toIntOrNull() != null && validate(text) == null
}
