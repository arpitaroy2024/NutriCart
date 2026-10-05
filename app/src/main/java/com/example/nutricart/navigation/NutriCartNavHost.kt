package com.example.nutricart.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.nutricart.R
import com.example.nutricart.ui.components.BottomNavTab
import com.example.nutricart.ui.components.NutriBottomNav
import com.example.nutricart.ui.screens.alerts.AlertsScreen
import com.example.nutricart.ui.screens.auth.CreateAccountScreen
import com.example.nutricart.ui.screens.auth.LoginScreen
import com.example.nutricart.ui.screens.editlist.AddItemsScreen
import com.example.nutricart.ui.screens.editlist.EditListScreen
import com.example.nutricart.ui.screens.generate.GenerateScreen
import com.example.nutricart.ui.screens.grocerylist.GroceryListScreen
import com.example.nutricart.ui.screens.home.HomeScreen
import com.example.nutricart.ui.screens.nutrition.NutritionScreen
import com.example.nutricart.ui.screens.onboarding.OnboardingScreen
import com.example.nutricart.ui.screens.profile.ProfileScreen
import com.example.nutricart.ui.screens.profilesetup.ProfileSetupScreen
import com.example.nutricart.ui.screens.splash.SplashScreen
import com.example.nutricart.ui.theme.NutriCartTheme

private const val FADE_MS = 200

@Composable
fun NutriCartNavHost(navController: NavHostController = rememberNavController()) {
    val activity = LocalActivity.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTab = ShellTabs.tabFor(backStackEntry?.destination?.route)

    // Leaving login or create account after signing in removes both from the back stack
    val onAuthenticated: (EntryDestination) -> Unit = { destination ->
        navController.navigate(Routes.forEntry(destination)) {
            popUpTo(Routes.LOGIN) { inclusive = true }
        }
    }

    // After logging out nothing signed-in is left underneath Login
    val onLoggedOut: () -> Unit = {
        navController.navigate(Routes.LOGIN) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    // Tabs are siblings: they never stack on each other, each keeps its own state,
    // and back from any of them returns to Home
    val openTab: (BottomNavTab) -> Unit = { tab ->
        navController.navigate(ShellTabs.routeFor(tab)) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val openEditor: (Long) -> Unit = { listId -> navController.navigate(Routes.listEdit(listId)) }
    val openNutrition: (Long) -> Unit = { listId -> navController.navigate(Routes.nutritionDetail(listId)) }
    val openReview: (Long) -> Unit = { listId -> navController.navigate(Routes.alerts(listId)) }

    // The bottom bar lives outside the NavHost so it stays put while tabs change
    Scaffold(
        containerColor = NutriCartTheme.colors.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (currentTab != null) {
                NutriBottomNav(selected = currentTab, onSelect = openTab)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
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

            composable(
                route = Routes.PROFILE_SETUP,
                arguments = listOf(
                    navArgument(Routes.ARG_MODE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { entry ->
                val isEdit = entry.arguments?.getString(Routes.ARG_MODE) == Routes.MODE_EDIT
                ProfileSetupScreen(
                    isEdit = isEdit,
                    onSaved = {
                        if (isEdit) {
                            navController.popBackStack()
                        } else {
                            // Setup is cleared, so back on Home exits the app
                            navController.navigate(Routes.HOME) {
                                popUpTo(navController.graph.id) { inclusive = true }
                            }
                        }
                    },
                    onClose = {
                        // First-time setup has nothing behind it, so leaving it leaves the app
                        if (isEdit) navController.popBackStack() else activity?.finish()
                    },
                    onLoggedOut = onLoggedOut
                )
            }

            // Bottom-navigation roots
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenProfile = { openTab(BottomNavTab.Profile) },
                    onGenerate = { budget -> navController.navigate(Routes.generate(budget)) },
                    onOpenList = { openTab(BottomNavTab.GroceryList) },
                    onOpenReview = openReview
                )
            }
            // The List tab: the account's latest list
            composable(Routes.LIST) {
                GroceryListScreen(
                    onBack = null,
                    onEditList = openEditor,
                    onOpenNutrition = openNutrition,
                    onOpenReview = openReview
                )
            }

            // Generation is a destination of its own, so it survives rotation. It is removed
            // when the list opens, which makes back from the new list return to Home.
            composable(
                route = Routes.GENERATE,
                arguments = listOf(
                    navArgument(Routes.ARG_BUDGET) {
                        type = NavType.IntType
                        defaultValue = 0
                    }
                )
            ) {
                GenerateScreen(
                    onListReady = { listId ->
                        navController.navigate(Routes.listDetail(listId)) {
                            popUpTo(Routes.GENERATE) { inclusive = true }
                        }
                    },
                    onClosed = { navController.popBackStack() }
                )
            }
            composable(
                route = Routes.LIST_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType })
            ) {
                GroceryListScreen(
                    onBack = { navController.popBackStack() },
                    onEditList = openEditor,
                    onOpenNutrition = openNutrition,
                    onOpenReview = openReview
                )
            }

            // The editor and its item picker always work on the list named in the route
            composable(
                route = Routes.LIST_EDIT,
                arguments = listOf(navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType })
            ) {
                EditListScreen(
                    onBack = { navController.popBackStack() },
                    onAddItems = { listId -> navController.navigate(Routes.listAdd(listId)) }
                )
            }
            composable(
                route = Routes.LIST_ADD,
                arguments = listOf(navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType })
            ) {
                AddItemsScreen(onBack = { navController.popBackStack() })
            }
            // The Nutrition tab: analysis of the account's latest list
            composable(Routes.NUTRITION) {
                NutritionScreen(
                    onBack = null,
                    onEditList = openEditor,
                    onOpenReview = openReview,
                    onGoHome = { openTab(BottomNavTab.Home) }
                )
            }
            // The same screen for one named list, opened from that list
            composable(
                route = Routes.NUTRITION_DETAIL,
                arguments = listOf(navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType })
            ) {
                NutritionScreen(
                    onBack = { navController.popBackStack() },
                    onEditList = openEditor,
                    onOpenReview = openReview,
                    onGoHome = null
                )
            }
            // The profile review of one list: allergen matches and nutrition considerations
            composable(
                route = Routes.ALERTS,
                arguments = listOf(navArgument(Routes.ARG_LIST_ID) { type = NavType.LongType })
            ) {
                AlertsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onEditProfile = { navController.navigate(Routes.profileSetup(edit = true)) },
                    onLoggedOut = onLoggedOut
                )
            }
        }
    }
}
