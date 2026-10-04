package com.example.nutricart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray

@Database(
    entities = [
        AccountEntity::class,
        ProfileEntity::class,
        BudgetEntity::class,
        GroceryListEntity::class,
        ListItemEntity::class,
        CatalogItemEntity::class,
        RegionPriceEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class NutriCartDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun profileDao(): ProfileDao
    abstract fun budgetDao(): BudgetDao
    abstract fun groceryListDao(): GroceryListDao
    abstract fun itemDao(): ItemDao
    abstract fun catalogDao(): CatalogDao

    companion object {
        fun create(context: Context): NutriCartDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                NutriCartDatabase::class.java,
                "nutricart.db"
            ).addMigrations(*ALL_MIGRATIONS).build()
    }
}

// Version 2: a profile holds any number of health conditions instead of exactly one, and a
// free-text entry beside the allergy and condition sets. The old single value is carried
// over ("None" becomes the empty set). The table is rebuilt because SQLite on older
// Android versions cannot rename or drop a column.
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `profiles_new` (" +
                "`accountId` INTEGER NOT NULL, " +
                "`region` TEXT NOT NULL, " +
                "`householdSize` INTEGER NOT NULL, " +
                "`allergies` TEXT NOT NULL, " +
                "`customAllergy` TEXT, " +
                "`conditions` TEXT NOT NULL, " +
                "`customCondition` TEXT, " +
                "PRIMARY KEY(`accountId`), " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO `profiles_new` " +
                "(`accountId`, `region`, `householdSize`, `allergies`, `customAllergy`, `conditions`, `customCondition`) " +
                "SELECT `accountId`, `region`, `householdSize`, `allergies`, NULL, " +
                "CASE WHEN `condition` = 'None' THEN '' ELSE `condition` END, NULL " +
                "FROM `profiles`"
        )
        db.execSQL("DROP TABLE `profiles`")
        db.execSQL("ALTER TABLE `profiles_new` RENAME TO `profiles`")
    }
}

// Version 3: a profile holds any number of typed allergies and conditions instead of one of
// each. An existing single value becomes a one-item list; a missing or blank one becomes an
// empty list. Rows are copied in code so the text is encoded as JSON correctly whatever it contains.
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE `profiles_new` (" +
                "`accountId` INTEGER NOT NULL, " +
                "`region` TEXT NOT NULL, " +
                "`householdSize` INTEGER NOT NULL, " +
                "`allergies` TEXT NOT NULL, " +
                "`customAllergies` TEXT NOT NULL, " +
                "`conditions` TEXT NOT NULL, " +
                "`customConditions` TEXT NOT NULL, " +
                "PRIMARY KEY(`accountId`), " +
                "FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.query(
            "SELECT `accountId`, `region`, `householdSize`, `allergies`, `customAllergy`, " +
                "`conditions`, `customCondition` FROM `profiles`"
        ).use { rows ->
            while (rows.moveToNext()) {
                db.execSQL(
                    "INSERT INTO `profiles_new` " +
                        "(`accountId`, `region`, `householdSize`, `allergies`, `customAllergies`, " +
                        "`conditions`, `customConditions`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(
                        rows.getLong(0),
                        rows.getString(1),
                        rows.getInt(2),
                        rows.getString(3),
                        singleValueAsJsonList(if (rows.isNull(4)) null else rows.getString(4)),
                        rows.getString(5),
                        singleValueAsJsonList(if (rows.isNull(6)) null else rows.getString(6))
                    )
                )
            }
        }
        db.execSQL("DROP TABLE `profiles`")
        db.execSQL("ALTER TABLE `profiles_new` RENAME TO `profiles`")
    }
}

private fun singleValueAsJsonList(value: String?): String =
    JSONArray(listOfNotNull(value?.trim()?.takeIf { it.isNotEmpty() })).toString()

// Every step from the first released schema to the current one
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
