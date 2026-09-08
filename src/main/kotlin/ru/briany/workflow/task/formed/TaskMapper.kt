package ru.briany.workflow.task.formed

import org.flowable.task.api.TaskInfo
import ru.briany.generated.model.Task
import java.time.Instant

/**
 * Maps Flowable's [TaskInfo] (the shared supertype of a runtime task and a
 * [org.flowable.task.api.history.HistoricTaskInstance]) to the API [Task] DTO.
 *
 * `state` is normalised to the contract's `active` / `completed` rather than Flowable's
 * internal task states, and the process definition key/name and business key are passed
 * in because Flowable does not carry them on the task itself.
 */
object TaskMapper {
    private const val CMMN_SCOPE = "cmmn"

    @Suppress("LongParameterList")
    fun toDto(
        task: TaskInfo,
        state: String,
        endedAt: Instant?,
        processDefinitionKey: String?,
        processDefinitionName: String?,
        processBusinessKey: String?,
    ): Task =
        Task(
            id = task.id,
            state = state,
            priority = task.priority,
            name = task.name,
            description = task.description,
            assignee = task.assignee,
            processInstanceId = task.processInstanceId,
            processDefinitionId = task.processDefinitionId,
            processDefinitionKey = processDefinitionKey,
            processDefinitionName = processDefinitionName,
            caseInstanceId = task.scopeId?.takeIf { task.scopeType == CMMN_SCOPE },
            taskDefinitionKey = task.taskDefinitionKey,
            formKey = task.formKey,
            createdAt = task.createTime?.toInstant(),
            endedAt = endedAt,
            dueAt = task.dueDate?.toInstant(),
            processBusinessKey = processBusinessKey,
        )
}
