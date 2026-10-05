package com.example.nutricart.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.FoodCategory
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.data.model.NutrientTag

// Local account. Only the salted hash of the password is stored, both hex-encoded.
@Entity(tableName = "accounts", indices = [Index(value = ["email"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val passwordSalt: String,
    val createdAt: Long
)

// One profile row per account
@Entity(
    tableName = "profiles",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProfileEntity(
    @PrimaryKey val accountId: Long,
    val region: String,
    val householdSize: Int,
    val allergies: Set<Allergen>,
    // Allergies the list does not cover, as typed by the user, in the order added
    val customAllergies: List<String> = emptyList(),
    // Both empty when the user has no condition
    val conditions: Set<HealthCondition>,
    val customConditions: List<String> = emptyList()
)

@Entity(
    tableName = "grocery_lists",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("accountId")]
)
data class GroceryListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val budget: Int,
    val createdAt: Long
)

// A monthly budget entered on Home, linked to the list it produced. Amounts are in Tk.
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GroceryListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("accountId"), Index("listId")]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val amount: Int,
    // yyyy-MM
    val month: String,
    val listId: Long?,
    val createdAt: Long
)

@Entity(
    tableName = "list_items",
    foreignKeys = [
        ForeignKey(
            entity = GroceryListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = CatalogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogItemId"]
        )
    ],
    indices = [Index("listId"), Index("catalogItemId")]
)
data class ListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val catalogItemId: Long,
    val quantity: Int,
    // Price per unit in Tk, captured when the item was added to the list
    val unitPrice: Int,
    val bought: Boolean = false,
    val alertOverridden: Boolean = false
)

@Entity(tableName = "catalog_items")
data class CatalogItemEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val category: FoodCategory,
    // Unit a quantity is counted in: "kg", "L" or "pcs"
    val unit: String,
    val gramsPerUnit: Int,
    val nutrientTag: NutrientTag,
    val caloriesPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val ironMgPer100g: Double,
    val allergens: Set<Allergen>,
    // Hand-set demo tags with no nutrition figure behind them. Stored, but read by nothing:
    // neither generation nor the profile review treats them as evidence.
    val flaggedConditions: Set<HealthCondition>
)

@Entity(
    tableName = "region_prices",
    primaryKeys = ["catalogItemId", "region"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["catalogItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RegionPriceEntity(
    val catalogItemId: Long,
    val region: String,
    // Tk per unit
    val price: Int,
    // Null for demo prices, which have no real update date
    val updatedAt: Long?
)

data class ListItemWithCatalog(
    @Embedded val item: ListItemEntity,
    @Relation(parentColumn = "catalogItemId", entityColumn = "id")
    val catalog: CatalogItemEntity
)
