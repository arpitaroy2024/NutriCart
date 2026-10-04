package com.example.nutricart.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Builds a database exactly as an older version created it, fills it, then opens it with
// the current code to check that the upgrade keeps every account and profile.
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    private val profilesVersion1 =
        "CREATE TABLE `profiles` (`accountId` INTEGER NOT NULL, `region` TEXT NOT NULL, `householdSize` INTEGER NOT NULL, `allergies` TEXT NOT NULL, `condition` TEXT NOT NULL, PRIMARY KEY(`accountId`), FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    private val profilesVersion2 =
        "CREATE TABLE `profiles` (`accountId` INTEGER NOT NULL, `region` TEXT NOT NULL, `householdSize` INTEGER NOT NULL, `allergies` TEXT NOT NULL, `customAllergy` TEXT, `conditions` TEXT NOT NULL, `customCondition` TEXT, PRIMARY KEY(`accountId`), FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

    // The schema Room generated for the tables that have not changed since version 1
    private val otherTables = listOf(
        "CREATE TABLE `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `email` TEXT NOT NULL, `passwordHash` TEXT NOT NULL, `passwordSalt` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE UNIQUE INDEX `index_accounts_email` ON `accounts` (`email`)",
        "CREATE TABLE `grocery_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `budget` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX `index_grocery_lists_accountId` ON `grocery_lists` (`accountId`)",
        "CREATE TABLE `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `accountId` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `month` TEXT NOT NULL, `listId` INTEGER, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`listId`) REFERENCES `grocery_lists`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
        "CREATE INDEX `index_budgets_accountId` ON `budgets` (`accountId`)",
        "CREATE INDEX `index_budgets_listId` ON `budgets` (`listId`)",
        "CREATE TABLE `catalog_items` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, `unit` TEXT NOT NULL, `gramsPerUnit` INTEGER NOT NULL, `nutrientTag` TEXT NOT NULL, `caloriesPer100g` REAL NOT NULL, `proteinPer100g` REAL NOT NULL, `carbsPer100g` REAL NOT NULL, `fatPer100g` REAL NOT NULL, `ironMgPer100g` REAL NOT NULL, `allergens` TEXT NOT NULL, `flaggedConditions` TEXT NOT NULL, PRIMARY KEY(`id`))",
        "CREATE TABLE `list_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER NOT NULL, `catalogItemId` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `unitPrice` INTEGER NOT NULL, `bought` INTEGER NOT NULL, `alertOverridden` INTEGER NOT NULL, FOREIGN KEY(`listId`) REFERENCES `grocery_lists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`catalogItemId`) REFERENCES `catalog_items`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )",
        "CREATE INDEX `index_list_items_listId` ON `list_items` (`listId`)",
        "CREATE INDEX `index_list_items_catalogItemId` ON `list_items` (`catalogItemId`)",
        "CREATE TABLE `region_prices` (`catalogItemId` INTEGER NOT NULL, `region` TEXT NOT NULL, `price` INTEGER NOT NULL, `updatedAt` INTEGER, PRIMARY KEY(`catalogItemId`, `region`), FOREIGN KEY(`catalogItemId`) REFERENCES `catalog_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
    )

    private fun createDatabase(version: Int, fill: SQLiteDatabase.() -> Unit) {
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            otherTables.forEach(db::execSQL)
            db.execSQL(if (version == 1) profilesVersion1 else profilesVersion2)
            db.fill()
            db.version = version
        }
    }

    private fun SQLiteDatabase.account(id: Long, email: String) = execSQL(
        "INSERT INTO accounts (id, name, email, passwordHash, passwordSalt, createdAt) VALUES (?, ?, ?, ?, ?, ?)",
        arrayOf<Any>(id, "User $id", email, "hash$id", "salt$id", 1_700_000_000_000 + id)
    )

    private fun SQLiteDatabase.profileV1(accountId: Long, region: String, size: Int, allergies: String, condition: String) =
        execSQL(
            "INSERT INTO profiles (accountId, region, householdSize, allergies, `condition`) VALUES (?, ?, ?, ?, ?)",
            arrayOf<Any>(accountId, region, size, allergies, condition)
        )

    private fun SQLiteDatabase.profileV2(
        accountId: Long,
        region: String,
        size: Int,
        allergies: String,
        customAllergy: String?,
        conditions: String,
        customCondition: String?
    ) = execSQL(
        "INSERT INTO profiles (accountId, region, householdSize, allergies, customAllergy, conditions, customCondition) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)",
        arrayOf<Any?>(accountId, region, size, allergies, customAllergy, conditions, customCondition)
    )

    private fun openCurrent(): NutriCartDatabase =
        Room.databaseBuilder(context, NutriCartDatabase::class.java, name)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()

    private fun <T> withCurrent(block: suspend (NutriCartDatabase) -> T): T {
        val db = openCurrent()
        try {
            return runBlocking { block(db) }
        } finally {
            db.close()
        }
    }

    // Version 1 -> current

    @Test
    fun fromVersion1_keepsAccountsAndCarriesProfilesOver() {
        createDatabase(1) {
            account(1, "first@example.com")
            account(2, "second@example.com")
            account(3, "third@example.com")
            profileV1(1, "Rangpur Division", 4, "Peanuts,Shellfish", "Diabetes")
            profileV1(2, "Dhaka Division", 12, "", "None")
            profileV1(3, "Sylhet Division", 1, "Gluten", "Hypertension")
        }

        withCurrent { db ->
            // Accounts and their password material are untouched
            val first = db.accountDao().findByEmail("first@example.com")!!
            assertEquals(1L, first.id)
            assertEquals("hash1", first.passwordHash)
            assertEquals("salt1", first.passwordSalt)
            assertNotNull(db.accountDao().findByEmail("second@example.com"))
            assertNotNull(db.accountDao().findByEmail("third@example.com"))

            // The single condition becomes a one-element set
            val one = db.profileDao().get(1)!!
            assertEquals("Rangpur Division", one.region)
            assertEquals(4, one.householdSize)
            assertEquals(setOf(Allergen.Peanuts, Allergen.Shellfish), one.allergies)
            assertEquals(setOf(HealthCondition.Diabetes), one.conditions)
            assertTrue(one.customAllergies.isEmpty())
            assertTrue(one.customConditions.isEmpty())

            // "None" becomes the empty set
            val two = db.profileDao().get(2)!!
            assertEquals(12, two.householdSize)
            assertTrue(two.allergies.isEmpty())
            assertTrue(two.conditions.isEmpty())

            val three = db.profileDao().get(3)!!
            assertEquals(setOf(Allergen.Gluten), three.allergies)
            assertEquals(setOf(HealthCondition.Hypertension), three.conditions)
        }
    }

    @Test
    fun fromVersion1_anEmptyDatabaseUpgrades() {
        createDatabase(1) { }

        withCurrent { db ->
            assertNull(db.accountDao().findByEmail("nobody@example.com"))
            assertNull(db.profileDao().get(1))
        }
    }

    // Version 2 -> current

    @Test
    fun fromVersion2_aSingleCustomConditionBecomesAOneItemList() {
        createDatabase(2) {
            account(1, "first@example.com")
            profileV2(1, "Rangpur Division", 4, "Fish", null, "Diabetes,Pcos", "Gout")
        }

        withCurrent { db ->
            val profile = db.profileDao().get(1)!!
            assertEquals(listOf("Gout"), profile.customConditions)
            assertTrue(profile.customAllergies.isEmpty())
        }
    }

    @Test
    fun fromVersion2_aSingleCustomAllergyBecomesAOneItemList() {
        createDatabase(2) {
            account(1, "first@example.com")
            profileV2(1, "Rangpur Division", 4, "Fish", "Mustard", "", null)
        }

        withCurrent { db ->
            val profile = db.profileDao().get(1)!!
            assertEquals(listOf("Mustard"), profile.customAllergies)
            assertTrue(profile.customConditions.isEmpty())
        }
    }

    @Test
    fun fromVersion2_missingOrBlankCustomValuesBecomeEmptyLists() {
        createDatabase(2) {
            account(1, "first@example.com")
            account(2, "second@example.com")
            profileV2(1, "Rangpur Division", 4, "", null, "", null)
            profileV2(2, "Dhaka Division", 2, "", "", "Anemia", "   ")
        }

        withCurrent { db ->
            listOf(1L, 2L).forEach { id ->
                val profile = db.profileDao().get(id)!!
                assertTrue("allergies of $id", profile.customAllergies.isEmpty())
                assertTrue("conditions of $id", profile.customConditions.isEmpty())
            }
        }
    }

    @Test
    fun fromVersion2_customTextWithQuotesAndCommasIsCarriedOverIntact() {
        val allergy = "Nuts, \"raw\" seeds"
        val condition = "Back\\slash & it's [odd]"
        createDatabase(2) {
            account(1, "first@example.com")
            profileV2(1, "Rangpur Division", 4, "", "  $allergy  ", "", condition)
        }

        withCurrent { db ->
            val profile = db.profileDao().get(1)!!
            // One item each, trimmed, not split on the comma
            assertEquals(listOf(allergy), profile.customAllergies)
            assertEquals(listOf(condition), profile.customConditions)
        }
    }

    @Test
    fun fromVersion2_everythingElseInTheProfileIsPreserved() {
        createDatabase(2) {
            account(1, "first@example.com")
            account(2, "second@example.com")
            profileV2(1, "Sylhet Division", 20, "Dairy,Fish,TreeNuts", "Mustard", "Diabetes,Hypertension,Celiac", "Gout")
            profileV2(2, "Khulna Division", 1, "", null, "", null)
        }

        withCurrent { db ->
            // Accounts are untouched
            val account = db.accountDao().findByEmail("first@example.com")!!
            assertEquals("hash1", account.passwordHash)
            assertEquals("salt1", account.passwordSalt)

            val one = db.profileDao().get(1)!!
            assertEquals(1L, one.accountId)
            assertEquals("Sylhet Division", one.region)
            assertEquals(20, one.householdSize)
            assertEquals(setOf(Allergen.Dairy, Allergen.Fish, Allergen.TreeNuts), one.allergies)
            assertEquals(
                setOf(HealthCondition.Diabetes, HealthCondition.Hypertension, HealthCondition.Celiac),
                one.conditions
            )
            assertEquals(listOf("Mustard"), one.customAllergies)
            assertEquals(listOf("Gout"), one.customConditions)

            // Each profile still belongs to its own account
            val two = db.profileDao().get(2)!!
            assertEquals(2L, two.accountId)
            assertEquals("Khulna Division", two.region)
            assertEquals(1, two.householdSize)
            assertTrue(two.allergies.isEmpty() && two.conditions.isEmpty())
            assertNull(db.profileDao().get(3))
        }
    }

    @Test
    fun upgradedDatabase_storesSeveralCustomValuesAndKeepsTheAccountLink() {
        createDatabase(2) {
            account(1, "first@example.com")
            profileV2(1, "Rangpur Division", 4, "Peanuts", "Mustard", "Diabetes", null)
        }

        withCurrent { db ->
            db.profileDao().upsert(
                ProfileEntity(
                    accountId = 1,
                    region = "Rangpur Division",
                    householdSize = 20,
                    allergies = setOf(Allergen.Peanuts, Allergen.Fish, Allergen.Sesame),
                    customAllergies = listOf("Mustard", "Mango", "Avocado"),
                    conditions = setOf(HealthCondition.Diabetes, HealthCondition.HighCholesterol),
                    customConditions = listOf("Migraine", "Endometriosis")
                )
            )

            val saved = db.profileDao().get(1)!!
            assertEquals(20, saved.householdSize)
            assertEquals(setOf(Allergen.Peanuts, Allergen.Fish, Allergen.Sesame), saved.allergies)
            assertEquals(listOf("Mustard", "Mango", "Avocado"), saved.customAllergies)
            assertEquals(setOf(HealthCondition.Diabetes, HealthCondition.HighCholesterol), saved.conditions)
            assertEquals(listOf("Migraine", "Endometriosis"), saved.customConditions)
            // The profile still belongs to its account: deleting the account removes it
            db.openHelper.writableDatabase.execSQL("DELETE FROM accounts WHERE id = 1")
            assertNull(db.profileDao().get(1))
        }
    }

    @Test
    fun fromVersion2_anEmptyDatabaseUpgrades() {
        createDatabase(2) { }

        withCurrent { db ->
            assertNull(db.profileDao().get(1))
        }
    }
}
