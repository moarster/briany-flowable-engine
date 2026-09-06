package ru.briany.domain.modeler

import ru.briany.generated.model.ModelerAppState

/**
 * Recomputes draft/synced/ahead purely from content hashes. Hashes are the source of truth;
 * the denormalized `state` column is always recomputed from them.
 */
object ModelerStateCalculator {
    fun aggregate(files: List<ModelerAppFileEntity>): String =
        ModelerHashing.aggregate(files.map { it.fileKey to it.contentHash })

    fun fileState(
        app: ModelerAppEntity,
        file: ModelerAppFileEntity,
    ): ModelerAppState =
        when {
            app.deploymentId == null -> ModelerAppState.DRAFT
            file.deployedHash != null && file.deployedHash == file.contentHash -> ModelerAppState.SYNCED
            else -> ModelerAppState.AHEAD
        }

    fun appState(
        app: ModelerAppEntity,
        files: List<ModelerAppFileEntity>,
    ): ModelerAppState =
        when {
            app.deploymentId == null -> ModelerAppState.DRAFT
            aggregate(files) == app.deployedContentHash -> ModelerAppState.SYNCED
            else -> ModelerAppState.AHEAD
        }
}
