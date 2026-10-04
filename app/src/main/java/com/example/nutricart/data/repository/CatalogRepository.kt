package com.example.nutricart.data.repository

import com.example.nutricart.data.local.CatalogDao
import com.example.nutricart.data.local.CatalogItemEntity
import com.example.nutricart.data.local.CatalogSeed
import com.example.nutricart.data.local.DemoCatalogSeed
import com.example.nutricart.data.local.RegionPriceEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// The food catalog and its regional prices. Shared by all accounts.
interface CatalogRepository {
    suspend fun items(): List<CatalogItemEntity>

    suspend fun item(id: Long): CatalogItemEntity?

    // Prices for one region, keyed by catalog item id
    suspend fun prices(region: String): Map<Long, RegionPriceEntity>

    suspend fun price(itemId: Long, region: String): RegionPriceEntity?
}

class LocalCatalogRepository(
    private val catalogDao: CatalogDao,
    private val seed: CatalogSeed = DemoCatalogSeed
) : CatalogRepository {

    private val seedLock = Mutex()
    private var seeded = false

    // Fills an empty catalog from the seed the first time it is read
    private suspend fun ensureSeeded() {
        if (seeded) return
        seedLock.withLock {
            if (!seeded) {
                if (catalogDao.itemCount() == 0) catalogDao.insertSeed(seed.items, seed.prices)
                seeded = true
            }
        }
    }

    override suspend fun items(): List<CatalogItemEntity> {
        ensureSeeded()
        return catalogDao.getAll()
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
}
