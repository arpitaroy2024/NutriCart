package com.example.nutricart.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// App-level settings that survive restarts
class SettingsStore(private val dataStore: DataStore<Preferences>) {

    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    // The session: null when nobody is logged in
    val loggedInAccountId: Flow<Long?> =
        dataStore.data.map { it[LOGGED_IN_ACCOUNT_ID] }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setLoggedInAccountId(accountId: Long?) {
        dataStore.edit {
            if (accountId == null) it.remove(LOGGED_IN_ACCOUNT_ID) else it[LOGGED_IN_ACCOUNT_ID] = accountId
        }
    }

    companion object {
        const val FILE_NAME = "settings"
        private val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        private val LOGGED_IN_ACCOUNT_ID = longPreferencesKey("logged_in_account_id")
    }
}
