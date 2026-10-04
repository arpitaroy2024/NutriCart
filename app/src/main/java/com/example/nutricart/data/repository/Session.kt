package com.example.nutricart.data.repository

import com.example.nutricart.data.SettingsStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

// Repositories for user-owned data never take an account id from the caller.
// They read it from the session, so they can only reach the logged-in account's rows.

internal suspend fun SettingsStore.requireAccountId(): Long =
    loggedInAccountId.first() ?: throw IllegalStateException("No account is logged in")

// Follows the session: emits null while logged out and switches source on login
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> SettingsStore.forCurrentAccount(source: (accountId: Long) -> Flow<T?>): Flow<T?> =
    loggedInAccountId.flatMapLatest { accountId ->
        if (accountId == null) flowOf(null) else source(accountId)
    }
