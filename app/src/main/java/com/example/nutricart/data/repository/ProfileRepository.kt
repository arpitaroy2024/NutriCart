package com.example.nutricart.data.repository

import com.example.nutricart.data.SettingsStore
import com.example.nutricart.data.local.ProfileDao
import com.example.nutricart.data.local.ProfileEntity
import com.example.nutricart.data.model.Allergen
import com.example.nutricart.data.model.HealthCondition
import kotlinx.coroutines.flow.Flow

// The logged-in account's profile
interface ProfileRepository {
    fun observe(): Flow<ProfileEntity?>

    suspend fun get(): ProfileEntity?

    suspend fun exists(): Boolean

    // Writes the whole profile in one statement; a partial profile is never stored
    suspend fun save(
        region: String,
        householdSize: Int,
        allergies: Set<Allergen>,
        condition: HealthCondition
    )

    companion object {
        const val MIN_HOUSEHOLD = 1
        const val MAX_HOUSEHOLD = 12
    }
}

class LocalProfileRepository(
    private val profileDao: ProfileDao,
    private val settings: SettingsStore
) : ProfileRepository {

    override fun observe(): Flow<ProfileEntity?> =
        settings.forCurrentAccount { profileDao.observe(it) }

    override suspend fun get(): ProfileEntity? = profileDao.get(settings.requireAccountId())

    override suspend fun exists(): Boolean = profileDao.exists(settings.requireAccountId())

    override suspend fun save(
        region: String,
        householdSize: Int,
        allergies: Set<Allergen>,
        condition: HealthCondition
    ) {
        require(region.isNotBlank()) { "Region is required" }
        require(householdSize in ProfileRepository.MIN_HOUSEHOLD..ProfileRepository.MAX_HOUSEHOLD) {
            "Household size must be between 1 and 12"
        }
        profileDao.upsert(
            ProfileEntity(
                accountId = settings.requireAccountId(),
                region = region,
                householdSize = householdSize,
                allergies = allergies,
                condition = condition
            )
        )
    }
}
