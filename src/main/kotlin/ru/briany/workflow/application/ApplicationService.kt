package ru.briany.workflow.application

import org.flowable.app.api.AppRepositoryService
import org.flowable.app.api.repository.AppDefinition
import org.flowable.app.api.repository.AppDefinitionQuery
import org.flowable.app.api.repository.AppDeployment
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.common.api.dtos.PagedList
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.model.Application
import ru.briany.generated.model.ApplicationPage
import ru.briany.workflow.application.ApplicationMapper.toRef
import java.util.UUID

@Service
class ApplicationService(
    private val appRepositoryService: AppRepositoryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun getApplications(
        key: String?,
        versionFilter: VersionFilter,
        pageable: Pageable,
    ): ApplicationPage {
        val query = appRepositoryService.createAppDefinitionQuery()

        if (key != null) {
            query.appDefinitionKey(key)
        }

        when (versionFilter) {
            VersionFilter.All -> {}

            VersionFilter.Latest -> {
                query.latestVersion()
            }

            is VersionFilter.Exact -> {
                query.appDefinitionVersion(versionFilter.version)
            }
        }
        query.applySort(pageable.sort)
        val total = query.count()
        val defs = query.listPage(pageable.offset.toInt(), pageable.pageSize)
        val mapped =
            defs.map { def ->
                ApplicationMapper.from(def, getAppDeployment(def)).toRef()
            }
        return PagedList.buildPage(mapped, total, pageable, ::ApplicationPage)
    }

    fun getApplication(key: String): Application {
        val definition =
            appRepositoryService
                .createAppDefinitionQuery()
                .latestVersion()
                .appDefinitionKey(key)
                .singleResult()
                ?: throw ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "App definition not found: $key",
                )
        return ApplicationMapper.from(definition, getAppDeployment(definition))
    }

    fun getApplication(
        key: String,
        version: Int,
    ): Application {
        val definition =
            appRepositoryService
                .createAppDefinitionQuery()
                .appDefinitionVersion(version)
                .appDefinitionKey(key)
                .singleResult()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "App definition not found: $key")
        return ApplicationMapper.from(definition, getAppDeployment(definition))
    }

    fun getApplication(id: UUID): Application {
        val definition = appRepositoryService.getAppDefinition(id.toString())
        return ApplicationMapper.from(definition, getAppDeployment(definition))
    }

    private fun getAppDeployment(app: AppDefinition): AppDeployment =
        appRepositoryService
            .createDeploymentQuery()
            .deploymentId(app.deploymentId)
            .singleResult()

    private fun AppDefinitionQuery.applySort(sort: Sort): AppDefinitionQuery {
        if (sort.isUnsorted) return this.orderByAppDefinitionName().asc()

        sort.forEach { order ->
            when (order.property) {
                "name" -> orderByAppDefinitionName()
                "key" -> orderByAppDefinitionKey()
                "id" -> orderByAppDefinitionId()
                "version" -> orderByAppDefinitionVersion()
                "category" -> orderByAppDefinitionCategory()
                "tenantId" -> orderByTenantId()
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort: ${order.property}")
            }
            if (order.isAscending) asc() else desc()
        }
        return this
    }
}
