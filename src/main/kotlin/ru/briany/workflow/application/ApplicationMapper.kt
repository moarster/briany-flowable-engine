package ru.briany.workflow.application

import org.flowable.app.api.repository.AppDefinition
import org.flowable.app.api.repository.AppDeployment
import ru.briany.generated.model.Application
import ru.briany.generated.model.ApplicationRef

object ApplicationMapper {
    fun from(
        def: AppDefinition,
        deployment: AppDeployment,
    ) = Application(
        id = def.id,
        key = def.key,
        name = def.name,
        version = def.version,
        description = def.description,
        category = def.category,
        resourceName = def.resourceName,
        deploymentId = def.deploymentId,
        deploymentTime = deployment.deploymentTime?.toInstant(),
    )

    fun Application.toRef() =
        ApplicationRef(
            id = id,
            key = key,
            name = name,
            version = version,
            description = description,
            category = category,
            resourceName = resourceName,
            deploymentId = deploymentId,
            icon = icon,
            theme = theme,
        )
}
