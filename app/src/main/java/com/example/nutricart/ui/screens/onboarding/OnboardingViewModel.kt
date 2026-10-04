package com.example.nutricart.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.data.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class OnboardingViewModel(private val settings: SettingsStore) : ViewModel() {

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    // Called for both Skip and Get started on the last pane, so onboarding is not shown again
    fun complete() {
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
            _finished.value = true
        }
    }
}
