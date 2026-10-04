package com.example.nutricart.ui.screens.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nutricart.navigation.EntryDestination
import com.example.nutricart.navigation.EntryRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

// storageError is set when local storage could not be read in time; onboarding
// then opens and offers a retry, so the splash never blocks.
data class SplashResult(val destination: EntryDestination, val storageError: Boolean = false)

class SplashViewModel(private val router: EntryRouter) : ViewModel() {

    private val _result = MutableStateFlow<SplashResult?>(null)
    val result: StateFlow<SplashResult?> = _result.asStateFlow()

    init {
        viewModelScope.launch {
            _result.value = try {
                SplashResult(withTimeout(MAX_MS) { router.startDestination() })
            } catch (e: Exception) {
                if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                SplashResult(EntryDestination.Welcome, storageError = true)
            }
        }
    }

    companion object {
        const val MIN_MS = 600L
        const val MAX_MS = 3000L
    }
}
