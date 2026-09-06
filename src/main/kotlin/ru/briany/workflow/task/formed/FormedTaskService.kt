package ru.briany.workflow.task.formed

import org.flowable.engine.HistoryService
import org.flowable.engine.TaskService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.domain.form.FormResponse
import ru.briany.domain.form.FormService
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Instant

data class TaskDto(
    val id: String,
    val name: String?,
    val description: String?,
    val assignee: String?,
    val processInstanceId: String?,
    val taskDefinitionKey: String?,
    val formKey: String?,
    val createTime: Instant?,
    val endTime: Instant?,
    val dueDate: Instant?,
    val priority: Int,
)

data class FormedTaskResponse(
    val task: TaskDto,
    val form: FormResponse<Any>,
    val variables: Map<String, JsonNode>,
)

@Service
class FormedTaskService(
    private val taskService: TaskService,
    private val historyService: HistoryService,
    private val formService: FormService,
    private val objectMapper: ObjectMapper,
) {
    // Not the cleanest way to merge runtime and historic tasks, but it handles both uniformly.
    fun getFormedTask(taskId: String): FormedTaskResponse {
        val runtime = taskService.createTaskQuery().taskId(taskId).singleResult()

        data class TaskInfo(
            val dto: TaskDto,
            val processInstanceId: String?,
            val processDefinitionId: String?,
        )

        val taskInfo =
            if (runtime != null) {
                TaskInfo(
                    dto =
                        TaskDto(
                            id = runtime.id,
                            name = runtime.name,
                            description = runtime.description,
                            assignee = runtime.assignee,
                            processInstanceId = runtime.processInstanceId,
                            taskDefinitionKey = runtime.taskDefinitionKey,
                            formKey = runtime.formKey,
                            createTime = runtime.createTime?.toInstant(),
                            endTime = null,
                            dueDate = runtime.dueDate?.toInstant(),
                            priority = runtime.priority,
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
                        TaskDto(
                            id = historic.id,
                            name = historic.name,
                            description = historic.description,
                            assignee = historic.assignee,
                            processInstanceId = historic.processInstanceId,
                            taskDefinitionKey = historic.taskDefinitionKey,
                            formKey = historic.formKey,
                            createTime = historic.createTime?.toInstant(),
                            endTime = historic.endTime?.toInstant(),
                            dueDate = historic.dueDate?.toInstant(),
                            priority = historic.priority,
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

        return FormedTaskResponse(taskDto, form, variables)
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
