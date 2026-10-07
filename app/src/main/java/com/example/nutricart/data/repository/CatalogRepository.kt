package com.example.nutricart.data.repository

import com.example.nutricart.data.local.CatalogDao
import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.CatalogSeed
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.local.RegionPriceEntity
import com.example.nutricart.domain.BasketSlot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The food catalog and its regional prices. Shared by all accounts.
interface CatalogRepository {
    // Every row, including packs that are no longer offered but that older lists still hold
    suspend fun items(): List<CatalogItemEntity>

    // The packs offered now: what the item picker and suggested alternatives choose from
    suspend fun offeredItems(): List<CatalogItemEntity>

    suspend fun item(id: Long): CatalogItemEntity?

    // Prices for one region, keyed by catalog item id
    suspend fun prices(region: String): Map<Long, RegionPriceEntity>

    suspend fun price(itemId: Long, region: String): RegionPriceEntity?

    // The monthly basket that goes with this catalog, for the grocery generator. Amounts are
    // grams, millilitres or pieces, to match the packs its items are sold in.
    fun basketTemplate(): List<BasketSlot>
}

class LocalCatalogRepository(
    private val catalogDao: CatalogDao,
    private val seed: CatalogSeed = DemoCatalogSeed
) : CatalogRepository {

    private val seedLock = Mutex()
    private var seeded = false

    // Fills an empty catalog from the seed the first time it is read. A catalog seeded by an
    // earlier version is topped up instead: packs it lacks are added and the packs they
    // replace are withdrawn. Rows already there keep their unit, weight and prices.
    private suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (!seeded) {
                val present = catalogDao.itemIds().toSet()
                if (present.isEmpty()) {
                    catalogDao.insertSeed(seed.items, seed.prices)
                } else {
                    val missing = seed.items.filter { it.id !in present }
                    val missingIds = missing.map { it.id }.toSet()
                    catalogDao.topUp(
                        items = missing,
                        prices = seed.prices.filter { it.catalogItemId in missingIds },
                        withdrawn = seed.items.filterNot { it.offered }.map { it.id }
                    )
                }
                seeded = true
            }
        }
    }

    override suspend fun items(): List<CatalogItemEntity> {
        ensureSeeded()
        return catalogDao.getAll()
    }

    override suspend fun offeredItems(): List<CatalogItemEntity> {
        ensureSeeded()
        return catalogDao.getOffered()
    }

    override suspend fun item(id: Long): CatalogItemEntity? {
        ensureSeeded()
        return catalogDao.getById(id)
    }

    override suspend fun prices(region: String): Map<Long, RegionPriceEntity> {
        ensureSeeded()
        return catalogDao.pricesForRegion(region).associateBy { it.catalogItemId }
    }

    override suspend fun price(itemId: Long, region: String): RegionPriceEntity? {
        ensureSeeded()
        return catalogDao.price(itemId, region)
    }

    override fun basketTemplate(): List<BasketSlot> = seed.basket
}
