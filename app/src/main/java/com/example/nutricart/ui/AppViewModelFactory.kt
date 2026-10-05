package com.example.nutricart.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.nutricart.AppContainer
import com.example.nutricart.NutriCartApp
import com.example.nutricart.ui.screens.alerts.AlertsViewModel
import com.example.nutricart.ui.screens.auth.CreateAccountViewModel
import com.example.nutricart.ui.screens.auth.LoginViewModel
import com.example.nutricart.ui.screens.editlist.AddItemsViewModel
import com.example.nutricart.ui.screens.editlist.EditListViewModel
import com.example.nutricart.ui.screens.generate.GenerateViewModel
import com.example.nutricart.ui.screens.grocerylist.GroceryListViewModel
import com.example.nutricart.ui.screens.home.HomeViewModel
import com.example.nutricart.ui.screens.nutrition.NutritionViewModel
import com.example.nutricart.ui.screens.onboarding.OnboardingViewModel
import com.example.nutricart.ui.screens.profile.ProfileViewModel
import com.example.nutricart.ui.screens.profilesetup.ProfileSetupViewModel
import com.example.nutricart.ui.screens.splash.SplashViewModel

private val CreationExtras.container: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as NutriCartApp).container

// Builds every ViewModel from the app container. Use as viewModel(factory = AppViewModelFactory).
val AppViewModelFactory = viewModelFactory {
    initializer { SplashViewModel(container.entryRouter) }
    initializer { OnboardingViewModel(container.settings) }
    initializer { LoginViewModel(container.accountRepository, container.entryRouter) }
    initializer { CreateAccountViewModel(container.accountRepository, container.entryRouter) }
    initializer { ProfileSetupViewModel(container.profileRepository, container.accountRepository) }
    initializer {
        HomeViewModel(
            container.accountRepository,
            container.profileRepository,
            container.groceryListRepository,
            createSavedStateHandle()
        )
    }
    initializer {
        GenerateViewModel(
            createSavedStateHandle(),
            container.profileRepository,
            container.catalogRepository,
            container.groceryListRepository
        )
    }
    initializer {
        GroceryListViewModel(createSavedStateHandle(), container.groceryListRepository, container.profileRepository)
    }
    initializer {
        AlertsViewModel(
            createSavedStateHandle(),
            container.groceryListRepository,
            container.profileRepository,
            container.catalogRepository
        )
    }
    initializer { EditListViewModel(createSavedStateHandle(), container.groceryListRepository) }
    initializer {
        NutritionViewModel(createSavedStateHandle(), container.groceryListRepository, container.profileRepository)
    }
    initializer {
        AddItemsViewModel(
            createSavedStateHandle(),
            container.groceryListRepository,
            container.catalogRepository,
            container.profileRepository
        )
    }
    initializer { ProfileViewModel(container.accountRepository, container.profileRepository) }
}
