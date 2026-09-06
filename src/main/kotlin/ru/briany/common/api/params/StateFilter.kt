package ru.briany.common.api.params

import ru.briany.generated.model.ProcessInstanceState

sealed class StateFilter {
    data object All : StateFilter()

    data class Of(
        val state: ProcessInstanceState,
    ) : StateFilter()

    companion object {
        fun parse(value: String?): StateFilter =
            when {
                value == null -> {
                    All
                }

                else -> {
                    Of(
                        ProcessInstanceState.entries.firstOrNull { it.value == value }
                            ?: throw IllegalArgumentException(
                                """Invalid state: '$value' (expected
                                     ${ProcessInstanceState.entries.joinToString { it.value }})""",
                            ),
                    )
                }
            }
    }
}
