package ru.briany.engine.api

import org.springframework.core.io.Resource
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RestController
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.api.ProcessDefinitionApi
import ru.briany.generated.model.Form
import ru.briany.generated.model.ProcessDefinition
import ru.briany.generated.model.ProcessDefinitionPage
import java.util.UUID

@RestController
class ProcessDefinitionController(
    private val processDefinitionFacade: ProcessDefinitionFacade,
    private val processDefinitionStatsService: ProcessDefinitionStatsService,
    private val processDefinitionResourceService: ProcessDefinitionResourceService,
) : ProcessDefinitionApi {
    override fun getProcess(
        key: String,
        includeStats: Boolean,
    ): ResponseEntity<ProcessDefinition> =
        ResponseEntity.ok(
            processDefinitionStatsService.getProcess(key, includeStats),
        )

    override fun getProcessDefinitionVersion(
        key: String,
        version: Int,
    ): ResponseEntity<ProcessDefinition> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitionByKey(key, version),
        )

    override fun getProcessStartForm(key: String): ResponseEntity<Form> =
        ResponseEntity.ok(
            processDefinitionResourceService.getStartForm(key),
        )

    override fun getProcessVersionXml(
        key: String,
        version: Int,
    ): ResponseEntity<Resource> = xmlResponse(processDefinitionResourceService.getXml(key, version))

    override fun getProcessXml(key: String): ResponseEntity<Resource> = xmlResponse(processDefinitionResourceService.getXml(key))

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

    override fun listProcessDefinitionsVersions(pageable: Pageable): ResponseEntity<ProcessDefinitionPage> =
        ResponseEntity.ok(
            processDefinitionFacade.getProcessDefinitions(
                version = VersionFilter.All,
                appId = null,
                key = null,
                pageable = pageable,
            ),
        )

    override fun listProcesses(
        includeStats: Boolean,
        pageable: Pageable,
    ): ResponseEntity<ProcessDefinitionPage> =
        ResponseEntity.ok(
            processDefinitionStatsService.listProcesses(includeStats, pageable),
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

    private fun xmlResponse(xml: BpmnXml): ResponseEntity<Resource> =
        ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_XML)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${xml.filename}\"")
            .body(xml.resource)
}
