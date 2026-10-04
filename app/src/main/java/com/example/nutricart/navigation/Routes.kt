package com.example.nutricart.navigation

// Route strings from the UI Flow document. LOGIN and REGISTER are not in the PDFs.
object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PROFILE_SETUP = "profile/setup?mode={mode}"
    const val HOME = "home"
    const val GENERATE = "generate?budget={amount}"
    const val LIST = "list"
    const val LIST_DETAIL = "list/{listId}"
    const val LIST_EDIT = "list/{listId}/edit"
    const val ITEM = "item/{itemId}"
    const val NUTRITION = "nutrition"
    const val NUTRITION_DETAIL = "nutrition/{listId}"
    const val ALERTS = "alerts/{listId}"
    const val ALERT_DETAIL = "alerts/{listId}/item/{itemId}"
    const val PROFILE = "profile"
}
