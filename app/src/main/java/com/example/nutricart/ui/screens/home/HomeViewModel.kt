package com.example.nutricart.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.BudgetError
import com.example.nutricart.domain.BudgetRules
import com.example.nutricart.domain.PlanningPeriod
import com.example.nutricart.domain.nutrition.NutritionAnalyzer
import com.example.nutricart.ui.screens.alerts.ProfileReview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// What Home shows about the account's latest list. Nutrition score and alert count are
// added with the phases that compute them.
data class ListSummary(
    val itemCount: Int,
    val total: Int,
    val budget: Int,
    // The list's nutrition balance score, or null when it cannot be worked out
    val nutritionScore: Int? = null,
    val listId: Long = 0,
    // Items the profile review flags that the user has not chosen to keep
    val reviewCount: Int = 0
) {
    val usedFraction: Float get() = if (budget > 0) total.toFloat() / budget else 0f
    val usedPercent: Int get() = if (budget > 0) (total.toLong() * 100 / budget).toInt() else 0
}

data class HomeUiState(
    val accountName: String = "",
    val region: String? = null,
    val householdSize: Int? = null,
    // Digits only
    val budget: String = "",
    // True once the user has left the field or tried to generate
    val budgetChecked: Boolean = false,
    // The days the next list is planned for; the budget above is for that period
    val periodDays: Int = PlanningPeriod.DEFAULT_DAYS,
    // The latest list, or null while the account has none
    val currentList: ListSummary? = null,
    // Set when Generate is tapped with a valid budget; the screen opens the processing
    // screen with this amount and periodDays, and clears it
    val generateRequest: Int? = null
) {
    // The smallest budget accepted for the chosen period
    val minimumBudget: Int get() = BudgetRules.minimum(periodDays)

    val budgetError: BudgetError?
        get() = if (budgetChecked) BudgetRules.validate(budget, periodDays) else null

    // Disabled only while the field is blank; an out-of-range amount is explained on tap
    val canGenerate: Boolean get() = budget.isNotEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    accounts: AccountRepository,
    profiles: ProfileRepository,
    lists: GroceryListRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    // The typed budget is kept in saved state so it survives rotation and process death
    private val _state = MutableStateFlow(
        HomeUiState(
            budget = savedState[KEY_BUDGET] ?: "",
            periodDays = PlanningPeriod.normalize(savedState[KEY_PERIOD])
        )
    )
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(accounts.currentAccount, profiles.observe()) { account, profile -> account to profile }
                .collect { (account, profile) ->
                    _state.update {
                        it.copy(
                            accountName = account?.name.orEmpty(),
                            region = profile?.region,
                            householdSize = profile?.householdSize
                        )
                    }
                }
        }
    }

    init {
        viewModelScope.launch {
            lists.observeLatestList()
                .flatMapLatest { list ->
                    if (list == null) {
                        flowOf(null)
                    } else {
                        combine(lists.observeItems(list.id), profiles.observe()) { items, profile ->
                            ListSummary(
                                itemCount = items.size,
                                total = items.sumOf { it.item.quantity * it.item.unitPrice },
                                budget = list.budget,
                                nutritionScore = NutritionAnalyzer
                                    .analyze(items, profile?.householdSize ?: 0, list.periodDays)?.score?.value,
                                listId = list.id,
                                reviewCount = ProfileReview.analyze(items, profile, list.periodDays)?.needsReview ?: 0
                            )
                        }
                    }
                }
                .collect { summary -> _state.update { it.copy(currentList = summary) } }
        }
    }

    fun onBudgetChange(input: String) {
        val budget = BudgetRules.sanitize(input)
        savedState[KEY_BUDGET] = budget
        _state.update { it.copy(budget = budget) }
    }

    fun onBudgetFocusLost() = _state.update { it.copy(budgetChecked = it.budget.isNotEmpty()) }

    // Only the offered periods can be chosen
    fun onPeriodSelected(days: Int) {
        if (days !in PlanningPeriod.options) return
        savedState[KEY_PERIOD] = days
        _state.update { it.copy(periodDays = days) }
    }

    // A valid budget asks the screen to start generation; an invalid one shows its error
    fun onGenerate() {
        val current = _state.value
        if (!current.canGenerate) return
        _state.update {
            it.copy(
                budgetChecked = true,
                generateRequest = current.budget.toInt().takeIf { BudgetRules.isValid(current.budget, current.periodDays) }
            )
        }
    }

    fun onGenerateHandled() = _state.update { it.copy(generateRequest = null) }

    private companion object {
        const val KEY_BUDGET = "budget"
        const val KEY_PERIOD = "periodDays"
    }
}
