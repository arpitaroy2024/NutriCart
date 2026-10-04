package com.example.nutricart.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.data.repository.Account
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Account holds no password material, so nothing sensitive can reach the screen
data class ProfileUiState(
    val account: Account? = null,
    val profile: ProfileEntity? = null,
    val loggedOut: Boolean = false
)

class ProfileViewModel(
    private val accounts: AccountRepository,
    profiles: ProfileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        // Both are observed, so an edited profile shows up without a refresh
        viewModelScope.launch {
            combine(accounts.currentAccount, profiles.observe()) { account, profile -> account to profile }
                .collect { (account, profile) ->
                    _state.update { it.copy(account = account, profile = profile) }
                }
        }
    }

    // Ends the session only. The account, profile and grocery history stay on the device.
    fun logout() {
        viewModelScope.launch {
            accounts.logout()
            _state.update { it.copy(loggedOut = true) }
        }
    }
}
