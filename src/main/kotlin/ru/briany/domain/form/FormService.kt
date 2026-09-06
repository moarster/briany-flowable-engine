package ru.briany.domain.form

import org.flowable.engine.RepositoryService
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.briany.common.api.dtos.PagedList
import ru.briany.common.api.params.IdOrKey
import ru.briany.common.api.params.VersionFilter
import ru.briany.generated.model.Form
import ru.briany.generated.model.FormSchema
import ru.briany.generated.model.FormSummary
import ru.briany.generated.model.FormSummaryPage
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Service("brnFormService")
@Transactional(readOnly = true)
class FormService(
    private val formRepository: FormRepository,
    private val objectMapper: ObjectMapper,
    private val repositoryService: RepositoryService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun deployForm(
        resourceName: String,
        rawBytes: ByteArray,
        deploymentId: String,
        tenantId: String,
    ): FormEntity {
        val json = String(rawBytes, Charsets.UTF_8)
        val schemaDto = objectMapper.readValue(json, FormSchema::class.java)

        val formKey =
            schemaDto.id
                ?: throw IllegalArgumentException("Form '$resourceName' missing 'id' field")

        val latestVersion =
            formRepository
                .findTopByKeyAndTenantIdOrderByVersionDesc(formKey, tenantId)
                ?.version ?: 0

        val variables = FormVariableExtractor.extract(schemaDto)

        val entity =
            formRepository.save(
                FormEntity(
                    key = formKey,
                    name = schemaDto.components?.firstOrNull()?.label ?: formKey,
                    version = latestVersion + 1,
                    schema = schemaDto,
                    deploymentId = deploymentId,
                    resourceName = resourceName,
                    resourceBytes = rawBytes,
                    variableBindings = objectMapper.writeValueAsString(variables),
                    tenantId = tenantId,
                ),
            )

        log.info("Deployed custom form '{}' v{} [deploymentId={}]", formKey, entity.version, deploymentId)
        return entity
    }

    @Transactional
    fun deleteByDeploymentId(deploymentId: String): Int {
        val definitions = formRepository.findByDeploymentId(deploymentId)
        if (definitions.isNotEmpty()) {
            formRepository.deleteByDeploymentId(deploymentId)
            log.info("Cascade-deleted {} custom form(s) for deploymentId={}", definitions.size, deploymentId)
        }
        return definitions.size
    }

    fun listForms(
        versionFilter: VersionFilter,
        pageable: Pageable,
    ): FormSummaryPage =
        when (versionFilter) {
            VersionFilter.All -> {
                val page = formRepository.findAll(pageable)
                PagedList.buildPage(
                    page.content.map { it.toListItem() },
                    page.totalElements,
                    pageable,
                    ::FormSummaryPage,
                )
            }

            else -> {
                val forms = formRepository.findLatest()
                PagedList.buildPage(forms.map { it.toListItem() }, forms.size.toLong(), pageable, ::FormSummaryPage)
            }
        }

    fun getForm(idOrKey: IdOrKey): Form =
        when (idOrKey) {
            is IdOrKey.Id -> {
                formRepository.findTopByIdOrderByVersionDesc(UUID.fromString(idOrKey.value))?.toResponse()
                    ?: throw NoSuchElementException("Form not found: $idOrKey")
            }

            is IdOrKey.Key -> {
                formRepository.findTopByKeyOrderByVersionDesc(idOrKey.value)?.toResponse()
                    ?: throw NoSuchElementException("Form not found: $idOrKey")
            }
        }

    fun getFormByDeployment(
        deploymentId: String,
        formKey: String,
    ): Form? = formRepository.findByDeploymentIdAndKey(deploymentId, formKey)?.toResponse()

    fun getFormsByDeployment(deploymentId: String): List<Form> =
        formRepository.findByDeploymentId(deploymentId).map {
            it.toResponse()
        }

    fun listVersions(
        formKey: String,
        tenantId: String = "",
    ): List<Form> =
        formRepository
            .findByKeyAndTenantId(formKey, tenantId)
            .sortedByDescending { it.version }
            .map { it.toResponse() }

    fun getFormByDeploymentWithFallback(
        processDefinitionId: String?,
        formKey: String,
    ): Form {
        if (processDefinitionId != null) {
            val deploymentId = repositoryService.getProcessDefinition(processDefinitionId).deploymentId
            val form = getFormByDeployment(deploymentId, formKey)
            if (form != null) return form
        }
        return getForm(IdOrKey.Key(formKey))
    }

    @Suppress("UNCHECKED_CAST")
    private fun FormEntity.toResponse() =
        Form(
            id = id,
            key = key,
            name = name,
            version = version,
            schema = schema,
            variables =
                variableBindings?.let { objectMapper.readValue(it, List::class.java) as List<String> }
                    ?: emptyList(),
            deploymentId = deploymentId,
            deployedAt = deployedAt,
        )

    private fun FormEntity.toListItem() =
        FormSummary(
            id = id,
            formKey = key,
            name = name,
            version = version,
            deploymentId = deploymentId,
        )
}
