package ru.briany.integration.workflow.application

import org.flowable.app.api.AppRepositoryService
import org.flowable.cmmn.api.CmmnRepositoryService
import org.flowable.dmn.api.DmnRepositoryService
import org.flowable.eventregistry.api.EventRepositoryService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.web.server.ResponseStatusException
import ru.briany.domain.form.FormService
import ru.briany.integration.BaseServiceIT
import ru.briany.workflow.application.DeploymentService
import javax.sql.DataSource

class DeploymentServiceIT : BaseServiceIT() {
    @Autowired
    lateinit var appRepositoryService: AppRepositoryService

    @Autowired
    lateinit var deploymentService: DeploymentService

    @Autowired
    @Qualifier("brnFormService")
    lateinit var formService: FormService

    @Autowired
    lateinit var dmnRepositoryService: DmnRepositoryService

    @Autowired
    lateinit var cmmnRepositoryService: CmmnRepositoryService

    @Autowired
    lateinit var eventRepositoryService: EventRepositoryService

    @Autowired
    lateinit var dataSource: DataSource

    @AfterEach
    fun cleanupDeployments() {
        dataSource.connection.use {
            it.createStatement().execute(
                "TRUNCATE brn_form_definition RESTART IDENTITY CASCADE",
            )
        }
        eventRepositoryService.createDeploymentQuery().list().forEach {
            eventRepositoryService.deleteDeployment(it.id)
        }
        cmmnRepositoryService.createDeploymentQuery().list().forEach {
            cmmnRepositoryService.deleteDeployment(it.id, true)
        }
        dmnRepositoryService.createDeploymentQuery().list().forEach {
            dmnRepositoryService.deleteDeployment(it.id)
        }
        appRepositoryService.createDeploymentQuery().list().forEach {
            appRepositoryService.deleteDeployment(it.id, true)
        }
    }

    @Test
    fun `deploy creates queryable app definition`() {
        val result = deploymentService.deploy("test-deploy", flwFiles(), bformFiles())

        assertEquals("full-samples", result.key)
        assertEquals("Custom App", result.name)
        assertEquals(1, result.version)
        assertNotNull(result.deploymentId)

        val appDef =
            appRepositoryService
                .createAppDefinitionQuery()
                .appDefinitionKey("full-samples")
                .singleResult()
        assertNotNull(appDef)
        assertEquals(1, appDef.version)
    }

    @Test
    fun `deploy creates queryable process definition`() {
        deploymentService.deploy("test-deploy", flwFiles(), bformFiles())

        val processDef =
            repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey("deployTestProcess")
                .singleResult()
        assertNotNull(processDef)
        assertEquals("Deploy Test Process", processDef.name)
    }

    @Test
    fun `deploy creates queryable DMN decision`() {
        deploymentService.deploy("test-deploy", flwFiles(), bformFiles())

        val dmnDef =
            dmnRepositoryService
                .createDecisionQuery()
                .decisionKey("deployTestDecision")
                .singleResult()
        assertNotNull(dmnDef)
        assertEquals("Deploy Test Decision", dmnDef.name)
    }

    @Test
    fun `deploy creates queryable CMMN case definition`() {
        deploymentService.deploy("test-deploy", flwFiles(), bformFiles())

        val caseDef =
            cmmnRepositoryService
                .createCaseDefinitionQuery()
                .caseDefinitionKey("deployTestCase")
                .singleResult()
        assertNotNull(caseDef)
        assertEquals("Deploy Test Case", caseDef.name)
    }

    @Test
    fun `deploy creates queryable event definition`() {
        deploymentService.deploy("test-deploy", flwFiles(), bformFiles())

        val eventDef =
            eventRepositoryService
                .createEventDefinitionQuery()
                .eventDefinitionKey("SAMPLE_EVENT")
                .singleResult()
        assertNotNull(eventDef)
        assertEquals("Sample event", eventDef.name)
    }

    @Test
    fun `deploy creates queryable channel definition`() {
        deploymentService.deploy("test-deploy", flwFiles(), bformFiles())

        val channelDef =
            eventRepositoryService
                .createChannelDefinitionQuery()
                .channelDefinitionKey("sample-channel")
                .singleResult()
        assertNotNull(channelDef)
        assertEquals("Sample outbound channel", channelDef.name)
    }

    @Test
    fun `deploy creates queryable custom form`() {
        val result = deploymentService.deploy("test-deploy", flwFiles(), bformFiles())

        val forms = formService.getFormsByDeployment(result.deploymentId!!)
        assertEquals(1, forms.size)
        assertEquals("sampleForm", forms[0].key)
        assertEquals(1, forms[0].version)
    }

    @Test
    fun `deploy increments version on redeploy`() {
        deploymentService.deploy("test-deploy-v1", flwFiles(), bformFiles())
        val result = deploymentService.deploy("test-deploy-v2", flwFiles(), bformFiles())

        assertEquals(2, result.version)

        val appDefs =
            appRepositoryService
                .createAppDefinitionQuery()
                .appDefinitionKey("full-samples")
                .orderByAppDefinitionVersion()
                .asc()
                .list()
        assertEquals(2, appDefs.size)
        assertEquals(1, appDefs[0].version)
        assertEquals(2, appDefs[1].version)
    }

    @Test
    fun `deploy rejects archive without app file`() {
        val flwFiles = mapOf("process.bpmn20.xml" to resource("test-process.bpmn"))

        val ex =
            assertThrows(ResponseStatusException::class.java) {
                deploymentService.deploy("no-app", flwFiles, emptyMap())
            }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `deploy rejects archive with multiple app files`() {
        val flwFiles =
            mapOf(
                "first.app" to resource("custom-app.app"),
                "second.app" to resource("custom-app.app"),
            )

        val ex =
            assertThrows(ResponseStatusException::class.java) {
                deploymentService.deploy("two-apps", flwFiles, emptyMap())
            }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `deploy without bform files deploys only native resources`() {
        val result = deploymentService.deploy("native-only", flwFiles(), emptyMap())

        assertNotNull(result.deploymentId)
        val forms = formService.getFormsByDeployment(result.deploymentId!!)
        assertEquals(0, forms.size)
    }

    private fun flwFiles(): Map<String, ByteArray> =
        mapOf(
            "custom-app.app" to resource("custom-app.app"),
            "test-process.bpmn" to resource("test-process.bpmn"),
            "test-decision.dmn" to resource("test-decision.dmn"),
            "test-case.cmmn" to resource("test-case.cmmn"),
            "sample-event.event" to resource("sample-event.event"),
            "sample-channel.channel" to resource("sample-channel.channel"),
        )

    private fun bformFiles(): Map<String, ByteArray> =
        mapOf(
            "sample-form.bform" to resource("sample-form.bform"),
        )

    private fun resource(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream("deployments/app-with-all/$name")!!.readAllBytes()
}
