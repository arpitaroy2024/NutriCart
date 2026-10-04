package com.example.nutricart.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountValidatorTest {

    @Test
    fun validInput_hasNoErrors() {
        assertTrue(AccountValidator.validate("Arpita Roy", "arpita@example.com", "secret1").isEmpty())
    }

    @Test
    fun blankName_isRejected() {
        assertEquals(
            setOf(RegistrationError.NameBlank),
            AccountValidator.validate("   ", "arpita@example.com", "secret1")
        )
    }

    @Test
    fun invalidEmails_areRejected() {
        listOf("", "arpita", "arpita@", "@example.com", "arpita@example", "arpita roy@example.com").forEach {
            assertFalse("'$it' should be invalid", AccountValidator.isValidEmail(it))
        }
    }

    @Test
    fun validEmails_areAccepted() {
        listOf("arpita@example.com", "a.roy+shop@mail.example.org", "  Arpita@Example.com ").forEach {
            assertTrue("'$it' should be valid", AccountValidator.isValidEmail(it))
        }
    }

    @Test
    fun passwordShorterThanSix_isRejected() {
        assertEquals(
            setOf(RegistrationError.PasswordTooShort),
            AccountValidator.validate("Arpita", "arpita@example.com", "12345")
        )
        assertTrue(AccountValidator.validate("Arpita", "arpita@example.com", "123456").isEmpty())
    }

    @Test
    fun everyProblem_isReportedAtOnce() {
        assertEquals(
            setOf(
                RegistrationError.NameBlank,
                RegistrationError.EmailInvalid,
                RegistrationError.PasswordTooShort
            ),
            AccountValidator.validate("", "nope", "123")
        )
    }

    @Test
    fun email_isNormalizedToTrimmedLowercase() {
        assertEquals("arpita@example.com", AccountValidator.normalizeEmail("  Arpita@Example.COM "))
    }
}
