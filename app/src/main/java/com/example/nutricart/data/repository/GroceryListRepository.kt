package com.example.nutricart.data.repository

import com.example.nutricart.data.SettingsStore
import com.example.nutricart.data.local.BudgetEntity
import com.example.nutricart.data.local.GroceryListDao
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ItemDao
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
import com.example.nutricart.domain.ListRules
import com.example.nutricart.domain.PlanningPeriod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NewListItem(
    val catalogItemId: Long,
    val quantity: Int,
    val unitPrice: Int
)

enum class AddItemResult { Added, QuantityIncreased, ListNotFound }

// The logged-in account's grocery lists and their items
interface GroceryListRepository {
    // Writes the list, its items and its budget row in one transaction and returns the list id.
    // periodDays is the days the list is planned for; anything unusual is stored as a month.
    suspend fun createList(
        budget: Int,
        items: List<NewListItem>,
        periodDays: Int = PlanningPeriod.DEFAULT_DAYS
    ): Long

    fun observeList(listId: Long): Flow<GroceryListEntity?>

    suspend fun latestList(): GroceryListEntity?

    fun observeLatestList(): Flow<GroceryListEntity?>

    fun observeItems(listId: Long): Flow<List<ListItemWithCatalog>>

    suspend fun getItems(listId: Long): List<ListItemWithCatalog>

    suspend fun setBought(itemId: Long, bought: Boolean)

    // Records "keep anyway" on a flagged item
    suspend fun overrideAlert(itemId: Long)

    // Sets or clears "keep anyway" on one item of one list. It is a note about that item
    // only: it changes nothing on the profile and nothing about what is flagged.
    suspend fun setKeptAnyway(itemId: Long, kept: Boolean)

    // Swaps a list item for another catalog item in one step; false if the item is not this account's
    suspend fun replaceItem(itemId: Long, catalogItemId: Long, quantity: Int, unitPrice: Int): Boolean

    // Single edits, saved as they are made

    // Adds delta (usually +1 or -1) to an item's quantity, kept within ListRules
    suspend fun changeQuantity(itemId: Long, delta: Int)

    // Removes the item and returns it for a later undo; null if it is not on one of this account's lists
    suspend fun removeItem(itemId: Long): ListItemEntity?

    // Puts back an item returned by removeItem, in its old place and with its old state
    suspend fun restoreItem(item: ListItemEntity): Boolean

    // Adds one unit of a catalog item at the given price, or raises the quantity if the list has it already
    suspend fun addItem(listId: Long, catalogItemId: Long, unitPrice: Int): AddItemResult

    // Applies every change from an edit session in one transaction
    suspend fun saveEdits(
        listId: Long,
        added: List<NewListItem>,
        quantities: Map<Long, Int>,
        removedItemIds: Set<Long>
    )
}

class LocalGroceryListRepository(
    private val listDao: GroceryListDao,
    private val itemDao: ItemDao,
    private val settings: SettingsStore,
    private val now: () -> Long = System::currentTimeMillis
) : GroceryListRepository {

    override suspend fun createList(budget: Int, items: List<NewListItem>, periodDays: Int): Long {
        val accountId = settings.requireAccountId()
        val createdAt = now()
        return listDao.insertGenerated(
            list = GroceryListEntity(
                accountId = accountId,
                budget = budget,
                createdAt = createdAt,
                periodDays = PlanningPeriod.normalize(periodDays)
            ),
            items = items.map { it.toEntity(listId = 0) },
            budget = BudgetEntity(
                accountId = accountId,
                amount = budget,
                month = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(createdAt)),
                listId = null,
                createdAt = createdAt
            )
        )
    }

    override fun observeList(listId: Long): Flow<GroceryListEntity?> =
        settings.forCurrentAccount { listDao.observe(listId, it) }

    override suspend fun latestList(): GroceryListEntity? =
        listDao.latest(settings.requireAccountId())

    override fun observeLatestList(): Flow<GroceryListEntity?> =
        settings.forCurrentAccount { listDao.observeLatest(it) }

    override fun observeItems(listId: Long): Flow<List<ListItemWithCatalog>> =
        settings.forCurrentAccount { itemDao.observeForList(listId, it) }.map { it.orEmpty() }

    override suspend fun getItems(listId: Long): List<ListItemWithCatalog> =
        itemDao.getForList(listId, settings.requireAccountId())

    override suspend fun setBought(itemId: Long, bought: Boolean) {
        itemDao.setBought(itemId, settings.requireAccountId(), bought)
    }

    override suspend fun overrideAlert(itemId: Long) = setKeptAnyway(itemId, true)

    override suspend fun setKeptAnyway(itemId: Long, kept: Boolean) {
        itemDao.setAlertOverridden(itemId, settings.requireAccountId(), kept)
    }

    override suspend fun replaceItem(itemId: Long, catalogItemId: Long, quantity: Int, unitPrice: Int): Boolean {
        require(unitPrice > 0) { "A replacement needs a price" }
        return itemDao.replaceOwned(
            itemId = itemId,
            accountId = settings.requireAccountId(),
            replacement = ListItemEntity(
                listId = 0,
                catalogItemId = catalogItemId,
                quantity = quantity.coerceIn(ListRules.MIN_QUANTITY, ListRules.MAX_QUANTITY),
                unitPrice = unitPrice
            ),
            max = ListRules.MAX_QUANTITY
        )
    }

    override suspend fun changeQuantity(itemId: Long, delta: Int) {
        itemDao.changeQuantity(
            itemId, settings.requireAccountId(), delta, ListRules.MIN_QUANTITY, ListRules.MAX_QUANTITY
        )
    }

    override suspend fun removeItem(itemId: Long): ListItemEntity? =
        itemDao.removeOwned(itemId, settings.requireAccountId())

    override suspend fun restoreItem(item: ListItemEntity): Boolean {
        if (!ownsList(item.listId)) return false
        // If the same catalog item was added again in the meantime, the two are merged
        itemDao.addOrIncrease(item, ListRules.MAX_QUANTITY)
        return true
    }

    override suspend fun addItem(listId: Long, catalogItemId: Long, unitPrice: Int): AddItemResult {
        require(unitPrice > 0) { "An item needs a price to be added" }
        if (!ownsList(listId)) return AddItemResult.ListNotFound
        val increased = itemDao.addOrIncrease(
            ListItemEntity(listId = listId, catalogItemId = catalogItemId, quantity = 1, unitPrice = unitPrice),
            ListRules.MAX_QUANTITY
        )
        return if (increased) AddItemResult.QuantityIncreased else AddItemResult.Added
    }

    private suspend fun ownsList(listId: Long): Boolean =
        listDao.get(listId, settings.requireAccountId()) != null

    override suspend fun saveEdits(
        listId: Long,
        added: List<NewListItem>,
        quantities: Map<Long, Int>,
        removedItemIds: Set<Long>
    ) {
        val owned = listDao.get(listId, settings.requireAccountId()) != null
        check(owned) { "List $listId does not belong to the logged-in account" }
        itemDao.applyEdits(
            listId = listId,
            added = added.map { it.toEntity(listId) },
            quantities = quantities,
            removedItemIds = removedItemIds.toList()
        )
    }

    private fun NewListItem.toEntity(listId: Long) = ListItemEntity(
        listId = listId,
        catalogItemId = catalogItemId,
        quantity = quantity,
        unitPrice = unitPrice
    )
}
