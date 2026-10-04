package com.example.nutricart.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nutricart.AppContainer
import com.example.nutricart.NutriCartApp
import com.example.nutricart.ui.screens.auth.CreateAccountViewModel
import com.example.nutricart.ui.screens.auth.LoginViewModel
import com.example.nutricart.ui.screens.onboarding.OnboardingViewModel
import com.example.nutricart.ui.screens.splash.SplashViewModel

private val CreationExtras.container: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriCartApp).container

// Builds every ViewModel from the app container. Use as viewModel(factory = AppViewModelFactory).
val AppViewModelFactory = viewModelFactory {
    initializer { SplashViewModel(container.entryRouter) }
    initializer { OnboardingViewModel(container.settings) }
    initializer { LoginViewModel(container.accountRepository, container.entryRouter) }
    initializer { CreateAccountViewModel(container.accountRepository, container.entryRouter) }
}
