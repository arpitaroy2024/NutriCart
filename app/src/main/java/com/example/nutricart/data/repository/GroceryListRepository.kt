package com.example.nutricart.data.repository

import com.example.nutricart.data.SettingsStore
import com.example.nutricart.data.local.BudgetEntity
import com.example.nutricart.data.local.GroceryListDao
import com.example.nutricart.data.local.GroceryListEntity
import com.example.nutricart.data.local.ItemDao
import com.example.nutricart.data.local.ListItemEntity
import com.example.nutricart.data.local.ListItemWithCatalog
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

// The logged-in account's grocery lists and their items
interface GroceryListRepository {
    // Writes the list, its items and its budget row in one transaction and returns the list id
    suspend fun createList(budget: Int, items: List<NewListItem>): Long

    fun observeList(listId: Long): Flow<GroceryListEntity?>

    suspend fun latestList(): GroceryListEntity?

    fun observeLatestList(): Flow<GroceryListEntity?>

    fun observeItems(listId: Long): Flow<List<ListItemWithCatalog>>

    suspend fun getItems(listId: Long): List<ListItemWithCatalog>

    suspend fun setBought(itemId: Long, bought: Boolean)

    // Records "keep anyway" on a flagged item
    suspend fun overrideAlert(itemId: Long)

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

    override suspend fun createList(budget: Int, items: List<NewListItem>): Long {
        val accountId = settings.requireAccountId()
        val createdAt = now()
        return listDao.insertGenerated(
            list = GroceryListEntity(accountId = accountId, budget = budget, createdAt = createdAt),
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

    override suspend fun overrideAlert(itemId: Long) {
        itemDao.setAlertOverridden(itemId, settings.requireAccountId())
    }

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
