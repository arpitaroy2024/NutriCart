package com.example.nutricart.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nutricart.R
import com.example.nutricart.ui.screens.auth.CreateAccountScreen
import com.example.nutricart.ui.screens.auth.LoginScreen
import com.example.nutricart.ui.screens.onboarding.OnboardingScreen
import com.example.nutricart.ui.screens.placeholder.PlaceholderScreen
import com.example.nutricart.ui.screens.splash.SplashScreen

private const val FADE_MS = 200

@Composable
fun NutriCartNavHost(navController: NavHostController = rememberNavController()) {
    // Leaving login or create account after signing in removes both from the back stack
    val onAuthenticated: (EntryDestination) -> Unit = { destination ->
        navController.navigate(Routes.forEntry(destination)) {
            popUpTo(Routes.LOGIN) { inclusive = true }
        }
    }

    // Temporary, for testing the entry flow from the Phase 4 placeholders. The session is kept.
    val backToLoginForTesting: () -> Unit = {
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(FADE_MS)) },
        exitTransition = { fadeOut(tween(FADE_MS)) }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onResolved = { result ->
                    navController.navigate(Routes.forEntry(result.destination, result.storageError)) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.WELCOME,
            arguments = listOf(
                navArgument(Routes.ARG_STORAGE_ERROR) {
                    type = NavType.BoolType
                    defaultValue = false
                }
            )
        ) { entry ->
            OnboardingScreen(
                storageError = entry.arguments?.getBoolean(Routes.ARG_STORAGE_ERROR) == true,
                onFinished = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
                onRetry = {
                    navController.navigate(Routes.SPLASH) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = onAuthenticated,
                onCreateAccount = { navController.navigate(Routes.REGISTER) }
            )
        }
        composable(Routes.REGISTER) {
            CreateAccountScreen(
                onAuthenticated = onAuthenticated,
                onBackToLogin = { navController.popBackStack() }
            )
        }

        // Phase 4 destinations: placeholders so the entry flow has somewhere to land
        composable(
            route = Routes.PROFILE_SETUP,
            arguments = listOf(
                navArgument(Routes.ARG_MODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            PlaceholderScreen(
                titleRes = R.string.placeholder_profile_setup,
                onBackToLogin = backToLoginForTesting
            )
        }
        composable(Routes.HOME) {
            PlaceholderScreen(
                titleRes = R.string.placeholder_home,
                onBackToLogin = backToLoginForTesting
            )
        }
    }
}
