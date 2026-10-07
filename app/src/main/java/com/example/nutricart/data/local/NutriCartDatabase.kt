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
    version = 5,
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

// Version 4: a grocery list records the days it was planned for. Every list made before
// this was a month's list, so existing rows become 30. Nothing else is touched.
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `grocery_lists` ADD COLUMN `periodDays` INTEGER NOT NULL DEFAULT 30")
    }
}

// Version 5: a catalog row says how big its pack is and whether it is still offered. Every
// existing row keeps its id, unit, weight and price: a "kg" row is a 1000 g pack, an "L" row
// a 1000 ml pack and a "pcs" row one piece, and all of them start as offered. No row is
// added or removed here; the catalog repository adds the smaller packs from the seed.
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `packAmount` INTEGER NOT NULL DEFAULT 1000")
        db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `packMeasure` TEXT NOT NULL DEFAULT 'Gram'")
        db.execSQL("ALTER TABLE `catalog_items` ADD COLUMN `offered` INTEGER NOT NULL DEFAULT 1")
        db.execSQL("UPDATE `catalog_items` SET `packMeasure` = 'Millilitre' WHERE `unit` = 'L'")
        db.execSQL("UPDATE `catalog_items` SET `packAmount` = 1, `packMeasure` = 'Piece' WHERE `unit` = 'pcs'")
    }
}

// Every step from the first released schema to the current one
val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
