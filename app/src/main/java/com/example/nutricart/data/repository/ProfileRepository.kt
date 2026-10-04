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

    // Writes the whole profile in one statement; a partial profile is never stored.
    // No conditions and no custom conditions means the user has none.
    // Custom entries are cleaned with cleanCustomEntries before they are stored.
    suspend fun save(
        region: String,
        householdSize: Int,
        allergies: Set<Allergen>,
        conditions: Set<HealthCondition>,
        customAllergies: List<String> = emptyList(),
        customConditions: List<String> = emptyList()
    )

    companion object {
        const val MIN_HOUSEHOLD = 1
        const val MAX_HOUSEHOLD = 20
        const val MAX_CUSTOM_LENGTH = 40
        const val MAX_CUSTOM_ENTRIES = 10

        // Trims each entry and cuts it to the length limit
        fun cleanCustomEntry(entry: String): String = entry.trim().take(MAX_CUSTOM_LENGTH).trim()

        // True when the list already holds the entry, ignoring case and outer spaces
        fun containsCustomEntry(entries: List<String>, entry: String): Boolean {
            val cleaned = cleanCustomEntry(entry)
            return entries.any { it.equals(cleaned, ignoreCase = true) }
        }

        // Drops blanks and repeats (ignoring case, first spelling kept), keeps the order,
        // and stops at the entry limit
        fun cleanCustomEntries(entries: List<String>): List<String> {
            val result = mutableListOf<String>()
            for (entry in entries) {
                val cleaned = cleanCustomEntry(entry)
                if (cleaned.isNotEmpty() && !containsCustomEntry(result, cleaned)) result += cleaned
                if (result.size == MAX_CUSTOM_ENTRIES) break
            }
            return result
        }
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
        conditions: Set<HealthCondition>,
        customAllergies: List<String>,
        customConditions: List<String>
    ) {
        require(region.isNotBlank()) { "Region is required" }
        require(householdSize in ProfileRepository.MIN_HOUSEHOLD..ProfileRepository.MAX_HOUSEHOLD) {
            "Household size must be between 1 and 20"
        }
        profileDao.upsert(
            ProfileEntity(
                accountId = settings.requireAccountId(),
                region = region,
                householdSize = householdSize,
                allergies = allergies,
                customAllergies = ProfileRepository.cleanCustomEntries(customAllergies),
                conditions = conditions,
                customConditions = ProfileRepository.cleanCustomEntries(customConditions)
            )
        )
    }
}
