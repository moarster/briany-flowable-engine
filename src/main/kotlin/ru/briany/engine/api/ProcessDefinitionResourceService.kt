package ru.briany.engine.api

import org.flowable.bpmn.model.StartEvent
import org.flowable.engine.RepositoryService
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import ru.briany.domain.form.FormService
import ru.briany.generated.model.Form
import org.flowable.engine.repository.ProcessDefinition as FlowableProcessDefinition

/** A deployed BPMN resource paired with the file name to serve it under. */
data class BpmnXml(
    val resource: Resource,
    val filename: String,
)

/**
 * Serves the deployed artifacts that back a process definition: the raw BPMN XML (what a
 * viewer renders so it shows exactly what the engine executes) and the start form its
 * start event's `flowable:formKey` names, resolved to a deployed schema.
 */
@Service
class ProcessDefinitionResourceService(
    private val repositoryService: RepositoryService,
    private val formService: FormService,
) {
    fun getXml(key: String): BpmnXml = readResource(latest(key))

    fun getXml(
        key: String,
        version: Int,
    ): BpmnXml = readResource(byVersion(key, version))

    fun getStartForm(key: String): Form {
        val definition = latest(key)
        val startFormKey =
            resolveStartFormKey(definition.id)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process definition has no start form: $key")
        return try {
            formService.getFormByDeploymentWithFallback(definition.id, startFormKey)
        } catch (e: NoSuchElementException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Start form '$startFormKey' not found for process: $key", e)
        }
    }

    private fun resolveStartFormKey(definitionId: String): String? =
        repositoryService
            .getBpmnModel(definitionId)
            .processes
            .asSequence()
            .flatMap { it.findFlowElementsOfType(StartEvent::class.java).asSequence() }
            .mapNotNull { it.formKey }
            .firstOrNull { it.isNotBlank() }

    private fun readResource(definition: FlowableProcessDefinition): BpmnXml {
        val stream =
            repositoryService.getResourceAsStream(definition.deploymentId, definition.resourceName)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No BPMN resource for process: ${definition.key}")
        return BpmnXml(InputStreamResource(stream), definition.resourceName)
    }

    private fun latest(key: String): FlowableProcessDefinition =
        repositoryService
            .createProcessDefinitionQuery()
            .latestVersion()
            .processDefinitionKey(key)
            .singleResult()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process definition not found: $key")

    private fun byVersion(
        key: String,
        version: Int,
    ): FlowableProcessDefinition =
        repositoryService
            .createProcessDefinitionQuery()
            .processDefinitionKey(key)
            .processDefinitionVersion(version)
            .singleResult()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Process definition not found: $key v$version")
}
