package com.example.nutricart.ui

import com.example.nutricart.data.TestEnvironment
import com.example.nutricart.data.auth.PasswordHasher
import com.example.nutricart.data.model.HealthCondition
import com.example.nutricart.navigation.EntryDestination
import com.example.nutricart.ui.screens.auth.AuthFailure
import com.example.nutricart.ui.screens.auth.CreateAccountViewModel
import com.example.nutricart.ui.screens.auth.LoginViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class AuthViewModelsTest {

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

    private fun sessionId(): Long? = runBlocking { env.settings.loggedInAccountId.first() }

    // An existing account that is logged out again, optionally with a profile
    private fun existingAccount(withProfile: Boolean = false) = runBlocking {
        env.accounts.register("Arpita Roy", "arpita@example.com", "secret123")
        if (withProfile) env.profiles.save("Rangpur Division", 4, emptySet(), emptySet())
        env.accounts.logout()
    }

    private fun loginViewModel(email: String, password: String) =
        LoginViewModel(env.accounts, env.router).apply {
            onEmailChange(email)
            onPasswordChange(password)
        }

    private fun createAccountViewModel(name: String, email: String, password: String) =
        CreateAccountViewModel(env.accounts, env.router).apply {
            onNameChange(name)
            onEmailChange(email)
            onPasswordChange(password)
        }

    // Login

    @Test
    fun login_buttonIsDisabledUntilTheInputIsUsable() {
        val viewModel = LoginViewModel(env.accounts, env.router)
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onEmailChange("not-an-email")
        viewModel.onPasswordChange("secret123")
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onEmailChange("arpita@example.com")
        viewModel.onPasswordChange("")
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onPasswordChange("secret123")
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun login_emailErrorAppearsOnlyAfterLeavingTheField() {
        val viewModel = LoginViewModel(env.accounts, env.router)
        viewModel.onEmailChange("not-an-email")
        assertFalse(viewModel.state.value.showEmailError)

        viewModel.onEmailFocusLost()
        assertTrue(viewModel.state.value.showEmailError)

        viewModel.onEmailChange("arpita@example.com")
        assertFalse(viewModel.state.value.showEmailError)
    }

    @Test
    fun login_withInvalidInputDoesNothing() {
        existingAccount()
        val viewModel = loginViewModel("not-an-email", "secret123")

        viewModel.submit()

        assertFalse(viewModel.state.value.loading)
        assertNull(viewModel.state.value.destination)
        assertNull(sessionId())
    }

    @Test
    fun login_successWithoutAProfileGoesToProfileSetup() {
        existingAccount()
        val viewModel = loginViewModel("arpita@example.com", "secret123")

        viewModel.submit()
        assertTrue(viewModel.state.value.loading)
        env.awaitUntil { !viewModel.state.value.loading }

        assertEquals(EntryDestination.ProfileSetup, viewModel.state.value.destination)
        assertNotNull(sessionId())
        assertEquals("", viewModel.state.value.password)
    }

    @Test
    fun login_successWithAProfileGoesToHome() {
        existingAccount(withProfile = true)
        val viewModel = loginViewModel("arpita@example.com", "secret123")

        viewModel.submit()
        env.awaitUntil { !viewModel.state.value.loading }

        assertEquals(EntryDestination.Home, viewModel.state.value.destination)
    }

    @Test
    fun login_wrongPasswordShowsTheGenericFailure() {
        existingAccount()
        val viewModel = loginViewModel("arpita@example.com", "wrong-password")

        viewModel.submit()
        env.awaitUntil { !viewModel.state.value.loading }

        assertEquals(AuthFailure.InvalidCredentials, viewModel.state.value.failure)
        assertNull(viewModel.state.value.destination)
        assertNull(sessionId())
    }

    @Test
    fun login_unknownEmailShowsTheSameFailure() {
        existingAccount()
        val viewModel = loginViewModel("nobody@example.com", "secret123")

        viewModel.submit()
        env.awaitUntil { !viewModel.state.value.loading }

        assertEquals(AuthFailure.InvalidCredentials, viewModel.state.value.failure)
        assertNull(sessionId())
    }

    @Test
    fun login_failureClearsWhenTheUserEditsAField() {
        existingAccount()
        val viewModel = loginViewModel("arpita@example.com", "wrong-password")
        viewModel.submit()
        env.awaitUntil { !viewModel.state.value.loading }

        viewModel.onPasswordChange("secret123")

        assertNull(viewModel.state.value.failure)
    }

    // Create account

    @Test
    fun register_buttonIsDisabledUntilEveryFieldIsValid() {
        val viewModel = CreateAccountViewModel(env.accounts, env.router)
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onNameChange("Arpita Roy")
        viewModel.onEmailChange("arpita@example.com")
        viewModel.onPasswordChange("12345")
        assertFalse(viewModel.state.value.canSubmit)

        viewModel.onPasswordChange("123456")
        assertTrue(viewModel.state.value.canSubmit)

        viewModel.onNameChange(" ")
        assertFalse(viewModel.state.value.canSubmit)
    }

    @Test
    fun register_fieldErrorsAppearOnlyAfterLeavingEachField() {
        val viewModel = createAccountViewModel(" ", "not-an-email", "123")
        val untouched = viewModel.state.value
        assertFalse(untouched.showNameError || untouched.showEmailInvalid || untouched.showPasswordError)

        viewModel.onNameFocusLost()
        viewModel.onEmailFocusLost()
        viewModel.onPasswordFocusLost()

        val touched = viewModel.state.value
        assertTrue(touched.showNameError)
        assertTrue(touched.showEmailInvalid)
        assertTrue(touched.showPasswordError)
    }

    @Test
    fun register_withInvalidInputCreatesNothing() {
        val viewModel = createAccountViewModel("Arpita Roy", "arpita@example.com", "123")

        viewModel.submit()

        assertFalse(viewModel.state.value.loading)
        assertNull(sessionId())
        assertNull(runBlocking { env.database.accountDao().findByEmail("arpita@example.com") })
    }

    @Test
    fun register_successLogsInAndGoesToProfileSetup() {
        val viewModel = createAccountViewModel("Arpita Roy", "Arpita@Example.com", "secret123")

        viewModel.submit()
        assertTrue(viewModel.state.value.loading)
        env.awaitUntil { !viewModel.state.value.loading }

        assertEquals(EntryDestination.ProfileSetup, viewModel.state.value.destination)
        val stored = runBlocking { env.database.accountDao().findByEmail("arpita@example.com") }!!
        assertEquals(stored.id, sessionId())
        assertEquals("Arpita Roy", runBlocking { env.accounts.currentAccount.first() }!!.name)
        // Only the salted hash reaches the database
        assertFalse(stored.passwordHash.contains("secret123"))
        assertTrue(PasswordHasher.verify("secret123", stored.passwordSalt, stored.passwordHash))
        assertEquals("", viewModel.state.value.password)
    }

    @Test
    fun register_duplicateEmailIsReportedOnTheEmailField() {
        existingAccount()
        val viewModel = createAccountViewModel("Someone Else", "ARPITA@example.com", "another456")

        viewModel.submit()
        env.awaitUntil { !viewModel.state.value.loading }

        assertTrue(viewModel.state.value.emailTaken)
        assertFalse(viewModel.state.value.canSubmit)
        assertNull(viewModel.state.value.destination)
        assertNull(sessionId())
    }

    @Test
    fun register_duplicateEmailErrorClearsWhenTheEmailChanges() {
        existingAccount()
        val viewModel = createAccountViewModel("Someone Else", "arpita@example.com", "another456")
        viewModel.submit()
        env.awaitUntil { !viewModel.state.value.loading }

        viewModel.onEmailChange("someone@example.com")

        assertFalse(viewModel.state.value.emailTaken)
        assertTrue(viewModel.state.value.canSubmit)
    }

    @Test
    fun registeredAccount_canLogInAgainAfterLoggingOut() {
        val register = createAccountViewModel("Arpita Roy", "arpita@example.com", "secret123")
        register.submit()
        env.awaitUntil { !register.state.value.loading }
        runBlocking { env.accounts.logout() }

        val login = loginViewModel("arpita@example.com", "secret123")
        login.submit()
        env.awaitUntil { !login.state.value.loading }

        assertEquals(EntryDestination.ProfileSetup, login.state.value.destination)
        assertNotNull(sessionId())
    }
}
