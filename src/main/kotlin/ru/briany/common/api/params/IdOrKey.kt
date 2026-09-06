package ru.briany.common.api.params

sealed class IdOrKey {
    data class Id(
        val value: String,
    ) : IdOrKey()

    data class Key(
        val value: String,
    ) : IdOrKey()

    companion object {
        private val UUID_REGEX =
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

        fun parse(value: String): IdOrKey = if (UUID_REGEX.matches(value)) Id(value) else Key(value)
    }
}
