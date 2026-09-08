package ru.briany.workflow.task.formed

import org.flowable.engine.HistoryService
import org.flowable.engine.TaskService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.domain.form.FormResponse
import ru.briany.domain.form.FormService
import ru.briany.generated.model.FormedTask
import ru.briany.generated.model.Task
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@Service
class FormedTaskService(
    private val taskService: TaskService,
    private val historyService: HistoryService,
    private val formService: FormService,
    private val objectMapper: ObjectMapper,
) {
    // Not the cleanest way to merge runtime and historic tasks, but it handles both uniformly.
    fun getFormedTask(taskId: String): FormedTask {
        val runtime = taskService.createTaskQuery().taskId(taskId).singleResult()

        data class TaskInfo(
            val dto: Task,
            val processInstanceId: String?,
            val processDefinitionId: String?,
        )

        val taskInfo =
            if (runtime != null) {
                TaskInfo(
                    dto =
                        Task(
                            id = runtime.id,
                            name = runtime.name,
                            description = runtime.description,
                            assignee = runtime.assignee,
                            processInstanceId = runtime.processInstanceId,
                            taskDefinitionKey = runtime.taskDefinitionKey,
                            formKey = runtime.formKey,
                            createdAt = runtime.createTime?.toInstant(),
                            endedAt = null,
                            dueAt = runtime.dueDate?.toInstant(),
                            priority = runtime.priority,
                            state = runtime.state,
                        ),
                    processInstanceId = runtime.processInstanceId,
                    processDefinitionId = runtime.processDefinitionId,
                )
            } else {
                val historic =
                    historyService
                        .createHistoricTaskInstanceQuery()
                        .taskId(taskId)
                        .singleResult()
                        ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: $taskId")
                TaskInfo(
                    dto =
                        Task(
                            id = historic.id,
                            name = historic.name,
                            description = historic.description,
                            assignee = historic.assignee,
                            processInstanceId = historic.processInstanceId,
                            taskDefinitionKey = historic.taskDefinitionKey,
                            formKey = historic.formKey,
                            createdAt = historic.createTime?.toInstant(),
                            endedAt = historic.endTime?.toInstant(),
                            dueAt = historic.dueDate?.toInstant(),
                            priority = historic.priority,
                            state = "archived",
                        ),
                    processInstanceId = historic.processInstanceId,
                    processDefinitionId = historic.processDefinitionId,
                )
            }

        val taskDto = taskInfo.dto
        val formKey =
            checkNotNull(taskDto.formKey) { "Task '$taskId' has no formKey assigned" }

        val form = formService.getFormByDeploymentWithFallback(taskInfo.processDefinitionId, formKey)

        val variables = resolveVariables(taskId, taskInfo.processInstanceId, form, isRuntime = runtime != null)

        return FormedTask(taskDto, form, variables)
    }

    private fun resolveVariables(
        taskId: String,
        processInstanceId: String?,
        form: FormResponse<Any>,
        isRuntime: Boolean,
    ): Map<String, JsonNode> {
        val needed = form.variables.toSet()
        if (needed.isEmpty() || processInstanceId == null) return emptyMap()

        val resolved =
            if (isRuntime) {
                taskService.getVariables(taskId).filterKeys { it in needed }
            } else {
                historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .list()
                    .filter { it.variableName in needed }
                    .associate { it.variableName to it.value }
            }
        return resolved.mapValues { (_, v) -> objectMapper.valueToTree(v) }
    }
}
