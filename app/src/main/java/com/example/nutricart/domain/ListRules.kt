package com.example.nutricart.domain

// Limits for an item on a grocery list. The generator can produce large quantities for a
// big household (over 200 eggs for twenty people), so the ceiling is well above that.
object ListRules {
    const val MIN_QUANTITY = 1
    const val MAX_QUANTITY = 999
}

// A list's cost against its budget. Editing may take the total over the budget; that is
// reported, never hidden or corrected.
data class ListTotals(val total: Int, val budget: Int) {
    val remaining: Int get() = budget - total
    val overBudget: Boolean get() = total > budget

    // How far over the budget the list is; 0 when it is within it
    val overBudgetBy: Int get() = (total - budget).coerceAtLeast(0)

    val usedFraction: Float get() = if (budget > 0) total.toFloat() / budget else 0f
    val usedPercent: Int get() = if (budget > 0) (total.toLong() * 100 / budget).toInt() else 0
}
