package ru.briany.engine.api

import org.flowable.engine.history.HistoricProcessInstance
import ru.briany.generated.model.ProcessInstance
import ru.briany.generated.model.ProcessInstanceState
import ru.briany.generated.model.ResourceRef

object ProcessInstanceMapper {
    fun from(instance: HistoricProcessInstance): ProcessInstance =
        ProcessInstance(
            id = instance.id,
            state = mapState(instance),
            name = instance.name,
            businessKey = instance.businessKey,
            startTime = instance.startTime?.toInstant(),
            endTime = instance.endTime?.toInstant(),
            startUserId = instance.startUserId,
            durationInMillis = instance.durationInMillis,
            processDefinition =
                ResourceRef(
                    id = instance.processDefinitionId,
                    key = instance.processDefinitionKey,
                    name = instance.processDefinitionName,
                    version = instance.processDefinitionVersion,
                ),
        )

    private fun mapState(instance: HistoricProcessInstance): ProcessInstanceState =
        when (instance.state) {
            "active" -> ProcessInstanceState.RUNNING
            "completed", "externally-terminated", "internally-terminated" -> ProcessInstanceState.COMPLETED
            "suspended" -> ProcessInstanceState.SUSPENDED
            else -> ProcessInstanceState.RUNNING
        }
}
