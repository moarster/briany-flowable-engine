package ru.briany.domain.modeler

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.briany.common.api.dtos.PagedList
import ru.briany.generated.model.CreateModelerAppRequest
import ru.briany.generated.model.ModelerApp
import ru.briany.generated.model.ModelerAppFile
import ru.briany.generated.model.ModelerAppFileSummary
import ru.briany.generated.model.ModelerAppPage
import ru.briany.generated.model.ModelerFileErrorSeverity
import ru.briany.generated.model.UpdateModelerAppRequest
import ru.briany.workflow.application.ApplicationMapper.toRef
import ru.briany.workflow.application.ApplicationService
import ru.briany.workflow.application.DeploymentService
import java.time.Instant

@Service
@Transactional(readOnly = true)
class ModelerAppService(
    private val appRepository: ModelerAppRepository,
    private val fileRepository: ModelerAppFileRepository,
    private val introspector: ModelerFileIntrospector,
    private val applicationService: ApplicationService,
    private val deploymentService: DeploymentService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class FileUpsertResult(
        val created: Boolean,
        val file: ModelerAppFileEntity,
    )

    fun list(
        filter: ModelerStateFilter,
        pageable: Pageable,
    ): ModelerAppPage {
        val page =
            when (filter) {
                is ModelerStateFilter.All -> appRepository.findAll(pageable)
                is ModelerStateFilter.Of -> appRepository.findByState(filter.state, pageable)
            }
        return PagedList.buildPage(
            page.content.map(ModelerAppMapper::toRef),
            page.totalElements,
            pageable,
            ::ModelerAppPage,
        )
    }

    fun get(key: String): ModelerApp = toDto(requireApp(key))

    fun listFiles(key: String): List<ModelerAppFileSummary> {
        val app = requireApp(key)
        return fileRepository.findByAppId(app.id).map { ModelerAppMapper.toSummary(app.key, it) }
    }

    fun getFile(
        key: String,
        fileKey: String,
    ): ModelerAppFile {
        val app = requireApp(key)
        return ModelerAppMapper.toFile(app.key, requireFile(app, fileKey))
    }

    fun getFileEntity(
        key: String,
        fileKey: String,
    ): ModelerAppFileEntity = requireFile(requireApp(key), fileKey)

    @Transactional
    fun create(request: CreateModelerAppRequest): ModelerApp {
        if (appRepository.existsByKeyAndTenantId(request.key, TENANT)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Modeler app already exists: ${request.key}")
        }
        val entity =
            appRepository.save(
                ModelerAppEntity(
                    key = request.key,
                    name = request.name,
                    description = request.description,
                    readme = request.readme,
                ),
            )
        log.info("Created modeler app '{}'", entity.key)
        return toDto(entity)
    }

    @Transactional
    fun update(
        key: String,
        request: UpdateModelerAppRequest,
    ): ModelerApp {
        val app = requireApp(key)
        request.name?.let { app.name = it }
        request.description?.let { app.description = it }
        request.readme?.let { app.readme = it }
        app.updatedAt = Instant.now()
        return toDto(appRepository.save(app))
    }

    @Transactional
    fun delete(key: String) {
        val app = requireApp(key)
        app.appDefinitionKey?.let { deploymentService.undeployAppByKey(it) }
        appRepository.delete(app)
        log.info("Deleted modeler app '{}'", key)
    }

    @Transactional
    fun upsertFile(
        key: String,
        resourceName: String,
        bytes: ByteArray,
    ): FileUpsertResult {
        val app = requireApp(key)
        val intro = introspect(resourceName, bytes)
        val fileKey = intro.fileKey ?: throw badRequest(intro)
        val existing = fileRepository.findByAppIdAndFileKey(app.id, fileKey)
        val entity = writeFile(app, existing, fileKey, resourceName, bytes, intro)
        return FileUpsertResult(created = existing == null, file = entity)
    }

    @Transactional
    fun updateFileContent(
        key: String,
        fileKey: String,
        resourceName: String,
        bytes: ByteArray,
    ): ModelerAppFile {
        val app = requireApp(key)
        val existing = requireFile(app, fileKey)
        val intro = introspect(resourceName, bytes)
        if (intro.fileKey != fileKey) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Derived key '${intro.fileKey}' does not match path file key '$fileKey'",
            )
        }
        val entity = writeFile(app, existing, fileKey, resourceName, bytes, intro)
        return ModelerAppMapper.toFile(app.key, entity)
    }

    @Transactional
    fun deleteFile(
        key: String,
        fileKey: String,
    ) {
        val app = requireApp(key)
        val file = requireFile(app, fileKey)
        fileRepository.delete(file)
        val files = fileRepository.findByAppId(app.id)
        recompute(app, files)
        app.updatedAt = Instant.now()
        persist(app, files)
    }

    fun toDto(app: ModelerAppEntity): ModelerApp {
        val files = fileRepository.findByAppId(app.id)
        val deployed =
            app.appDefinitionKey
                ?.takeIf { app.deploymentId != null }
                ?.let { applicationService.findApplication(it)?.toRef() }
        return ModelerAppMapper.toApp(app, files, deployed)
    }

    fun requireApp(key: String): ModelerAppEntity =
        appRepository.findByKeyAndTenantId(key, TENANT)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Modeler app not found: $key")

    private fun requireFile(
        app: ModelerAppEntity,
        fileKey: String,
    ): ModelerAppFileEntity =
        fileRepository.findByAppIdAndFileKey(app.id, fileKey)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Modeler file not found: $fileKey")

    private fun introspect(
        resourceName: String,
        bytes: ByteArray,
    ): ModelerFileIntrospector.Introspection {
        val intro = introspector.introspect(resourceName, bytes)
        if (intro.errors.any { it.severity == ModelerFileErrorSeverity.ERROR }) {
            throw badRequest(intro)
        }
        return intro
    }

    private fun writeFile(
        app: ModelerAppEntity,
        existing: ModelerAppFileEntity?,
        fileKey: String,
        resourceName: String,
        bytes: ByteArray,
        intro: ModelerFileIntrospector.Introspection,
    ): ModelerAppFileEntity {
        val hash = ModelerHashing.sha256Hex(bytes)
        val resName = resourceName.ifBlank { "$fileKey.${intro.type.value}" }
        val entity =
            existing?.apply {
                content = bytes
                contentHash = hash
                type = intro.type
                name = intro.name
                description = intro.description
                this.resourceName = resName
                errorLog = intro.errors.ifEmpty { null }
                updatedAt = Instant.now()
            } ?: ModelerAppFileEntity(
                appId = app.id,
                fileKey = fileKey,
                type = intro.type,
                resourceName = resName,
                content = bytes,
                contentHash = hash,
                name = intro.name,
                description = intro.description,
                errorLog = intro.errors.ifEmpty { null },
            )
        fileRepository.save(entity)
        val files = fileRepository.findByAppId(app.id)
        recompute(app, files)
        app.updatedAt = Instant.now()
        persist(app, files)
        return entity
    }

    private fun recompute(
        app: ModelerAppEntity,
        files: List<ModelerAppFileEntity>,
    ) {
        files.forEach { it.state = ModelerStateCalculator.fileState(app, it) }
        app.state = ModelerStateCalculator.appState(app, files)
    }

    private fun persist(
        app: ModelerAppEntity,
        files: List<ModelerAppFileEntity>,
    ) {
        fileRepository.saveAll(files)
        appRepository.save(app)
    }

    private fun badRequest(intro: ModelerFileIntrospector.Introspection): ResponseStatusException {
        val detail = intro.errors.joinToString("; ") { "${it.code}: ${it.message}" }
        return ResponseStatusException(HttpStatus.BAD_REQUEST, "File validation failed: $detail")
    }

    companion object {
        const val TENANT = ""
    }
}
