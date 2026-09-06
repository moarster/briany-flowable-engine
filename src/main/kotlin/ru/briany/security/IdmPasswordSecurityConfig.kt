package ru.briany.security

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration

/**
 * Validates the Flowable IDM admin password at application startup.
 *
 * If FLOWABLE_IDM_ADMIN_PASSWORD is not set or the value is weak this check throws an exception
 * and prevents the application from starting.
 */
@Configuration
class IdmPasswordSecurityConfig(
    @Value($$"${flowable.common.app.idm-admin.password}")
    private val idmAdminPassword: String,
) {
    private val log = LoggerFactory.getLogger(IdmPasswordSecurityConfig::class.java)

    companion object {
        private const val MIN_LENGTH = 12
    }

    @PostConstruct
    fun validateIdmAdminPassword() {
        val violations = mutableListOf<String>()

        if (idmAdminPassword.isBlank()) {
            throw IllegalStateException(
                "[SECURITY] FLOWABLE_IDM_ADMIN_PASSWORD is set to a blank string. " +
                    "Application will not start without a valid password.",
            )
        }

        if (idmAdminPassword.length < MIN_LENGTH) {
            violations += "password is too short: minimum $MIN_LENGTH characters, got ${idmAdminPassword.length}"
        }

        if (idmAdminPassword.any { it.isWhitespace() }) {
            violations += "password must not contain any whitespace characters (spaces, tabs, etc.)"
        }

        if (!idmAdminPassword.any { it.isUpperCase() }) {
            violations += "password must contain at least one uppercase letter"
        }

        if (!idmAdminPassword.any { it.isLowerCase() }) {
            violations += "password must contain at least one lowercase letter"
        }

        if (!idmAdminPassword.any { it.isDigit() }) {
            violations += "password must contain at least one digit"
        }

        if (!idmAdminPassword.any { it.isAllowedSpecialChar() }) {
            violations += "password must contain at least one special character (!@#$%^&* etc.)"
        }

        if (violations.isNotEmpty()) {
            throw IllegalStateException(
                "[SECURITY] FLOWABLE_IDM_ADMIN_PASSWORD does not meet security requirements:\n" +
                    violations.joinToString("\n") { "  - $it" } +
                    "\nApplication startup aborted. Set a strong password via FLOWABLE_IDM_ADMIN_PASSWORD.",
            )
        }

        log.info("[SECURITY] IDM admin password passed all security checks.")
    }

    private fun Char.isAllowedSpecialChar(): Boolean =
        this in
            setOf(
                '!',
                '@',
                '#',
                '$',
                '%',
                '^',
                '&',
                '*',
                '(',
                ')',
                '-',
                '_',
                '=',
                '+',
                '[',
                ']',
                '{',
                '}',
                ';',
                ':',
                '\'',
                '"',
                ',',
                '.',
                '/',
                '<',
                '>',
                '?',
                '\\',
                '|',
                '`',
                '~',
            )
}
