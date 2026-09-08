package ru.briany.workflow.task.formed

import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.briany.generated.api.TaskApi
import ru.briany.generated.model.AssignTaskRequest
import ru.briany.generated.model.CompleteTaskRequest
import ru.briany.generated.model.Task
import ru.briany.generated.model.TaskAssignmentFilter
import ru.briany.generated.model.TaskPage
import java.time.Instant

@RestController
class TaskController(
    private val taskService: TaskService,
    private val formedTaskService: FormedTaskService,
) : TaskApi {
    override fun assignTask(
        id: String,
        assignTaskRequest: AssignTaskRequest,
    ): ResponseEntity<Task> = ResponseEntity.ok(taskService.assignTask(id, assignTaskRequest.userId))

    override fun claimTask(id: String): ResponseEntity<Task> = ResponseEntity.ok(taskService.claimTask(id))

    override fun completeTask(
        id: String,
        completeTaskRequest: CompleteTaskRequest?,
    ): ResponseEntity<Unit> {
        taskService.completeTask(id, completeTaskRequest?.variables)
        return ResponseEntity.noContent().build()
    }

    override fun getFormedTask(id: String) = ResponseEntity.ok(formedTaskService.getFormedTask(id))

    override fun getTask(id: String): ResponseEntity<Task> = ResponseEntity.ok(taskService.getTask(id))

    @Suppress("LongParameterList")
    override fun listTasks(
        state: String,
        assignment: TaskAssignmentFilter,
        assignee: String?,
        processDefinitionKey: String?,
        processInstanceId: String?,
        taskDefinitionKey: String?,
        nameLike: String?,
        dueBefore: Instant?,
        dueAfter: Instant?,
        pageable: Pageable,
    ): ResponseEntity<TaskPage> =
        ResponseEntity.ok(
            taskService.listTasks(
                state = state,
                assignment = assignment,
                assignee = assignee,
                processDefinitionKey = processDefinitionKey,
                processInstanceId = processInstanceId,
                taskDefinitionKey = taskDefinitionKey,
                nameLike = nameLike,
                dueBefore = dueBefore,
                dueAfter = dueAfter,
                pageable = pageable,
            ),
        )

    override fun unclaimTask(id: String): ResponseEntity<Task> = ResponseEntity.ok(taskService.unclaimTask(id))
}
