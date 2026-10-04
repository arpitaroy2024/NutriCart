package com.example.nutricart.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    @Test
    fun salt_isRandomAnd16Bytes() {
        val first = PasswordHasher.newSalt()
        val second = PasswordHasher.newSalt()

        assertEquals(32, first.length)
        assertNotEquals(first, second)
    }

    @Test
    fun hash_isDeterministicForSamePasswordAndSalt() {
        val salt = PasswordHasher.newSalt()

        assertEquals(PasswordHasher.hash("secret123", salt), PasswordHasher.hash("secret123", salt))
    }

    @Test
    fun hash_differsWithDifferentSalt() {
        val first = PasswordHasher.hash("secret123", PasswordHasher.newSalt())
        val second = PasswordHasher.hash("secret123", PasswordHasher.newSalt())

        assertNotEquals(first, second)
    }

    @Test
    fun hash_doesNotContainThePassword() {
        val hash = PasswordHasher.hash("secret123", PasswordHasher.newSalt())

        assertEquals(40, hash.length)
        assertFalse(hash.contains("secret123"))
    }

    @Test
    fun verify_acceptsTheCorrectPassword() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("secret123", salt)

        assertTrue(PasswordHasher.verify("secret123", salt, hash))
    }

    @Test
    fun verify_rejectsAWrongPassword() {
        val salt = PasswordHasher.newSalt()
        val hash = PasswordHasher.hash("secret123", salt)

        assertFalse(PasswordHasher.verify("secret124", salt, hash))
        assertFalse(PasswordHasher.verify("", salt, hash))
    }

    @Test
    fun verify_rejectsTheRightPasswordWithAnotherSalt() {
        val hash = PasswordHasher.hash("secret123", PasswordHasher.newSalt())

        assertFalse(PasswordHasher.verify("secret123", PasswordHasher.newSalt(), hash))
    }
}
