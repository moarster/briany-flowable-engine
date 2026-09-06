package ru.briany.domain.modeler

import ru.briany.generated.model.ApplicationRef
import ru.briany.generated.model.ModelerApp
import ru.briany.generated.model.ModelerAppFile
import ru.briany.generated.model.ModelerAppFileSummary
import ru.briany.generated.model.ModelerAppRef

object ModelerAppMapper {
    fun toRef(app: ModelerAppEntity): ModelerAppRef =
        ModelerAppRef(
            id = app.id,
            key = app.key,
            state = app.state,
            name = app.name,
            description = app.description,
            appDefinitionId = app.appDefinitionId,
            deploymentId = app.deploymentId,
            deployedVersion = app.deployedVersion,
            deployedAt = app.deployedAt,
            createdAt = app.createdAt,
            updatedAt = app.updatedAt,
        )

    fun toSummary(
        appKey: String,
        file: ModelerAppFileEntity,
    ): ModelerAppFileSummary =
        ModelerAppFileSummary(
            id = file.id,
            appKey = appKey,
            fileKey = file.fileKey,
            type = file.type,
            state = file.state,
            resourceName = file.resourceName,
            errorCount = file.errorLog?.size ?: 0,
            name = file.name,
            engineResourceId = file.engineResourceId,
        )

    fun toFile(
        appKey: String,
        file: ModelerAppFileEntity,
    ): ModelerAppFile =
        ModelerAppFile(
            id = file.id,
            appKey = appKey,
            fileKey = file.fileKey,
            type = file.type,
            state = file.state,
            resourceName = file.resourceName,
            contentHash = file.contentHash,
            errors = file.errorLog ?: emptyList(),
            name = file.name,
            description = file.description,
            engineResourceId = file.engineResourceId,
            deployedHash = file.deployedHash,
            createdAt = file.createdAt,
            updatedAt = file.updatedAt,
        )

    fun toApp(
        app: ModelerAppEntity,
        files: List<ModelerAppFileEntity>,
        deployedApplication: ApplicationRef?,
    ): ModelerApp =
        ModelerApp(
            id = app.id,
            key = app.key,
            state = app.state,
            name = app.name,
            description = app.description,
            appDefinitionId = app.appDefinitionId,
            deploymentId = app.deploymentId,
            deployedVersion = app.deployedVersion,
            deployedAt = app.deployedAt,
            createdAt = app.createdAt,
            updatedAt = app.updatedAt,
            readme = app.readme,
            files = files.map { summary -> toSummary(app.key, summary) },
            deployedApplication = deployedApplication,
        )
}
