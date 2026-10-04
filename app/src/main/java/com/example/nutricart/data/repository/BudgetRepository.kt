package com.example.nutricart.data.repository

import com.example.nutricart.data.SettingsStore
import com.example.nutricart.data.local.BudgetDao
import com.example.nutricart.data.local.BudgetEntity
import kotlinx.coroutines.flow.Flow

// The logged-in account's budgets. A budget row is written together with the
// list it produced, by GroceryListRepository.createList.
interface BudgetRepository {
    suspend fun latest(): BudgetEntity?

    fun observeLatest(): Flow<BudgetEntity?>
}

class LocalBudgetRepository(
    private val budgetDao: BudgetDao,
    private val settings: SettingsStore
) : BudgetRepository {

    override suspend fun latest(): BudgetEntity? = budgetDao.latest(settings.requireAccountId())

    override fun observeLatest(): Flow<BudgetEntity?> =
        settings.forCurrentAccount { budgetDao.observeLatest(it) }
}
