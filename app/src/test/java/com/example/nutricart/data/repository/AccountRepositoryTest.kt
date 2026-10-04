package com.example.nutricart.data.repository

import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.auth.PasswordHasher
import com.example.nutricart.data.auth.RegistrationError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class AccountRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var env: TestEnvironment

    @Before
    fun setUp() {
        env = TestEnvironment(File(folder.root, "settings.preferences_pb"))
    }

    @After
    fun tearDown() {
        env.close()
    }

    @Test
    fun register_createsTheAccountAndLogsItIn() = runBlocking<Unit> {
        val result = env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")

        assertTrue(result is RegisterResult.Success)
        val account = (result as RegisterResult.Success).account
        assertEquals("Arpita Roy", account.name)
        assertEquals("arpita@example.com", account.email)
        assertEquals(account.id, env.settings.loggedInAccountId.first())
        assertEquals(account, env.accounts.currentAccount.first())
    }

    @Test
    fun register_storesASaltedHashNotThePassword() = runBlocking<Unit> {
        env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")

        val stored = env.database.accountDao().findByEmail("arpita@example.com")!!
        assertNotEquals("secret123", stored.passwordHash)
        assertFalse(stored.passwordHash.contains("secret123"))
        assertEquals(32, stored.passwordSalt.length)
        assertTrue(PasswordHasher.verify("secret123", stored.passwordSalt, stored.passwordHash))
    }

    @Test
    fun register_trimsTheNameAndNormalizesTheEmail() = runBlocking<Unit> {
        env.accounts.register("  Arpita Roy ", "  Arpita@Example.COM ", "secret123")

        val stored = env.database.accountDao().findByEmail("arpita@example.com")
        assertNotNull(stored)
        assertEquals("Arpita Roy", stored!!.name)
    }

    @Test
    fun register_rejectsADuplicateEmailIgnoringCase() = runBlocking<Unit> {
        val first = env.accounts.register("Arpita Roy", "arpita@example.com", "secret123") as RegisterResult.Success

        val second = env.accounts.register("Someone Else", "ARPITA@example.com", "another456")

        assertEquals(RegisterResult.Failure(setOf(RegistrationError.EmailTaken)), second)
        // The first account is untouched and still the logged-in one
        assertEquals(first.account, env.accounts.currentAccount.first())
    }

    @Test
    fun register_withInvalidInputCreatesNothing() = runBlocking<Unit> {
        val result = env.accounts.register(" ", "not-an-email", "123")

        assertEquals(
            RegisterResult.Failure(
                setOf(
                    RegistrationError.NameBlank,
                    RegistrationError.EmailInvalid,
                    RegistrationError.PasswordTooShort
                )
            ),
            result
        )
        assertNull(env.settings.loggedInAccountId.first())
        assertNull(env.database.accountDao().findByEmail("not-an-email"))
    }

    @Test
    fun twoAccountsWithTheSamePassword_getDifferentHashes() = runBlocking<Unit> {
        env.accounts.register("One", "one@example.com", "secret123")
        env.accounts.register("Two", "two@example.com", "secret123")

        val one = env.database.accountDao().findByEmail("one@example.com")!!
        val two = env.database.accountDao().findByEmail("two@example.com")!!
        assertNotEquals(one.passwordSalt, two.passwordSalt)
        assertNotEquals(one.passwordHash, two.passwordHash)
    }

    @Test
    fun login_withTheCorrectPasswordStartsASession() = runBlocking<Unit> {
        val registered = env.accounts.register("Arpita Roy", "arpita@example.com", "secret123") as RegisterResult.Success
        env.accounts.logout()

        val result = env.accounts.login(" Arpita@example.com", "secret123")

        assertEquals(LoginResult.Success(registered.account), result)
        assertEquals(registered.account.id, env.settings.loggedInAccountId.first())
    }

    @Test
    fun login_withAWrongPasswordFailsAndStaysLoggedOut() = runBlocking<Unit> {
        env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
        env.accounts.logout()

        val result = env.accounts.login("arpita@example.com", "secret124")

        assertEquals(LoginResult.InvalidCredentials, result)
        assertNull(env.settings.loggedInAccountId.first())
    }

    @Test
    fun login_givesTheSameFailureForAnUnknownEmailAndAWrongPassword() = runBlocking<Unit> {
        env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
        env.accounts.logout()

        val unknownEmail = env.accounts.login("nobody@example.com", "secret123")
        val wrongPassword = env.accounts.login("arpita@example.com", "wrong-password")

        assertEquals(LoginResult.InvalidCredentials, unknownEmail)
        assertEquals(unknownEmail, wrongPassword)
    }

    @Test
    fun logout_endsTheSessionButKeepsTheAccount() = runBlocking<Unit> {
        env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")

        env.accounts.logout()

        assertNull(env.accounts.currentAccount.first())
        assertNotNull(env.database.accountDao().findByEmail("arpita@example.com"))
        assertTrue(env.accounts.login("arpita@example.com", "secret123") is LoginResult.Success)
    }
}
