package com.example.nutricart.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.domain.BudgetError
import com.example.nutricart.domain.BudgetRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val accountName: String = "",
    val region: String? = null,
    val householdSize: Int? = null,
    // Digits only
    val budget: String = "",
    // True once the user has left the field or tried to generate
    val budgetChecked: Boolean = false,
    // Raised when Generate is tapped with a valid budget; the screen shows a notice and clears it
    val generationUnavailableNotice: Boolean = false
) {
    val budgetError: BudgetError?
        get() = if (budgetChecked) BudgetRules.validate(budget) else null

    // Disabled only while the field is blank; an out-of-range amount is explained on tap
    val canGenerate: Boolean get() = budget.isNotEmpty()
}

class HomeViewModel(
    accounts: AccountRepository,
    profiles: ProfileRepository,
    private val savedState: SavedStateHandle
) : ViewModel() {

    // The typed budget is kept in saved state so it survives rotation and process death
    private val _state = MutableStateFlow(HomeUiState(budget = savedState[KEY_BUDGET] ?: ""))
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

    fun onBudgetChange(input: String) {
        val budget = BudgetRules.sanitize(input)
        savedState[KEY_BUDGET] = budget
        _state.update { it.copy(budget = budget) }
    }

    fun onBudgetFocusLost() = _state.update { it.copy(budgetChecked = it.budget.isNotEmpty()) }

    // Grocery generation is built in Phase 5. Until then a valid budget only raises a notice.
    fun onGenerate() {
        val current = _state.value
        if (!current.canGenerate) return
        _state.update {
            it.copy(
                budgetChecked = true,
                generationUnavailableNotice = BudgetRules.isValid(current.budget)
            )
        }
    }

    fun onNoticeShown() = _state.update { it.copy(generationUnavailableNotice = false) }

    private companion object {
        const val KEY_BUDGET = "budget"
    }
}
