package com.example.nutricart.navigation

import com.example.nutricart.data.SettingsStore
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.ProfileRepository
import kotlinx.coroutines.flow.first

enum class EntryDestination { Welcome, Login, ProfileSetup, Home }

// Decides where the entry flow goes from the stored onboarding flag, session and profile
class EntryRouter(
    private val settings: SettingsStore,
    private val accounts: AccountRepository,
    private val profiles: ProfileRepository
) {
    // Where the splash screen sends the user
    suspend fun startDestination(): EntryDestination {
        if (!settings.onboardingComplete.first()) return EntryDestination.Welcome
        if (settings.loggedInAccountId.first() == null) return EntryDestination.Login
        if (accounts.currentAccount.first() == null) {
            // The session names an account that no longer exists: treat as logged out
            accounts.logout()
            return EntryDestination.Login
        }
        return afterAuthentication()
    }

    // Where a logged-in user goes: profile setup until a profile exists, then Home
    suspend fun afterAuthentication(): EntryDestination =
        if (profiles.exists()) EntryDestination.Home else EntryDestination.ProfileSetup
}
