package ru.briany.workflow.task.formed

import org.flowable.engine.HistoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.repository.ProcessDefinition
import org.flowable.idm.api.IdmIdentityService
import org.flowable.task.api.TaskInfo
import org.flowable.task.api.TaskInfoQuery
import org.flowable.task.api.history.HistoricTaskInstance
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.common.api.dtos.PagedList
import ru.briany.generated.model.Task
import ru.briany.generated.model.TaskAssignmentFilter
import ru.briany.generated.model.TaskPage
import ru.briany.generated.model.User
import java.time.Instant
import java.util.Date
import org.flowable.engine.TaskService as FlowableTaskService

/**
 * Orchestrates human-task operations over the Flowable runtime and history services.
 *
 * Runtime (`active`) tasks are served from the engine's `TaskService`, completed ones
 * from history; both are exposed as the same [Task] DTO so a link to a task keeps working
 * after it is completed. The `assignment` filter is resolved server-side against the
 * authenticated principal and its IDM groups (carried as Spring Security authorities).
 */
@Service
class TaskService(
    private val taskService: FlowableTaskService,
    private val historyService: HistoryService,
    private val repositoryService: RepositoryService,
    private val idmIdentityService: IdmIdentityService,
) {
    @Suppress("LongParameterList")
    fun listTasks(
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
    ): TaskPage {
        val completed = state == STATE_COMPLETED
        val query: TaskInfoQuery<*, *> =
            if (completed) {
                historyService.createHistoricTaskInstanceQuery().finished()
            } else {
                taskService.createTaskQuery()
            }

        applyAssignee(query, assignee, assignment)
        processDefinitionKey?.let { query.processDefinitionKey(it) }
        processInstanceId?.let { query.processInstanceId(it) }
        taskDefinitionKey?.let { query.taskDefinitionKey(it) }
        nameLike?.let { query.taskNameLikeIgnoreCase("%$it%") }
        dueBefore?.let { query.taskDueBefore(Date.from(it)) }
        dueAfter?.let { query.taskDueAfter(Date.from(it)) }
        applySort(query, pageable.sort)

        val total = query.count()
        val page = query.listPage(pageable.offset.toInt(), pageable.pageSize)
        return PagedList.buildPage(mapTasks(page, completed), total, pageable, ::TaskPage)
    }

    fun getTask(id: String): Task {
        val runtime = taskService.createTaskQuery().taskId(id).singleResult()
        if (runtime != null) return mapTasks(listOf(runtime), completed = false).first()

        val historic =
            historyService.createHistoricTaskInstanceQuery().taskId(id).singleResult()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: $id")
        return mapTasks(listOf(historic), completed = true).first()
    }

    fun claimTask(id: String): Task {
        val task = requireRuntimeTask(id)
        val principal = currentUserId()
        when (task.assignee) {
            null -> {
                taskService.claim(id, principal)
            }

            principal -> {
                Unit
            }

            // already claimed by the principal, claiming again is a no-op
            else -> {
                throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Task '$id' is already assigned to '${task.assignee}'",
                )
            }
        }
        return getTask(id)
    }

    fun unclaimTask(id: String): Task {
        requireRuntimeTask(id)
        taskService.unclaim(id)
        return getTask(id)
    }

    fun assignTask(
        id: String,
        userId: String,
    ): Task {
        if (userId.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A user id is required")
        }
        requireRuntimeTask(id)
        taskService.setAssignee(id, userId)
        return getTask(id)
    }

    fun completeTask(
        id: String,
        variables: Map<String, Any>?,
    ) {
        val runtime = taskService.createTaskQuery().taskId(id).singleResult()
        if (runtime == null) {
            val finished =
                historyService
                    .createHistoricTaskInstanceQuery()
                    .taskId(id)
                    .finished()
                    .singleResult()
            if (finished != null) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Task '$id' is already completed")
            }
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: $id")
        }
        if (variables.isNullOrEmpty()) taskService.complete(id) else taskService.complete(id, variables)
    }

    private fun requireRuntimeTask(id: String): org.flowable.task.api.Task =
        taskService.createTaskQuery().taskId(id).singleResult()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found: $id")

    private fun applyAssignee(
        query: TaskInfoQuery<*, *>,
        assignee: String?,
        assignment: TaskAssignmentFilter,
    ) {
        if (!assignee.isNullOrBlank()) {
            query.taskAssignee(assignee)
            return
        }
        when (assignment) {
            TaskAssignmentFilter.ANY -> {
                Unit
            }

            TaskAssignmentFilter.MINE -> {
                query.taskAssignee(currentUserId())
            }

            TaskAssignmentFilter.UNASSIGNED -> {
                query.taskUnassigned()
            }

            TaskAssignmentFilter.CANDIDATE -> {
                query.taskCandidateUser(currentUserId())
                val groups = currentUserGroups()
                if (groups.isNotEmpty()) query.taskCandidateGroupIn(groups)
            }
        }
    }

    private fun applySort(
        query: TaskInfoQuery<*, *>,
        sort: Sort,
    ) {
        if (sort.isUnsorted) {
            query.orderByTaskCreateTime()
            query.desc()
            return
        }
        sort.forEach { order ->
            when (order.property) {
                "createdAt" -> query.orderByTaskCreateTime()
                "dueAt" -> query.orderByTaskDueDate()
                "priority" -> query.orderByTaskPriority()
                "name" -> query.orderByTaskName()
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort: ${order.property}")
            }
            if (order.isAscending) query.asc() else query.desc()
        }
    }

    private fun mapTasks(
        tasks: List<TaskInfo>,
        completed: Boolean,
    ): List<Task> {
        val definitions = resolveDefinitions(tasks.mapNotNull { it.processDefinitionId }.toSet())
        val businessKeys = resolveBusinessKeys(tasks.mapNotNull { it.processInstanceId }.toSet())
        val state = if (completed) STATE_COMPLETED else STATE_ACTIVE
        val idmUsers =
            idmIdentityService
                .createUserQuery()
                .userIds(
                    tasks
                        .mapNotNull { it.assignee }
                        .toSet()
                        .toList(),
                ).list()
                .map { User(id = it.id, displayName = it.displayName) }
                .associateBy { it.id }

        return tasks.map { task ->
            val definition = task.processDefinitionId?.let { definitions[it] }
            TaskMapper.toDto(
                task = task,
                assigneeUser = idmUsers[task.assignee],
                state = state,
                endedAt = (task as? HistoricTaskInstance)?.endTime?.toInstant(),
                processDefinitionKey = definition?.key,
                processDefinitionName = definition?.name,
                processBusinessKey = task.processInstanceId?.let { businessKeys[it] },
            )
        }
    }

    private fun resolveDefinitions(ids: Set<String>): Map<String, ProcessDefinition> {
        if (ids.isEmpty()) return emptyMap()
        return repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionIds(ids)
            .list()
            .associateBy { it.id }
    }

    private fun resolveBusinessKeys(ids: Set<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        return historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceIds(ids)
            .list()
            .filter { it.businessKey != null }
            .associate { it.id to it.businessKey }
    }

    private fun currentUserId(): String =
        SecurityContextHolder.getContext().authentication?.name
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "No authenticated principal")

    private fun currentUserGroups(): List<String> =
        (
            SecurityContextHolder
                .getContext()
                .authentication
                ?.authorities
                ?.map { it.authority }
                ?.filter { it != ACCESS_TASK_AUTHORITY }
                ?: emptyList()
        ) as List<String>

    companion object {
        private const val STATE_ACTIVE = "active"
        private const val STATE_COMPLETED = "completed"
        private const val ACCESS_TASK_AUTHORITY = "access-task"
    }
}
