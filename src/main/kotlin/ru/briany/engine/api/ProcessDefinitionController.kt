package ru.briany.engine.api

import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.api.ProcessDefinitionApi
import ru.briany.generated.model.ProcessDefinition
import ru.briany.generated.model.ProcessDefinitionPage
import java.util.UUID

@RestController
class ProcessDefinitionController(
    private val processDefinitionFacade: ProcessDefinitionFacade,
) : ProcessDefinitionApi {
    override fun getProcess(key: String): ResponseEntity<ProcessDefinition> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitionByKey(key),
        )

    override fun getProcessDefinitionVersion(
        key: String,
        version: Int,
    ): ResponseEntity<ProcessDefinition> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitionByKey(key, version),
        )

    override fun listProcessDefinitionVersions(
        key: String,
        pageable: Pageable,
    ): ResponseEntity<ProcessDefinitionPage> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitions(
                version = VersionFilter.All,
                appId = null,
                key = key,
                pageable = pageable,
            ),
        )

    override fun listProcesses(pageable: Pageable): ResponseEntity<ProcessDefinitionPage> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitions(
                version = VersionFilter.Latest,
                appId = null,
                key = null,
                pageable = pageable,
            ),
        )

    fun listApplicationProcessDefinitions(
        id: UUID,
        key: String?,
        version: String,
        pageable: Pageable,
    ): ResponseEntity<ProcessDefinitionPage> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitions(
                version = VersionFilter.parse(version),
                appId = id,
                key = key,
                pageable = pageable,
            ),
        )

    fun listProcessDefinitions(
        key: String?,
        version: String,
        pageable: Pageable,
    ): ResponseEntity<ProcessDefinitionPage> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitions(
                version = VersionFilter.parse(version),
                appId = null,
                key = key,
                pageable = pageable,
            ),
        )
}
