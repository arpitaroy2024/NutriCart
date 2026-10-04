package com.example.nutricart.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

// Salted PBKDF2 password hashing. SHA-1 is used because PBKDF2WithHmacSHA256
// is only available from API 26 and the app supports API 24.
object PasswordHasher {
    private const val ALGORITHM = "PBKDF2WithHmacSHA1"
    private const val ITERATIONS = 300_000
    private const val SALT_BYTES = 16
    private const val KEY_BITS = 160

    private val random = SecureRandom()

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        random.nextBytes(salt)
        return salt.toHex()
    }

    fun hash(password: String, salt: String): String {
        val spec = PBEKeySpec(password.toCharArray(), salt.hexToBytes(), ITERATIONS, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded.toHex()
        } finally {
            spec.clearPassword()
        }
    }

    // Constant-time comparison
    fun verify(password: String, salt: String, expectedHash: String): Boolean =
        MessageDigest.isEqual(hash(password, salt).hexToBytes(), expectedHash.hexToBytes())

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray =
        ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
