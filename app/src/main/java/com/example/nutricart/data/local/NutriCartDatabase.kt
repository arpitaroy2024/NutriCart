package com.example.nutricart.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 1,
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
            ).build()
    }
}
