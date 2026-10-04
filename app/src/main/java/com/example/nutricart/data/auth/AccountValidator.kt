package com.example.nutricart.data.auth

enum class RegistrationError { NameBlank, EmailInvalid, EmailTaken, PasswordTooShort }

// Field rules for creating an account. EmailTaken needs the database and is
// reported by AccountRepository.
object AccountValidator {
    const val MIN_PASSWORD_LENGTH = 6

    private val emailPattern = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun isValidEmail(email: String): Boolean = emailPattern.matches(normalizeEmail(email))

    fun validate(name: String, email: String, password: String): Set<RegistrationError> = buildSet {
        if (name.isBlank()) add(RegistrationError.NameBlank)
        if (!isValidEmail(email)) add(RegistrationError.EmailInvalid)
        if (password.length < MIN_PASSWORD_LENGTH) add(RegistrationError.PasswordTooShort)
    }
}
