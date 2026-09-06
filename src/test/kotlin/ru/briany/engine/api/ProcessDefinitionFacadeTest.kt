package ru.briany.engine.api

import org.flowable.app.api.AppRepositoryService
import org.flowable.app.api.repository.AppDefinition
import org.flowable.engine.RepositoryService
import org.flowable.engine.repository.Deployment
import org.flowable.engine.repository.DeploymentQuery
import org.flowable.engine.repository.ProcessDefinitionQuery
import org.flowable.idm.api.GroupQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.web.server.ResponseStatusException
import ru.briany.common.api.params.VersionFilter
import java.util.UUID

class ProcessDefinitionFacadeTest {
    private lateinit var repositoryService: RepositoryService
    private lateinit var appRepositoryService: AppRepositoryService
    private lateinit var facade: ProcessDefinitionFacade
    private lateinit var pdQuery: ProcessDefinitionQuery
    private lateinit var deploymentQuery: DeploymentQuery
    private lateinit var groupQuery: GroupQuery

    private val pdId = "key:1:11111111-1111-1111-1111-111111111111"

    @BeforeEach
    fun setUp() {
        pdQuery = mock(defaultAnswer = RETURNS_SELF)
        deploymentQuery = mock(defaultAnswer = RETURNS_SELF)
        repositoryService =
            mock {
                on { createProcessDefinitionQuery() } doReturn pdQuery
                on { createDeploymentQuery() } doReturn deploymentQuery
            }
        appRepositoryService = mock()
        groupQuery = mock(defaultAnswer = RETURNS_SELF)

        whenever(groupQuery.list()).thenReturn(emptyList())

        facade = ProcessDefinitionFacade(repositoryService, appRepositoryService)
    }

    private fun mockProcessDef(
        id: String = pdId,
        key: String = "my-process",
        name: String = "My Process",
        version: Int = 1,
        deploymentId: String = "deploy-1",
        hasStartForm: Boolean = false,
    ): org.flowable.engine.repository.ProcessDefinition =
        mock {
            on { getId() } doReturn id
            on { getKey() } doReturn key
            on { getName() } doReturn name
            on { getVersion() } doReturn version
            on { getDeploymentId() } doReturn deploymentId
            on { hasStartFormKey() } doReturn hasStartForm
        }

    // --- getProcessDefinitions ---

    @Test
    fun `getProcessDefinitions - returns mapped page`() {
        val def = mockProcessDef()
        whenever(pdQuery.count()).thenReturn(1L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(listOf(def))
        whenever(repositoryService.getIdentityLinksForProcessDefinition(pdId)).thenReturn(emptyList())

        val result = facade.getProcessDefinitions(VersionFilter.Latest, null, null, PageRequest.of(0, 20))

        assertEquals(1, result.data.size)
        assertEquals("my-process", result.data[0].key)
        assertEquals(1, result.totalElements)
    }

    @Test
    fun `getProcessDefinitions - VersionFilter All does not filter`() {
        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(VersionFilter.All, null, null, PageRequest.of(0, 20))

        verify(pdQuery, never()).latestVersion()
        verify(pdQuery, never()).processDefinitionVersion(any())
    }

    @Test
    fun `getProcessDefinitions - VersionFilter Latest calls latestVersion`() {
        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(VersionFilter.Latest, null, null, PageRequest.of(0, 20))

        verify(pdQuery).latestVersion()
    }

    @Test
    fun `getProcessDefinitions - VersionFilter Exact calls processDefinitionVersion`() {
        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(VersionFilter.Exact(7), null, null, PageRequest.of(0, 20))

        verify(pdQuery).processDefinitionVersion(7)
    }

    @Test
    fun `getProcessDefinitions - key filter applies processDefinitionKey`() {
        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(VersionFilter.Latest, null, "my-process", PageRequest.of(0, 20))

        verify(pdQuery).processDefinitionKey("my-process")
    }

    @Test
    fun `getProcessDefinitions - null key does not apply processDefinitionKey`() {
        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(VersionFilter.Latest, null, null, PageRequest.of(0, 20))

        verify(pdQuery, never()).processDefinitionKey(any())
    }

    @Test
    fun `getProcessDefinitions - unsupported sort throws BAD_REQUEST`() {
        val ex =
            assertThrows<ResponseStatusException> {
                facade.getProcessDefinitions(
                    VersionFilter.Latest,
                    null,
                    null,
                    PageRequest.of(0, 20, Sort.by("badField")),
                )
            }
        assertEquals(400, ex.statusCode.value())
    }

    @Test
    fun `getProcessDefinitions - default sort is by name asc`() {
        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(VersionFilter.Latest, null, null, PageRequest.of(0, 20))

        verify(pdQuery).orderByProcessDefinitionName()
        verify(pdQuery).asc()
    }

    @Test
    fun `getProcessDefinitions - explicit sort by deploymentId desc applies`() {
        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(
            VersionFilter.Latest,
            null,
            null,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "deploymentId")),
        )

        verify(pdQuery).orderByDeploymentId()
        verify(pdQuery).desc()
    }

    // --- app filtering ---

    @Test
    fun `getProcessDefinitions - filters by app id`() {
        val appId = UUID.randomUUID()
        val appDef =
            mock<AppDefinition> {
                on { id } doReturn appId.toString()
                on { deploymentId } doReturn "app-deploy-1"
            }
        val bpmnDeployment = mock<Deployment> { on { id } doReturn "bpmn-deploy-1" }
        whenever(appRepositoryService.getAppDefinition(appId.toString())).thenReturn(appDef)
        whenever(deploymentQuery.singleResult()).thenReturn(bpmnDeployment)

        whenever(pdQuery.count()).thenReturn(0L)
        whenever(pdQuery.listPage(0, 20)).thenReturn(emptyList())

        facade.getProcessDefinitions(
            VersionFilter.Latest,
            appId,
            null,
            PageRequest.of(0, 20),
        )

        verify(pdQuery).deploymentId("bpmn-deploy-1")
    }

    @Test
    fun `getProcessDefinitions - app not found bubbles up`() {
        whenever(appRepositoryService.getAppDefinition(any()))
            .thenThrow(ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "App definition not found"))

        val ex =
            assertThrows<ResponseStatusException> {
                facade.getProcessDefinitions(
                    VersionFilter.Latest,
                    UUID.randomUUID(),
                    null,
                    PageRequest.of(0, 20),
                )
            }
        assertEquals(404, ex.statusCode.value())
    }

    // --- pagination ---

    @Test
    fun `getProcessDefinitions - pagination metadata`() {
        val defs = (1..3).map { mockProcessDef(id = "pd-$it") }
        whenever(pdQuery.count()).thenReturn(13L)
        whenever(pdQuery.listPage(5, 5)).thenReturn(defs)
        defs.forEach {
            whenever(repositoryService.getIdentityLinksForProcessDefinition(it.id)).thenReturn(emptyList())
        }

        val result = facade.getProcessDefinitions(VersionFilter.Latest, null, null, PageRequest.of(1, 5))

        assertEquals(13, result.totalElements)
        assertEquals(3, result.totalPages)
        assertEquals(1, result.page)
        assertEquals(5, result.pageSize)
        assertTrue(result.hasNext)
        assertTrue(result.hasPrevious)
    }

    // --- getProcessDefinition(key, userId) ---

    @Test
    fun `getProcessDefinition by key returns latest version`() {
        val def = mockProcessDef()
        whenever(pdQuery.singleResult()).thenReturn(def)
        whenever(repositoryService.getIdentityLinksForProcessDefinition(pdId)).thenReturn(emptyList())

        val result = facade.getProcessDefinitionByKey("my-process")

        assertEquals("my-process", result.key)
        verify(pdQuery).processDefinitionKey("my-process")
        verify(pdQuery).latestVersion()
    }

    @Test
    fun `getProcessDefinition by key not found throws 404`() {
        whenever(pdQuery.singleResult()).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                facade.getProcessDefinitionByKey("missing")
            }
        assertEquals(404, ex.statusCode.value())
    }

    // --- getProcessDefinition(key, version) ---

    @Test
    fun `getProcessDefinition by key and version returns mapped result`() {
        val def = mockProcessDef(version = 4)
        whenever(pdQuery.singleResult()).thenReturn(def)
        whenever(repositoryService.getIdentityLinksForProcessDefinition(pdId)).thenReturn(emptyList())

        val result = facade.getProcessDefinitionByKey("my-process", 4)

        assertEquals("my-process", result.key)
        assertEquals(4, result.version)
        verify(pdQuery).processDefinitionKey("my-process")
        verify(pdQuery).processDefinitionVersion(4)
        verify(pdQuery, never()).latestVersion()
    }

    @Test
    fun `getProcessDefinition by key and version not found throws 404`() {
        whenever(pdQuery.singleResult()).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                facade.getProcessDefinitionByKey("my-process", 99)
            }
        assertEquals(404, ex.statusCode.value())
    }

    // --- getProcessDefinition(id: UUID) ---

    @Test
    fun `getProcessDefinition by UUID fetches directly via repositoryService`() {
        val def = mockProcessDef()
        val defId = pdId
        whenever(repositoryService.getProcessDefinition(pdId)).thenReturn(def)
        whenever(repositoryService.getIdentityLinksForProcessDefinition(pdId)).thenReturn(emptyList())

        val result = facade.getProcessDefinition(defId)

        assertEquals("my-process", result.key)
        verify(repositoryService).getProcessDefinition(pdId)
    }
}
