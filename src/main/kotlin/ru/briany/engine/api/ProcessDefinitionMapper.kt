package ru.briany.engine.api

import ru.briany.generated.model.ProcessDefinition

object ProcessDefinitionMapper {
    fun from(pd: org.flowable.engine.repository.ProcessDefinition): ProcessDefinition =
        ProcessDefinition(
            id = pd.id,
            name = pd.name,
            description = pd.description,
            key = pd.key,
            category = pd.category,
            version = pd.version,
            deploymentId = pd.deploymentId,
            hasStartForm = pd.hasStartFormKey(),
        )
}
