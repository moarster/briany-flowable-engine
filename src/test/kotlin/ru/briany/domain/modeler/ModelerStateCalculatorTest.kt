package ru.briany.domain.modeler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.briany.generated.model.ModelerAppState
import ru.briany.generated.model.ModelerFileType
import java.util.UUID

class ModelerStateCalculatorTest {
    private fun app(deploymentId: String?): ModelerAppEntity =
        ModelerAppEntity(key = "app", deploymentId = deploymentId)

    private fun file(
        appId: UUID,
        contentHash: String,
        deployedHash: String?,
    ): ModelerAppFileEntity =
        ModelerAppFileEntity(
            appId = appId,
            fileKey = "modelerProcessA",
            type = ModelerFileType.BPMN,
            resourceName = "process-a.bpmn",
            content = ByteArray(0),
            contentHash = contentHash,
            deployedHash = deployedHash,
        )

    @Test
    fun `never deployed app and file are draft`() {
        val app = app(null)
        val file = file(app.id, contentHash = "h1", deployedHash = null)

        assertEquals(ModelerAppState.DRAFT, ModelerStateCalculator.fileState(app, file))
        assertEquals(ModelerAppState.DRAFT, ModelerStateCalculator.appState(app, listOf(file)))
    }

    @Test
    fun `deployed file with matching hash is synced`() {
        val app = app("dep-1")
        val file = file(app.id, contentHash = "h1", deployedHash = "h1")

        assertEquals(ModelerAppState.SYNCED, ModelerStateCalculator.fileState(app, file))
    }

    @Test
    fun `deployed file with changed content is ahead`() {
        val app = app("dep-1")
        val file = file(app.id, contentHash = "h2", deployedHash = "h1")

        assertEquals(ModelerAppState.AHEAD, ModelerStateCalculator.fileState(app, file))
    }

    @Test
    fun `file added after deploy with no deployed hash is ahead`() {
        val app = app("dep-1")
        val file = file(app.id, contentHash = "h1", deployedHash = null)

        assertEquals(ModelerAppState.AHEAD, ModelerStateCalculator.fileState(app, file))
    }

    @Test
    fun `app state is synced when aggregate matches the deployed content hash`() {
        val app = app("dep-1")
        val file = file(app.id, contentHash = "h1", deployedHash = "h1")
        app.deployedContentHash = ModelerStateCalculator.aggregate(listOf(file))

        assertEquals(ModelerAppState.SYNCED, ModelerStateCalculator.appState(app, listOf(file)))
    }

    @Test
    fun `app state is ahead when a file changed after deploy`() {
        val app = app("dep-1")
        val deployedFile = file(app.id, contentHash = "h1", deployedHash = "h1")
        app.deployedContentHash = ModelerStateCalculator.aggregate(listOf(deployedFile))

        val changedFile = file(app.id, contentHash = "h2", deployedHash = "h1")
        assertEquals(ModelerAppState.AHEAD, ModelerStateCalculator.appState(app, listOf(changedFile)))
    }
}
