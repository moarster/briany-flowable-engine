package ru.briany.domain.modeler

import ru.briany.generated.model.ModelerAppState

/**
 * List filter over the modeler synchronization state, modeled on VersionFilter.
 */
sealed class ModelerStateFilter {
    data object All : ModelerStateFilter()

    data class Of(
        val state: ModelerAppState,
    ) : ModelerStateFilter()

    companion object {
        fun of(state: ModelerAppState?): ModelerStateFilter = state?.let { Of(it) } ?: All
    }
}
