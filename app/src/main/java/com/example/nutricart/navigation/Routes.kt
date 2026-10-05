package com.example.nutricart.navigation

// Route strings from the UI Flow document. LOGIN and REGISTER are not in the PDFs.
object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome?storageError={storageError}"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PROFILE_SETUP = "profile/setup?mode={mode}"
    const val HOME = "home"
    const val GENERATE = "generate?budget={amount}"
    const val LIST = "list"
    const val LIST_DETAIL = "list/{listId}"
    const val LIST_EDIT = "list/{listId}/edit"
    const val LIST_ADD = "list/{listId}/add"
    const val ITEM = "item/{itemId}"
    const val NUTRITION = "nutrition"
    const val NUTRITION_DETAIL = "nutrition/{listId}"
    const val ALERTS = "alerts/{listId}"
    const val ALERT_DETAIL = "alerts/{listId}/item/{itemId}"
    const val PROFILE = "profile"

    const val ARG_STORAGE_ERROR = "storageError"
    const val ARG_MODE = "mode"
    const val ARG_BUDGET = "amount"
    const val ARG_LIST_ID = "listId"
    const val MODE_EDIT = "edit"

    // storageError asks the welcome screen to offer a retry after a failed read on the splash
    fun welcome(storageError: Boolean = false) = "welcome?storageError=$storageError"

    fun profileSetup(edit: Boolean = false) = if (edit) "profile/setup?mode=$MODE_EDIT" else "profile/setup"

    fun generate(budget: Int) = "generate?budget=$budget"

    fun listDetail(listId: Long) = "list/$listId"

    fun listEdit(listId: Long) = "list/$listId/edit"

    fun listAdd(listId: Long) = "list/$listId/add"

    fun nutritionDetail(listId: Long) = "nutrition/$listId"

    fun alerts(listId: Long) = "alerts/$listId"

    fun forEntry(destination: EntryDestination, storageError: Boolean = false) = when (destination) {
        EntryDestination.Welcome -> welcome(storageError)
        EntryDestination.Login -> LOGIN
        EntryDestination.ProfileSetup -> profileSetup()
        EntryDestination.Home -> HOME
    }
}
