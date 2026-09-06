package ru.briany.engine.api

import org.flowable.app.api.AppRepositoryService
import org.flowable.engine.HistoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.history.HistoricProcessInstanceQuery
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.common.api.dtos.PagedList
import ru.briany.common.api.params.StateFilter
import ru.briany.generated.model.ProcessInstancePage
import ru.briany.generated.model.ProcessInstanceState
import java.util.UUID

@Service
class ProcessInstanceFacade(
    private val historyService: HistoryService,
    private val repositoryService: RepositoryService,
    private val appRepositoryService: AppRepositoryService,
) {
    fun getProcessInstances(
        stateFilter: StateFilter = StateFilter.All,
        pageable: Pageable,
    ): ProcessInstancePage = executeQuery(stateFilter, pageable) {}

    fun getApplicationProcessInstances(
        appId: UUID,
        stateFilter: StateFilter,
        pageable: Pageable,
    ): ProcessInstancePage {
        val appDeploymentId = appRepositoryService.getAppDefinition(appId.toString()).deploymentId
        val bpmnDeployment =
            repositoryService
                .createDeploymentQuery()
                .parentDeploymentId(appDeploymentId)
                .singleResult()

        return executeQuery(stateFilter, pageable) { query ->
            if (bpmnDeployment != null) query.deploymentId(bpmnDeployment.id)
        }
    }

    fun getProcessInstances(
        processDefId: String,
        stateFilter: StateFilter,
        pageable: Pageable,
    ): ProcessInstancePage =
        executeQuery(stateFilter, pageable) { query ->
            query.processDefinitionId(processDefId)
        }

    fun getProcessInstancesByProcessDefKey(
        processDefKey: String,
        version: Int?,
        stateFilter: StateFilter,
        pageable: Pageable,
    ): ProcessInstancePage =
        executeQuery(stateFilter, pageable) { query ->
            if (version != null) query.processDefinitionVersion(version)
            query.processDefinitionKey(processDefKey)
        }

    private fun executeQuery(
        stateFilter: StateFilter,
        pageable: Pageable,
        customize: (HistoricProcessInstanceQuery) -> Unit,
    ): ProcessInstancePage {
        val query = historyService.createHistoricProcessInstanceQuery()

        when (stateFilter) {
            StateFilter.All -> {}

            is StateFilter.Of -> {
                when (stateFilter.state) {
                    ProcessInstanceState.RUNNING -> query.unfinished()
                    ProcessInstanceState.COMPLETED -> query.finished()
                    ProcessInstanceState.SUSPENDED -> query.unfinished() // suspended are unfinished
                }
            }
        }

        customize(query)
        query.applySort(pageable.sort)

        val total = query.count()
        val instances = query.listPage(pageable.offset.toInt(), pageable.pageSize)

        val involvedIds = resolveInvolvedIds(instances.map { it.id })

        val mapped =
            instances.map { instance ->
                ProcessInstanceMapper.from(
                    instance = instance,
                )
            }

        return PagedList.buildPage(mapped, total, pageable, ::ProcessInstancePage)
    }

    private fun resolveInvolvedIds(instanceIds: List<String>): Set<String> {
        if (instanceIds.isEmpty()) return emptySet()
        return historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceIds(instanceIds.toSet())
            .list()
            .map { it.id }
            .toSet()
    }

    private fun HistoricProcessInstanceQuery.applySort(sort: Sort): HistoricProcessInstanceQuery {
        if (sort.isUnsorted) return this.orderByProcessInstanceStartTime().desc()

        sort.forEach { order ->
            when (order.property) {
                "startTime" -> orderByProcessInstanceStartTime()
                "endTime" -> orderByProcessInstanceEndTime()
                "duration" -> orderByProcessInstanceDuration()
                "id" -> orderByProcessInstanceId()
                "businessKey" -> orderByProcessInstanceBusinessKey()
                "processDefinitionId" -> orderByProcessDefinitionId()
                "tenantId" -> orderByTenantId()
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort: ${order.property}")
            }
            if (order.isAscending) asc() else desc()
        }
        return this
    }
}
