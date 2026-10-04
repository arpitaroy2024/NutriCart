package com.example.nutricart.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.nutricart.ui.screens.auth.LoginScreen
import com.example.nutricart.ui.screens.splash.SplashScreen
import kotlinx.coroutines.delay

@Composable
fun NutriCartNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen()
            LaunchedEffect(Unit) {
                delay(2000)
                navController.navigate(Routes.LOGIN) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
        composable(Routes.LOGIN) {
            LoginScreen()
        }
    }
}
