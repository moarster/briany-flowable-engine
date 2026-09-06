package ru.briany.utils

import java.security.SecureRandom

object PasswordGenerator {
    fun generateSecurePassword(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val secureRandom = SecureRandom()
        val result = StringBuilder(length)
        for (i in 0 until length) {
            val index = secureRandom.nextInt(alphabet.length)
            result.append(alphabet[index])
        }
        return result.toString()
    }

    fun generateSecurePassword(): String = generateSecurePassword(DEFAULT_PASSWORD_LENGTH)

    private const val DEFAULT_PASSWORD_LENGTH = 32
}
