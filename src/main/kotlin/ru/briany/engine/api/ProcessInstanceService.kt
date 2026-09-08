package ru.briany.engine.api

import org.flowable.engine.HistoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.RuntimeService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.generated.model.ActivityInstance
import ru.briany.generated.model.ProcessInstance
import ru.briany.generated.model.StartProcessInstanceRequest
import ru.briany.generated.model.Variable

/**
 * Command and detail operations on a single process instance: starting, cancelling,
 * deleting, and reading its activity/variable history. These carry more logic than the
 * paginated listings on [ProcessInstanceFacade] (running-vs-historic distinctions,
 * variable-scope resolution, mapping), so they live in a dedicated service.
 */
@Service
class ProcessInstanceService(
    private val runtimeService: RuntimeService,
    private val historyService: HistoryService,
    private val repositoryService: RepositoryService,
    private val processInstanceFacade: ProcessInstanceFacade,
) {
    fun start(
        key: String,
        request: StartProcessInstanceRequest?,
    ): ProcessInstance {
        val builder =
            runtimeService
                .createProcessInstanceBuilder()
                .processDefinitionId(resolveDefinitionId(key, request?.version))
        request?.businessKey?.let { builder.businessKey(it) }
        request?.name?.let { builder.name(it) }
        request?.variables?.let { builder.variables(it) }

        val instance = builder.start()
        return processInstanceFacade.getProcessInstance(instance.processInstanceId)
    }

    fun cancel(
        id: String,
        reason: String?,
    ) {
        val running = runtimeService.createProcessInstanceQuery().processInstanceId(id).singleResult()
        if (running == null) {
            if (historicExists(id)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Process instance is not running: $id")
            }
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process instance not found: $id")
        }
        runtimeService.deleteProcessInstance(id, reason)
    }

    fun delete(id: String) {
        if (isRunning(id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot delete a running process instance: $id")
        }
        if (!historicExists(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process instance not found: $id")
        }
        historyService.deleteHistoricProcessInstance(id)
    }

    fun activities(id: String): List<ActivityInstance> {
        requireInstance(id)
        return historyService
            .createHistoricActivityInstanceQuery()
            .processInstanceId(id)
            .orderByHistoricActivityInstanceStartTime()
            .asc()
            .list()
            .map(ActivityInstanceMapper::from)
    }

    fun variables(id: String): List<Variable> {
        requireInstance(id)
        return historyService
            .createHistoricVariableInstanceQuery()
            .processInstanceId(id)
            .list()
            .map(VariableMapper::from)
    }

    private fun resolveDefinitionId(
        key: String,
        version: Int?,
    ): String {
        val query = repositoryService.createProcessDefinitionQuery().processDefinitionKey(key)
        if (version != null) query.processDefinitionVersion(version) else query.latestVersion()
        return query.singleResult()?.id
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process definition not found: $key")
    }

    private fun requireInstance(id: String) {
        if (!historicExists(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process instance not found: $id")
        }
    }

    private fun isRunning(id: String): Boolean =
        runtimeService.createProcessInstanceQuery().processInstanceId(id).count() > 0

    private fun historicExists(id: String): Boolean =
        historyService.createHistoricProcessInstanceQuery().processInstanceId(id).count() > 0
}
