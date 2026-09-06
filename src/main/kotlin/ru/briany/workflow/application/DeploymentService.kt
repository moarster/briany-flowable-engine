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
import ru.briany.generated.model.ResourceRef

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

    /**
     * Cascade-removes every deployed version of an app identified by its app definition key.
     * A mutable modeler workspace tracks only the latest version, so deletion must clear all of them.
     */
    @Transactional
    fun undeployAppByKey(appDefinitionKey: String) {
        appRepositoryService
            .createAppDefinitionQuery()
            .appDefinitionKey(appDefinitionKey)
            .list()
            .map { it.deploymentId }
            .distinct()
            .forEach(::undeployApp)
    }

    /**
     * Cascade-removes an app deployment rooted at the Flowable app deployment id. Forms are stored
     * under the app deployment id; process/dmn/cmmn/event resources are child deployments.
     */
    @Transactional
    fun undeployApp(appDeploymentId: String) {
        formService.deleteByDeploymentId(appDeploymentId)
        repositoryService
            .createDeploymentQuery()
            .parentDeploymentId(appDeploymentId)
            .list()
            .forEach { repositoryService.deleteDeployment(it.id, true) }
        dmnRepositoryService
            .createDeploymentQuery()
            .parentDeploymentId(appDeploymentId)
            .list()
            .forEach { dmnRepositoryService.deleteDeployment(it.id) }
        cmmnRepositoryService
            .createDeploymentQuery()
            .parentDeploymentId(appDeploymentId)
            .list()
            .forEach { cmmnRepositoryService.deleteDeployment(it.id, true) }
        eventRepositoryService
            .createDeploymentQuery()
            .parentDeploymentId(appDeploymentId)
            .list()
            .forEach { eventRepositoryService.deleteDeployment(it.id) }
        appRepositoryService.deleteDeployment(appDeploymentId, true)
        log.info("Undeployed app deployment [appDeploymentId={}]", appDeploymentId)
    }

    /**
     * Resolves the engine resources produced by an app deployment, keyed by their resource name.
     * Used to link modeler files to engine ids and to populate Application.deployedResources.
     */
    fun resolveDeployedResources(appDeploymentId: String): List<DeployedResource> {
        val result = mutableListOf<DeployedResource>()
        formService.getFormResourcesByDeployment(appDeploymentId).forEach { (name, ref) ->
            result += DeployedResource(name, ref, DeployedResourceKind.FORM)
        }
        repositoryService
            .createDeploymentQuery()
            .parentDeploymentId(appDeploymentId)
            .list()
            .forEach { child ->
                repositoryService
                    .createProcessDefinitionQuery()
                    .deploymentId(child.id)
                    .list()
                    .forEach { pd ->
                        result +=
                            DeployedResource(
                                pd.resourceName,
                                ResourceRef(id = pd.id, key = pd.key, name = pd.name, version = pd.version),
                                DeployedResourceKind.PROCESS,
                            )
                    }
            }
        dmnRepositoryService
            .createDeploymentQuery()
            .parentDeploymentId(appDeploymentId)
            .list()
            .forEach { child ->
                dmnRepositoryService
                    .createDecisionQuery()
                    .deploymentId(child.id)
                    .list()
                    .forEach { decision ->
                        result +=
                            DeployedResource(
                                decision.resourceName,
                                ResourceRef(id = decision.id, key = decision.key, name = decision.name, version = decision.version),
                                DeployedResourceKind.DECISION,
                            )
                    }
            }
        return result
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

    enum class DeployedResourceKind { PROCESS, DECISION, FORM }

    data class DeployedResource(
        val resourceName: String,
        val ref: ResourceRef,
        val kind: DeployedResourceKind,
    )
}
