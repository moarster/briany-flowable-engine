package ru.briany.workflow.application

import org.flowable.app.api.AppRepositoryService
import org.flowable.app.api.repository.AppDefinition
import org.flowable.app.api.repository.AppDefinitionQuery
import org.flowable.app.api.repository.AppDeployment
import org.flowable.app.api.repository.AppDeploymentBuilder
import org.flowable.cmmn.api.CmmnRepositoryService
import org.flowable.cmmn.api.repository.CmmnDeployment
import org.flowable.cmmn.api.repository.CmmnDeploymentQuery
import org.flowable.dmn.api.DmnDeployment
import org.flowable.dmn.api.DmnDeploymentQuery
import org.flowable.dmn.api.DmnRepositoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.repository.Deployment
import org.flowable.engine.repository.DeploymentBuilder
import org.flowable.engine.repository.DeploymentQuery
import org.flowable.eventregistry.api.EventDeployment
import org.flowable.eventregistry.api.EventDeploymentQuery
import org.flowable.eventregistry.api.EventRepositoryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.briany.domain.form.FormEntity
import ru.briany.domain.form.FormService
import ru.briany.generated.model.FormSchema
import java.util.Date
import java.util.UUID

class DeploymentServiceTest {
    @Mock
    private lateinit var appRepositoryService: AppRepositoryService

    @Mock
    private lateinit var repositoryService: RepositoryService

    @Mock
    private lateinit var eventRepositoryService: EventRepositoryService

    @Mock
    private lateinit var dmnRepositoryService: DmnRepositoryService

    @Mock
    private lateinit var cmmnRepositoryService: CmmnRepositoryService

    @Mock
    private lateinit var formService: FormService

    private lateinit var service: DeploymentService

    private lateinit var mockBuilder: DeploymentBuilder
    private lateinit var mockDeployment: Deployment
    private lateinit var mockDeploymentQuery: DeploymentQuery
    private lateinit var mockAppBuilder: AppDeploymentBuilder
    private lateinit var mockAppDeployment: AppDeployment

    @BeforeEach
    @Suppress("LongMethod")
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service =
            DeploymentService(
                appRepositoryService,
                repositoryService,
                eventRepositoryService,
                dmnRepositoryService,
                cmmnRepositoryService,
                formService,
            )

        mockDeployment =
            mock<Deployment> {
                on { id } doReturn "dep-123"
                on { name } doReturn "test-deploy"
                on { parentDeploymentId } doReturn "app-dep-456"
                on { deploymentTime } doReturn Date()
            }

        mockDeploymentQuery =
            mock<DeploymentQuery> {
                on { deploymentId(any()) } doReturn mock
                on { parentDeploymentId(any()) } doReturn mock
                on { orderByDeploymentTime() } doReturn mock
                on { desc() } doReturn mock
                on { singleResult() } doReturn mockDeployment
                on { list() } doReturn listOf(mockDeployment)
            }

        mockBuilder =
            mock<DeploymentBuilder> {
                on { name(any()) } doReturn mock
                on { tenantId(any()) } doReturn mock
                on { addInputStream(any(), any()) } doReturn mock
                on { deploy() } doReturn mockDeployment
            }

        mockAppDeployment =
            mock<AppDeployment> {
                on { id } doReturn "app-dep-456"
                on { name } doReturn "test-app-deploy"
                on { deploymentTime } doReturn Date()
            }

        mockAppBuilder =
            mock<AppDeploymentBuilder> {
                on { name(any()) } doReturn mock
                on { tenantId(any()) } doReturn mock
                on { addInputStream(any(), any()) } doReturn mock
                on { deploy() } doReturn mockAppDeployment
            }

        whenever(repositoryService.createDeploymentQuery()).thenReturn(mockDeploymentQuery)
        whenever(appRepositoryService.createDeployment()).thenReturn(mockAppBuilder)

        val mockAppDefinition =
            mock<AppDefinition> {
                on { id } doReturn "app-def-1"
                on { key } doReturn "my-app"
                on { name } doReturn "My App"
                on { version } doReturn 1
            }
        val mockAppDefQuery = mock<AppDefinitionQuery>(defaultAnswer = RETURNS_SELF)
        whenever(mockAppDefQuery.singleResult()).thenReturn(mockAppDefinition)
        whenever(appRepositoryService.createAppDefinitionQuery()).thenReturn(mockAppDefQuery)
    }

    // --- deploy tests ---

    @Test
    fun `deploy - non-app zip throws exception`() {
        whenever(formService.deployForm(any(), any(), any(), any()))
            .thenReturn(formEntity("formA"))

        assertThrows<ResponseStatusException> {
            service.deploy(
                deploymentName = "myDeploy",
                flwFiles = mapOf("proc.bpmn" to "<xml/>".toByteArray()),
                bformFiles = mapOf("form.bform" to "{}".toByteArray()),
            )
        }

        verifyNoInteractions(appRepositoryService)
    }

    @Test
    fun `deploy - app zip with no bform files means no form service calls`() {
        service.deploy(
            deploymentName = "myApp",
            flwFiles = mapOf("my-app.app" to "{}".toByteArray()),
            bformFiles = emptyMap(),
        )
        verifyNoInteractions(formService)
    }

    // --- delete tests ---

    @Test
    @Suppress("LongMethod")
    fun `delete - removes related event dmn cmmn and app deployments`() {
        val processEventDeployment = mock<EventDeployment> { on { id } doReturn "event-process-123" }
        val appEventDeployment = mock<EventDeployment> { on { id } doReturn "event-app-456" }
        val appDmnDeployment = mock<DmnDeployment> { on { id } doReturn "dmn-app-456" }
        val appCmmnDeployment = mock<CmmnDeployment> { on { id } doReturn "cmmn-app-456" }

        val processEventQuery =
            mock<EventDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn listOf(processEventDeployment)
            }
        val appEventQuery =
            mock<EventDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn listOf(appEventDeployment)
            }
        val processDmnQuery =
            mock<DmnDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn emptyList()
            }
        val appDmnQuery =
            mock<DmnDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn listOf(appDmnDeployment)
            }
        val processCmmnQuery =
            mock<CmmnDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn emptyList()
            }
        val appCmmnQuery =
            mock<CmmnDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn listOf(appCmmnDeployment)
            }

        whenever(eventRepositoryService.createDeploymentQuery())
            .thenReturn(processEventQuery, appEventQuery)
        whenever(dmnRepositoryService.createDeploymentQuery())
            .thenReturn(processDmnQuery, appDmnQuery)
        whenever(cmmnRepositoryService.createDeploymentQuery())
            .thenReturn(processCmmnQuery, appCmmnQuery)

        service.delete("dep-123")

        verify(mockDeploymentQuery).deploymentId("dep-123")
        verify(processEventQuery).parentDeploymentId("dep-123")
        verify(appEventQuery).parentDeploymentId("app-dep-456")
        verify(processDmnQuery).parentDeploymentId("dep-123")
        verify(appDmnQuery).parentDeploymentId("app-dep-456")
        verify(processCmmnQuery).parentDeploymentId("dep-123")
        verify(appCmmnQuery).parentDeploymentId("app-dep-456")

        inOrder(
            eventRepositoryService,
            dmnRepositoryService,
            cmmnRepositoryService,
            formService,
            repositoryService,
            appRepositoryService,
        ).run {
            verify(eventRepositoryService).deleteDeployment("event-process-123")
            verify(eventRepositoryService).deleteDeployment("event-app-456")
            verify(dmnRepositoryService).deleteDeployment("dmn-app-456")
            verify(cmmnRepositoryService).deleteDeployment("cmmn-app-456", true)
            verify(formService).deleteByDeploymentId("dep-123")
            verify(repositoryService).deleteDeployment("dep-123", true)
            verify(appRepositoryService).deleteDeployment("app-dep-456", true)
        }
    }

    @Test
    fun `delete - deployment not found throws 404`() {
        whenever(mockDeploymentQuery.singleResult()).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                service.delete("non-existent")
            }

        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
        verify(repositoryService, never()).deleteDeployment(any(), any())
        verifyNoInteractions(
            appRepositoryService,
            eventRepositoryService,
            dmnRepositoryService,
            cmmnRepositoryService,
            formService,
        )
    }

    @Test
    fun `delete - deployment without parent skips app deployment deletion`() {
        val orphanDeployment =
            mock<Deployment> {
                on { id } doReturn "orphan-123"
                on { parentDeploymentId } doReturn null
            }
        whenever(mockDeploymentQuery.singleResult()).thenReturn(orphanDeployment)

        val emptyEventQuery =
            mock<EventDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn emptyList()
            }
        val emptyDmnQuery =
            mock<DmnDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn emptyList()
            }
        val emptyCmmnQuery =
            mock<CmmnDeploymentQuery> {
                on { parentDeploymentId(any()) } doReturn mock
                on { list() } doReturn emptyList()
            }

        whenever(eventRepositoryService.createDeploymentQuery()).thenReturn(emptyEventQuery)
        whenever(dmnRepositoryService.createDeploymentQuery()).thenReturn(emptyDmnQuery)
        whenever(cmmnRepositoryService.createDeploymentQuery()).thenReturn(emptyCmmnQuery)

        service.delete("orphan-123")

        verify(repositoryService).deleteDeployment("orphan-123", true)
        verify(formService).deleteByDeploymentId("orphan-123")
        verify(appRepositoryService, never()).deleteDeployment(any(), any())
    }

    private fun formEntity(formKey: String) =
        FormEntity(
            id = UUID.randomUUID(),
            key = formKey,
            schema = FormSchema(),
            deploymentId = "dep-1",
            resourceName = "$formKey.bform",
            resourceBytes = ByteArray(0),
        )
}
