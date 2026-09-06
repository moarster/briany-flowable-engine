package ru.briany.workflow.application

import org.flowable.app.api.AppRepositoryService
import org.flowable.cmmn.api.CmmnRepositoryService
import org.flowable.dmn.api.DmnRepositoryService
import org.flowable.engine.RepositoryService
import org.flowable.eventregistry.api.EventRepositoryService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.briany.domain.form.FormService
import ru.briany.generated.model.Application

@Service
class DeploymentService(
    private val appRepositoryService: AppRepositoryService,
    @Lazy private val repositoryService: RepositoryService,
    @Lazy private val eventRepositoryService: EventRepositoryService,
    @Lazy private val dmnRepositoryService: DmnRepositoryService,
    @Lazy private val cmmnRepositoryService: CmmnRepositoryService,
    private val formService: FormService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Deploys native and custom files in one transaction
     */
    @Transactional
    fun deploy(
        deploymentName: String,
        flwFiles: Map<String, ByteArray>,
        bformFiles: Map<String, ByteArray>,
    ): Application {
        val appCount = flwFiles.keys.count { it.endsWith(".app") }

        val deployment =
            when (appCount) {
                0 -> {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "No .app file found in the archive")
                }

                1 -> {
                    appRepositoryService
                        .createDeployment()
                        .name(deploymentName)
                        .apply { flwFiles.forEach { (name, bytes) -> addInputStream(name, bytes.inputStream()) } }
                        .deploy()
                }

                else -> {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Multiple .app files found in the archive")
                }
            }

        log.info("Created Flowable deployment '{}' [id={}]", deployment.name, deployment.id)

        val deployedForms =
            bformFiles.map { (resourceName, bytes) ->
                formService.deployForm(resourceName, bytes, deployment.id, "").key
            }

        log.info("Deployed {} Briany forms", deployedForms.size)

        val appDefinition =
            appRepositoryService
                .createAppDefinitionQuery()
                .deploymentId(deployment.id)
                .singleResult()

        return ApplicationMapper.from(appDefinition, deployment)
    }

    @Transactional
    fun delete(deploymentId: String) {
        val processDeployment =
            repositoryService
                .createDeploymentQuery()
                .deploymentId(deploymentId)
                .singleResult()
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment not found: $deploymentId")

        val appDeploymentId = processDeployment.parentDeploymentId

        listOfNotNull(deploymentId, appDeploymentId).forEach { parentId ->
            deleteRelatedDeployments(parentId)
        }

        formService.deleteByDeploymentId(deploymentId)
        repositoryService.deleteDeployment(deploymentId, true)

        if (!appDeploymentId.isNullOrBlank()) {
            appRepositoryService.deleteDeployment(appDeploymentId, true)
        }

        log.info("Deleted deployment [id={}, appDeploymentId={}]", deploymentId, appDeploymentId)
    }

    private fun deleteRelatedDeployments(parentDeploymentId: String) {
        eventRepositoryService
            .createDeploymentQuery()
            .parentDeploymentId(parentDeploymentId)
            .list()
            .forEach { eventRepositoryService.deleteDeployment(it.id) }

        dmnRepositoryService
            .createDeploymentQuery()
            .parentDeploymentId(parentDeploymentId)
            .list()
            .forEach { dmnRepositoryService.deleteDeployment(it.id) }

        cmmnRepositoryService
            .createDeploymentQuery()
            .parentDeploymentId(parentDeploymentId)
            .list()
            .forEach { cmmnRepositoryService.deleteDeployment(it.id, true) }
    }
}
