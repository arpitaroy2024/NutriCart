package com.example.nutricart.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.nutricart.data.SettingsStore
import com.example.nutricart.data.auth.AccountValidator
import com.example.nutricart.data.auth.PasswordHasher
import com.example.nutricart.data.auth.RegistrationError
import com.example.nutricart.data.local.AccountDao
import com.example.nutricart.data.local.AccountEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

// An account as the rest of the app sees it: no password material
data class Account(
    val id: Long,
    val name: String,
    val email: String,
    val createdAt: Long
)

sealed interface RegisterResult {
    data class Success(val account: Account) : RegisterResult
    data class Failure(val errors: Set<RegistrationError>) : RegisterResult
}

sealed interface LoginResult {
    data class Success(val account: Account) : LoginResult

    // Deliberately does not say whether the email or the password was wrong
    data object InvalidCredentials : LoginResult
}

interface AccountRepository {
    // The logged-in account, or null
    val currentAccount: Flow<Account?>

    // Creates the account and logs it in
    suspend fun register(name: String, email: String, password: String): RegisterResult

    suspend fun login(email: String, password: String): LoginResult

    // Ends the session. Accounts, profiles and grocery history stay on the device.
    suspend fun logout()
}

class LocalAccountRepository(
    private val accountDao: AccountDao,
    private val settings: SettingsStore,
    private val hashingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = System::currentTimeMillis
) : AccountRepository {

    override val currentAccount: Flow<Account?> =
        settings.forCurrentAccount { accountId ->
            accountDao.observeById(accountId).map { it?.toAccount() }
        }

    override suspend fun register(name: String, email: String, password: String): RegisterResult {
        val errors = AccountValidator.validate(name, email, password)
        if (errors.isNotEmpty()) return RegisterResult.Failure(errors)

        val normalizedEmail = AccountValidator.normalizeEmail(email)
        if (accountDao.findByEmail(normalizedEmail) != null) {
            return RegisterResult.Failure(setOf(RegistrationError.EmailTaken))
        }

        val salt = PasswordHasher.newSalt()
        val hash = withContext(hashingDispatcher) { PasswordHasher.hash(password, salt) }
        val entity = AccountEntity(
            name = name.trim(),
            email = normalizedEmail,
            passwordHash = hash,
            passwordSalt = salt,
            createdAt = now()
        )
        val id = try {
            accountDao.insert(entity)
        } catch (e: SQLiteConstraintException) {
            // Another registration took the email between the check and the insert
            return RegisterResult.Failure(setOf(RegistrationError.EmailTaken))
        }

        settings.setLoggedInAccountId(id)
        return RegisterResult.Success(entity.copy(id = id).toAccount())
    }

    override suspend fun login(email: String, password: String): LoginResult {
        val entity = accountDao.findByEmail(AccountValidator.normalizeEmail(email))
        val matches = withContext(hashingDispatcher) {
            if (entity == null) {
                // Hash anyway so an unknown email takes as long as a wrong password
                PasswordHasher.hash(password, DUMMY_SALT)
                false
            } else {
                PasswordHasher.verify(password, entity.passwordSalt, entity.passwordHash)
            }
        }
        if (entity == null || !matches) return LoginResult.InvalidCredentials

        settings.setLoggedInAccountId(entity.id)
        return LoginResult.Success(entity.toAccount())
    }

    override suspend fun logout() {
        settings.setLoggedInAccountId(null)
    }

    private fun AccountEntity.toAccount() = Account(id, name, email, createdAt)

    private companion object {
        const val DUMMY_SALT = "00000000000000000000000000000000"
    }
}
