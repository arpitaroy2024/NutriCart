package com.example.nutricart.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// Every query on user-owned data takes the owning accountId, so one account
// can never read or change another account's rows.

@Dao
interface AccountDao {
    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Query("SELECT * FROM accounts WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    fun observeById(id: Long): Flow<AccountEntity?>
}

@Dao
interface ProfileDao {
    @Query("SELECT EXISTS(SELECT 1 FROM profiles WHERE accountId = :accountId)")
    suspend fun exists(accountId: Long): Boolean

    @Query("SELECT * FROM profiles WHERE accountId = :accountId")
    suspend fun get(accountId: Long): ProfileEntity?

    @Query("SELECT * FROM profiles WHERE accountId = :accountId")
    fun observe(accountId: Long): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets WHERE accountId = :accountId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latest(accountId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE accountId = :accountId ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeLatest(accountId: Long): Flow<BudgetEntity?>
}

@Dao
interface GroceryListDao {
    @Insert
    suspend fun insertList(list: GroceryListEntity): Long

    @Insert
    suspend fun insertItems(items: List<ListItemEntity>)

    @Insert
    suspend fun insertBudget(budget: BudgetEntity)

    // A generated list, its items and its budget row are written together or not at all
    @Transaction
    suspend fun insertGenerated(
        list: GroceryListEntity,
        items: List<ListItemEntity>,
        budget: BudgetEntity
    ): Long {
        val listId = insertList(list)
        insertItems(items.map { it.copy(listId = listId) })
        insertBudget(budget.copy(listId = listId))
        return listId
    }

    @Query("SELECT * FROM grocery_lists WHERE id = :listId AND accountId = :accountId")
    suspend fun get(listId: Long, accountId: Long): GroceryListEntity?

    @Query("SELECT * FROM grocery_lists WHERE id = :listId AND accountId = :accountId")
    fun observe(listId: Long, accountId: Long): Flow<GroceryListEntity?>

    @Query("SELECT * FROM grocery_lists WHERE accountId = :accountId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latest(accountId: Long): GroceryListEntity?

    @Query("SELECT * FROM grocery_lists WHERE accountId = :accountId ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeLatest(accountId: Long): Flow<GroceryListEntity?>
}

@Dao
interface ItemDao {
    @Insert
    suspend fun insertAll(items: List<ListItemEntity>)

    @Transaction
    @Query(
        "SELECT list_items.* FROM list_items " +
            "INNER JOIN grocery_lists ON grocery_lists.id = list_items.listId " +
            "WHERE list_items.listId = :listId AND grocery_lists.accountId = :accountId " +
            "ORDER BY list_items.id"
    )
    fun observeForList(listId: Long, accountId: Long): Flow<List<ListItemWithCatalog>>

    @Transaction
    @Query(
        "SELECT list_items.* FROM list_items " +
            "INNER JOIN grocery_lists ON grocery_lists.id = list_items.listId " +
            "WHERE list_items.listId = :listId AND grocery_lists.accountId = :accountId " +
            "ORDER BY list_items.id"
    )
    suspend fun getForList(listId: Long, accountId: Long): List<ListItemWithCatalog>

    @Query(
        "UPDATE list_items SET bought = :bought WHERE id = :itemId " +
            "AND listId IN (SELECT id FROM grocery_lists WHERE accountId = :accountId)"
    )
    suspend fun setBought(itemId: Long, accountId: Long, bought: Boolean): Int

    @Query(
        "UPDATE list_items SET alertOverridden = 1 WHERE id = :itemId " +
            "AND listId IN (SELECT id FROM grocery_lists WHERE accountId = :accountId)"
    )
    suspend fun setAlertOverridden(itemId: Long, accountId: Long): Int

    @Query("UPDATE list_items SET quantity = :quantity WHERE id = :itemId AND listId = :listId")
    suspend fun updateQuantity(listId: Long, itemId: Long, quantity: Int)

    @Query("DELETE FROM list_items WHERE listId = :listId AND id IN (:itemIds)")
    suspend fun deleteByIds(listId: Long, itemIds: List<Long>)

    // Single edits, each limited to the lists of one account

    // Adds delta to the stored quantity and keeps the result within the limits. Done in one
    // statement, so quick repeated taps cannot overwrite each other.
    @Query(
        "UPDATE list_items SET quantity = MIN(:max, MAX(:min, quantity + :delta)) WHERE id = :itemId " +
            "AND listId IN (SELECT id FROM grocery_lists WHERE accountId = :accountId)"
    )
    suspend fun changeQuantity(itemId: Long, accountId: Long, delta: Int, min: Int, max: Int): Int

    @Query(
        "SELECT * FROM list_items WHERE id = :itemId " +
            "AND listId IN (SELECT id FROM grocery_lists WHERE accountId = :accountId)"
    )
    suspend fun getOwned(itemId: Long, accountId: Long): ListItemEntity?

    @Query(
        "DELETE FROM list_items WHERE id = :itemId " +
            "AND listId IN (SELECT id FROM grocery_lists WHERE accountId = :accountId)"
    )
    suspend fun deleteOwned(itemId: Long, accountId: Long): Int

    // Removes the item and returns it as it was, so it can be put back
    @Transaction
    suspend fun removeOwned(itemId: Long, accountId: Long): ListItemEntity? {
        val item = getOwned(itemId, accountId) ?: return null
        deleteOwned(itemId, accountId)
        return item
    }

    @Query("SELECT * FROM list_items WHERE listId = :listId AND catalogItemId = :catalogItemId LIMIT 1")
    suspend fun findInList(listId: Long, catalogItemId: Long): ListItemEntity?

    // A list never holds the same catalog item twice: adding one that is already there
    // raises its quantity instead. Returns true when an existing row was increased.
    @Transaction
    suspend fun addOrIncrease(item: ListItemEntity, max: Int): Boolean {
        val existing = findInList(item.listId, item.catalogItemId)
        return if (existing == null) {
            insertAll(listOf(item))
            false
        } else {
            updateQuantity(item.listId, existing.id, minOf(max, existing.quantity + item.quantity))
            true
        }
    }

    // All edits to one list are written in a single transaction
    @Transaction
    suspend fun applyEdits(
        listId: Long,
        added: List<ListItemEntity>,
        quantities: Map<Long, Int>,
        removedItemIds: List<Long>
    ) {
        deleteByIds(listId, removedItemIds)
        quantities.forEach { (itemId, quantity) -> updateQuantity(listId, itemId, quantity) }
        insertAll(added)
    }
}

@Dao
interface CatalogDao {
    @Query("SELECT COUNT(*) FROM catalog_items")
    suspend fun itemCount(): Int

    @Insert
    suspend fun insertItems(items: List<CatalogItemEntity>)

    @Insert
    suspend fun insertPrices(prices: List<RegionPriceEntity>)

    @Transaction
    suspend fun insertSeed(items: List<CatalogItemEntity>, prices: List<RegionPriceEntity>) {
        insertItems(items)
        insertPrices(prices)
    }

    @Query("SELECT * FROM catalog_items ORDER BY name")
    suspend fun getAll(): List<CatalogItemEntity>

    @Query("SELECT * FROM catalog_items WHERE id = :id")
    suspend fun getById(id: Long): CatalogItemEntity?

    @Query("SELECT * FROM region_prices WHERE region = :region")
    suspend fun pricesForRegion(region: String): List<RegionPriceEntity>

    @Query("SELECT * FROM region_prices WHERE catalogItemId = :itemId AND region = :region")
    suspend fun price(itemId: Long, region: String): RegionPriceEntity?
}
