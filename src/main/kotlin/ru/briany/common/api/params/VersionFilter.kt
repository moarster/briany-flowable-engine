package ru.briany.common.api.params

sealed class VersionFilter {
    data object All : VersionFilter()

    data object Latest : VersionFilter()

    data class Exact(
        val version: Int,
    ) : VersionFilter()

    companion object {
        fun parse(value: String?): VersionFilter =
            when {
                value == null -> {
                    Latest
                }

                value.equals("all", ignoreCase = true) -> {
                    All
                }

                value.equals("latest", ignoreCase = true) -> {
                    Latest
                }

                else -> {
                    value.toIntOrNull()?.let { Exact(it) }
                        ?: throw IllegalArgumentException("Invalid version: '$value' (expected 'all', 'latest', or a number)")
                }
            }
    }
}
