package com.example.nutricart.navigation

import com.example.nutricart.ui.components.BottomNavTab

// The four bottom-navigation roots. The bar is shown only on these routes.
object ShellTabs {
    private val routes = mapOf(
        BottomNavTab.Home to Routes.HOME,
        BottomNavTab.GroceryList to Routes.LIST,
        BottomNavTab.Nutrition to Routes.NUTRITION,
        BottomNavTab.Profile to Routes.PROFILE
    )

    fun routeFor(tab: BottomNavTab): String = routes.getValue(tab)

    // Null for any route that is not a tab root, which hides the bar
    fun tabFor(route: String?): BottomNavTab? = routes.entries.firstOrNull { it.value == route }?.key
}
