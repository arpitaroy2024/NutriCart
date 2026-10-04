package com.example.nutricart.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.nutricart.data.local.NutriCartDatabase
import com.example.nutricart.data.repository.LocalAccountRepository
import com.example.nutricart.data.repository.LocalBudgetRepository
import com.example.nutricart.data.repository.LocalCatalogRepository
import com.example.nutricart.data.repository.LocalGroceryListRepository
import com.example.nutricart.data.repository.LocalProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File

// The real repositories over an in-memory Room database and a DataStore file in a temp folder
class TestEnvironment(settingsFile: File, now: () -> Long = { 1_700_000_000_000 }) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val database: NutriCartDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        NutriCartDatabase::class.java
    ).allowMainThreadQueries().build()

    val settings = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { settingsFile })

    val accounts = LocalAccountRepository(database.accountDao(), settings, now = now)
    val profiles = LocalProfileRepository(database.profileDao(), settings)
    val budgets = LocalBudgetRepository(database.budgetDao(), settings)
    val lists = LocalGroceryListRepository(database.groceryListDao(), database.itemDao(), settings, now)
    val catalog = LocalCatalogRepository(database.catalogDao())

    fun close() {
        database.close()
        scope.cancel()
    }
}
