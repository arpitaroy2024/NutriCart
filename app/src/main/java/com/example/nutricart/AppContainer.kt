package com.example.nutricart

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.nutricart.data.SettingsStore
import com.example.nutricart.data.local.NutriCartDatabase
import com.example.nutricart.data.repository.AccountRepository
import com.example.nutricart.data.repository.BudgetRepository
import com.example.nutricart.data.repository.CatalogRepository
import com.example.nutricart.data.repository.GroceryListRepository
import com.example.nutricart.data.repository.LocalAccountRepository
import com.example.nutricart.data.repository.LocalBudgetRepository
import com.example.nutricart.data.repository.LocalCatalogRepository
import com.example.nutricart.data.repository.LocalGroceryListRepository
import com.example.nutricart.data.repository.LocalProfileRepository
import com.example.nutricart.data.repository.ProfileRepository
import com.example.nutricart.navigation.EntryRouter

// App-wide dependencies, created once on first use. Everything is on-device.
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    private val database: NutriCartDatabase by lazy { NutriCartDatabase.create(appContext) }

    val settings: SettingsStore by lazy {
        SettingsStore(
            PreferenceDataStoreFactory.create {
                appContext.preferencesDataStoreFile(SettingsStore.FILE_NAME)
            }
        )
    }

    val accountRepository: AccountRepository by lazy {
        LocalAccountRepository(database.accountDao(), settings)
    }

    val profileRepository: ProfileRepository by lazy {
        LocalProfileRepository(database.profileDao(), settings)
    }

    val budgetRepository: BudgetRepository by lazy {
        LocalBudgetRepository(database.budgetDao(), settings)
    }

    val groceryListRepository: GroceryListRepository by lazy {
        LocalGroceryListRepository(database.groceryListDao(), database.itemDao(), settings)
    }

    val catalogRepository: CatalogRepository by lazy {
        LocalCatalogRepository(database.catalogDao())
    }

    val entryRouter: EntryRouter by lazy {
        EntryRouter(settings, accountRepository, profileRepository)
    }
}
