package ru.briany.workflow.application

import org.flowable.app.api.repository.AppDefinition
import org.flowable.app.api.repository.AppDeployment
import ru.briany.generated.model.Application
import ru.briany.generated.model.ApplicationDeployedResources
import ru.briany.generated.model.ApplicationRef
import ru.briany.workflow.application.DeploymentService.DeployedResource
import ru.briany.workflow.application.DeploymentService.DeployedResourceKind

object ApplicationMapper {
    fun from(
        def: AppDefinition,
        deployment: AppDeployment,
        deployedResources: List<DeployedResource>? = null,
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
        deployedResources = deployedResources?.let(::toDeployedResources),
    )

    private fun toDeployedResources(resources: List<DeployedResource>) =
        ApplicationDeployedResources(
            processDefinitions =
                resources.filter { it.kind == DeployedResourceKind.PROCESS }.map { it.ref },
            bforms =
                resources.filter { it.kind == DeployedResourceKind.FORM }.map { it.ref },
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
