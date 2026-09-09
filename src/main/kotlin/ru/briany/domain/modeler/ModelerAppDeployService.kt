package ru.briany.domain.modeler

import org.flowable.bpmn.converter.BpmnXMLConverter
import org.flowable.common.engine.api.FlowableException
import org.flowable.common.engine.api.io.InputStreamProvider
import org.flowable.validation.ProcessValidator
import org.flowable.validation.ValidationError as EngineValidationError
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.briany.common.api.ApiProblemException
import ru.briany.generated.model.Application
import ru.briany.generated.model.ModelerApp
import ru.briany.generated.model.ModelerAppState
import ru.briany.generated.model.ModelerFileError
import ru.briany.generated.model.ModelerFileErrorSeverity
import ru.briany.generated.model.ModelerFileType
import ru.briany.generated.model.ValidationError
import ru.briany.workflow.application.DeploymentService
import tools.jackson.databind.ObjectMapper
import java.time.Instant

/**
 * Wraps DeploymentService: bundles workspace files, synthesizes the .app descriptor, deploys to
 * the engine, links engine resource ids, and maintains the deployed-version reference.
 */
@Service
class ModelerAppDeployService(
    private val appRepository: ModelerAppRepository,
    private val fileRepository: ModelerAppFileRepository,
    private val deploymentService: DeploymentService,
    private val modelerAppService: ModelerAppService,
    private val objectMapper: ObjectMapper,
    private val brianyProcessValidator: ProcessValidator,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val bpmnConverter = BpmnXMLConverter()

    @Transactional
    fun deploy(key: String): ModelerApp {
        val app = modelerAppService.requireApp(key)
        val files = fileRepository.findByAppId(app.id)
        guardDeployable(key, files)
        preValidate(files)
        val application = runDeploy(app, files)
        linkResources(application, files)
        markDeployed(app, files, application)
        log.info("Deployed modeler app '{}' [deploymentId={}]", key, application.deploymentId)
        return modelerAppService.toDto(app)
    }

    @Transactional
    fun undeploy(key: String): ModelerApp {
        val app = modelerAppService.requireApp(key)
        val appDefinitionKey =
            app.appDefinitionKey.takeIf { app.deploymentId != null }
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Modeler app is not deployed: $key")
        deploymentService.undeployAppByKey(appDefinitionKey)
        val files = fileRepository.findByAppId(app.id)
        clearDeployment(app, files)
        log.info("Undeployed modeler app '{}'", key)
        return modelerAppService.toDto(app)
    }

    private fun guardDeployable(
        key: String,
        files: List<ModelerAppFileEntity>,
    ) {
        if (files.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Modeler app has no files to deploy: $key")
        }
        val blocking =
            files.filter { file -> file.errorLog.orEmpty().any { it.severity == ModelerFileErrorSeverity.ERROR } }
        if (blocking.isNotEmpty()) {
            val detail = blocking.joinToString(", ") { it.fileKey }
            throw ResponseStatusException(HttpStatus.CONFLICT, "Modeler app has files with errors: $detail")
        }
    }

    private fun runDeploy(
        app: ModelerAppEntity,
        files: List<ModelerAppFileEntity>,
    ): Application {
        val appBytes =
            objectMapper.writeValueAsBytes(mapOf("key" to app.key, "name" to (app.name ?: app.key)))
        val flwFiles = linkedMapOf<String, ByteArray>("${app.key}$APP_SUFFIX" to appBytes)
        val bformFiles = linkedMapOf<String, ByteArray>()
        files.forEach { file ->
            if (file.type == ModelerFileType.BFORM) {
                bformFiles[file.resourceName] = file.content
            } else {
                flwFiles[file.resourceName] = file.content
            }
        }
        return try {
            deploymentService.deploy(app.key, flwFiles, bformFiles)
        } catch (ex: FlowableException) {
            log.warn("Engine deployment failed for modeler app '{}': {}", app.key, ex.message)
            val message = ex.message ?: "Engine deployment failed"
            throw ApiProblemException(
                status = HttpStatus.CONFLICT,
                detail = message,
                code = "DEPLOY_FAILED",
                errors = listOf(ValidationError(field = app.key, message = message)),
            )
        }
    }

    /**
     * Runs the engine's own [ProcessValidator] over each BPMN source before the deploy call, so a
     * disallowed construct surfaces as a structured [ApiProblemException] the client can map onto
     * files and diagram elements, rather than a flat 409 message. Each error's `field` is the
     * modeler file key, suffixed with `#<activityId>` when the engine reports one.
     */
    private fun preValidate(files: List<ModelerAppFileEntity>) {
        val errors =
            files
                .filter { it.type == ModelerFileType.BPMN }
                .flatMap { file -> validateFile(file) }
        if (errors.isNotEmpty()) {
            throw ApiProblemException(
                status = HttpStatus.CONFLICT,
                detail = "Deployment validation failed",
                code = "DEPLOY_VALIDATION_FAILED",
                errors = errors,
            )
        }
    }

    private fun validateFile(file: ModelerAppFileEntity): List<ValidationError> {
        val provider = InputStreamProvider { file.content.inputStream() }
        val model = bpmnConverter.convertToBpmnModel(provider, false, false)
        return brianyProcessValidator.validate(model).map { toValidationError(file, it) }
    }

    private fun toValidationError(
        file: ModelerAppFileEntity,
        error: EngineValidationError,
    ): ValidationError {
        val field = error.activityId?.takeIf { it.isNotBlank() }?.let { "${file.fileKey}#$it" } ?: file.fileKey
        val message = error.defaultDescription ?: error.problem ?: "Validation error"
        return ValidationError(field = field, message = message)
    }

    private fun linkResources(
        application: Application,
        files: List<ModelerAppFileEntity>,
    ) {
        val deploymentId = application.deploymentId ?: return
        val byName =
            deploymentService
                .resolveDeployedResources(deploymentId)
                .associate { it.resourceName to it.ref.id }
        files.forEach { file -> file.engineResourceId = byName[file.resourceName] }
    }

    private fun markDeployed(
        app: ModelerAppEntity,
        files: List<ModelerAppFileEntity>,
        application: Application,
    ) {
        app.appDefinitionId = application.id
        app.appDefinitionKey = application.key
        app.deploymentId = application.deploymentId
        app.deployedVersion = application.version
        app.deployedContentHash = ModelerStateCalculator.aggregate(files)
        app.deployedAt = Instant.now()
        app.updatedAt = Instant.now()
        files.forEach { file ->
            file.deployedHash = file.contentHash
            file.errorLog = file.errorLog?.filter(::isWarning)?.ifEmpty { null }
            file.state = ModelerAppState.SYNCED
        }
        app.state = ModelerAppState.SYNCED
        fileRepository.saveAll(files)
        appRepository.save(app)
    }

    private fun clearDeployment(
        app: ModelerAppEntity,
        files: List<ModelerAppFileEntity>,
    ) {
        app.appDefinitionId = null
        app.appDefinitionKey = null
        app.deploymentId = null
        app.deployedVersion = null
        app.deployedContentHash = null
        app.deployedAt = null
        app.state = ModelerAppState.DRAFT
        app.updatedAt = Instant.now()
        files.forEach { file ->
            file.deployedHash = null
            file.engineResourceId = null
            file.state = ModelerAppState.DRAFT
        }
        fileRepository.saveAll(files)
        appRepository.save(app)
    }

    private fun isWarning(error: ModelerFileError): Boolean = error.severity != ModelerFileErrorSeverity.ERROR

    companion object {
        const val APP_SUFFIX = ".app"
    }
}
