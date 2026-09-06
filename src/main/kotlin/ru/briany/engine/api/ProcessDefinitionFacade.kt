package ru.briany.engine.api

import org.flowable.app.api.AppRepositoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.repository.ProcessDefinitionQuery
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.common.api.dtos.PagedList
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.model.ProcessDefinition
import ru.briany.generated.model.ProcessDefinitionPage
import java.util.UUID

@Service
class ProcessDefinitionFacade(
    private val repositoryService: RepositoryService,
    private val appRepositoryService: AppRepositoryService,
) {
    fun getProcessDefinitions(
        version: VersionFilter = VersionFilter.Latest,
        appId: UUID?,
        key: String?,
        pageable: Pageable,
    ): ProcessDefinitionPage {
        val query = repositoryService.createProcessDefinitionQuery()

        if (appId != null) {
            val appDeploymentId = appRepositoryService.getAppDefinition(appId.toString()).deploymentId
            val bpmnDeployment =
                repositoryService
                    .createDeploymentQuery()
                    .parentDeploymentId(appDeploymentId)
                    .singleResult()
            if (bpmnDeployment != null) query.deploymentId(bpmnDeployment.id)
        }

        if (key != null) query.processDefinitionKey(key)

        when (version) {
            VersionFilter.All -> {}

            VersionFilter.Latest -> {
                query.latestVersion()
            }

            is VersionFilter.Exact -> {
                query.processDefinitionVersion(version.version)
            }
        }

        query.applySort(pageable.sort)

        val total = query.count()
        val defs = query.listPage(pageable.offset.toInt(), pageable.pageSize)

        val mapped =
            defs.map { def ->
                ProcessDefinitionMapper.from(def)
            }

        return PagedList.buildPage(mapped, total, pageable, ::ProcessDefinitionPage)
    }

    fun getProcessDefinition(id: String): ProcessDefinition {
        val definition = repositoryService.getProcessDefinition(id)
        return ProcessDefinitionMapper.from(definition)
    }

    fun getProcessDefinitionByKey(key: String): ProcessDefinition {
        val definition =
            repositoryService
                .createProcessDefinitionQuery()
                .latestVersion()
                .processDefinitionKey(key)
                .singleResult()
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Process definition not found: $key",
                )

        return ProcessDefinitionMapper.from(definition)
    }

    fun getProcessDefinitionByKey(
        key: String,
        version: Int,
    ): ProcessDefinition {
        val definition =
            repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionVersion(version)
                .processDefinitionKey(key)
                .singleResult()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process definition not found: $key")

        return ProcessDefinitionMapper.from(definition)
    }

    private fun ProcessDefinitionQuery.applySort(sort: Sort): ProcessDefinitionQuery {
        if (sort.isUnsorted) return this.orderByProcessDefinitionName().asc()

        sort.forEach { order ->
            when (order.property) {
                "name" -> orderByProcessDefinitionName()
                "key" -> orderByProcessDefinitionKey()
                "id" -> orderByProcessDefinitionId()
                "version" -> orderByProcessDefinitionVersion()
                "category" -> orderByProcessDefinitionCategory()
                "tenantId" -> orderByTenantId()
                "deploymentId" -> orderByDeploymentId()
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort: ${order.property}")
            }
            if (order.isAscending) asc() else desc()
        }
        return this
    }
}
