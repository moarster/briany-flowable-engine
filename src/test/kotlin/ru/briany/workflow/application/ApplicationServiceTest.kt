package ru.briany.workflow.application

import org.flowable.app.api.AppRepositoryService
import org.flowable.app.api.repository.AppDefinition
import org.flowable.app.api.repository.AppDefinitionQuery
import org.flowable.app.api.repository.AppDeployment
import org.flowable.app.api.repository.AppDeploymentQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import org.mockito.Mockito.RETURNS_SELF
import org.mockito.Mockito.never
import org.mockito.MockitoAnnotations
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

class ApplicationServiceTest {
    @Mock
    private lateinit var appRepositoryService: AppRepositoryService

    private lateinit var service: ApplicationService
    private lateinit var query: AppDefinitionQuery
    private lateinit var mockAppDeploymentQuery: AppDeploymentQuery
    private lateinit var deploymentService: DeploymentService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        query = mock(defaultAnswer = RETURNS_SELF)

        mockAppDeploymentQuery = mock(defaultAnswer = RETURNS_SELF)

        appRepositoryService =
            mock {
                on { createAppDefinitionQuery() } doReturn query
                on { createDeploymentQuery() } doReturn mockAppDeploymentQuery
            }

        deploymentService =
            mock {
                on { resolveDeployedResources(any()) } doReturn emptyList()
            }

        service =
            ApplicationService(appRepositoryService, deploymentService)
    }

    private fun mockAppDefinition(
        id: String = "app-1",
        key: String = "my-app",
        name: String = "My App",
        version: Int = 1,
    ): AppDefinition =
        mock {
            on { getId() } doReturn id
            on { getKey() } doReturn key
            on { getName() } doReturn name
            on { getVersion() } doReturn version
            on { deploymentId } doReturn "deploy-1"
        }

    private fun mockAppDeployment(): AppDeployment = mock<AppDeployment>()

    // --- getApplications ---

    @Test
    fun `getApplications - returns page with mapped results`() {
        val def = mockAppDefinition()
        whenever(query.count()).thenReturn(1L)
        whenever(query.listPage(0, 20)).thenReturn(listOf(def))
        whenever(mockAppDeploymentQuery.singleResult()).thenReturn(mockAppDeployment())

        val result = service.getApplications(null, VersionFilter.Latest, PageRequest.of(0, 20))

        assertEquals(1, result.data.size)
        assertEquals("my-app", result.data[0].key)
        assertEquals(1, result.totalElements)
        assertEquals(0, result.page)
        assertEquals(20, result.pageSize)
    }

    @Test
    fun `getApplications - empty result`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        val result = service.getApplications(null, VersionFilter.Latest, PageRequest.of(0, 20))

        assertEquals(0, result.data.size)
        assertEquals(0, result.totalElements)
    }

    @Test
    fun `getApplications - VersionFilter Latest calls latestVersion`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        service.getApplications(null, VersionFilter.Latest, PageRequest.of(0, 20))

        verify(query).latestVersion()
    }

    @Test
    fun `getApplications - VersionFilter Exact calls appDefinitionVersion`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        service.getApplications(null, VersionFilter.Exact(3), PageRequest.of(0, 20))

        verify(query).appDefinitionVersion(3)
    }

    @Test
    fun `getApplications - VersionFilter All does not filter`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        service.getApplications(null, VersionFilter.All, PageRequest.of(0, 20))

        verify(query, never()).latestVersion()
        verify(query, never()).appDefinitionVersion(any())
    }

    @Test
    fun `getApplications - key filter applies appDefinitionKey`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        service.getApplications("my-app", VersionFilter.All, PageRequest.of(0, 20))

        verify(query).appDefinitionKey("my-app")
    }

    @Test
    fun `getApplications - null key does not apply appDefinitionKey`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        service.getApplications(null, VersionFilter.Latest, PageRequest.of(0, 20))

        verify(query, never()).appDefinitionKey(any())
    }

    @Test
    fun `getApplications - pagination calculates correctly`() {
        val defs = (1..5).map { mockAppDefinition(id = "app-$it", key = "app-$it") }
        whenever(query.count()).thenReturn(12L)
        whenever(query.listPage(5, 5)).thenReturn(defs)
        defs.forEach { def ->
            whenever(mockAppDeploymentQuery.singleResult()).thenReturn(mockAppDeployment())
        }

        val result = service.getApplications(null, VersionFilter.Latest, PageRequest.of(1, 5))

        assertEquals(5, result.data.size)
        assertEquals(12, result.totalElements)
        assertEquals(3, result.totalPages)
        assertEquals(1, result.page)
        assertTrue(result.hasNext)
        assertTrue(result.hasPrevious)
    }

    @Test
    fun `getApplications - first page has no previous`() {
        val def = mockAppDefinition()
        whenever(query.count()).thenReturn(10L)
        whenever(query.listPage(0, 5)).thenReturn(listOf(def))
        whenever(mockAppDeploymentQuery.singleResult()).thenReturn(mockAppDeployment())

        val result = service.getApplications(null, VersionFilter.Latest, PageRequest.of(0, 5))

        assertFalse(result.hasPrevious)
        assertTrue(result.hasNext)
    }

    @Test
    fun `getApplications - last page has no next`() {
        val def = mockAppDefinition()
        whenever(query.count()).thenReturn(3L)
        whenever(query.listPage(0, 5)).thenReturn(listOf(def))
        whenever(mockAppDeploymentQuery.singleResult()).thenReturn(mockAppDeployment())

        val result = service.getApplications(null, VersionFilter.Latest, PageRequest.of(0, 5))

        assertFalse(result.hasNext)
        assertFalse(result.hasPrevious)
    }

    @Test
    fun `getApplications - default sort is by name asc`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        service.getApplications(null, VersionFilter.Latest, PageRequest.of(0, 20))

        verify(query).orderByAppDefinitionName()
        verify(query).asc()
    }

    @Test
    fun `getApplications - explicit sort by version desc applies`() {
        whenever(query.count()).thenReturn(0L)
        whenever(query.listPage(0, 20)).thenReturn(emptyList())

        service.getApplications(
            null,
            VersionFilter.Latest,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "version")),
        )

        verify(query).orderByAppDefinitionVersion()
        verify(query).desc()
    }

    @Test
    fun `getApplications - unsupported sort field throws BAD_REQUEST`() {
        val pageable = PageRequest.of(0, 20, Sort.by("unknownField"))

        val ex =
            assertThrows<ResponseStatusException> {
                service.getApplications(null, VersionFilter.Latest, pageable)
            }
        assertEquals(400, ex.statusCode.value())
    }

    // --- getApplication(key) ---

    @Test
    fun `getApplication by key returns mapped latest version`() {
        val def = mockAppDefinition()
        whenever(query.singleResult()).thenReturn(def)
        whenever(mockAppDeploymentQuery.singleResult()).thenReturn(mockAppDeployment())

        val result = service.getApplication("my-app")

        assertEquals("my-app", result.key)
        verify(query).appDefinitionKey("my-app")
        verify(query).latestVersion()
    }

    @Test
    fun `getApplication by key not found throws 404`() {
        whenever(query.singleResult()).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                service.getApplication("missing")
            }
        assertEquals(404, ex.statusCode.value())
    }

    // --- getApplication(key, version) ---

    @Test
    fun `getApplication by key and version returns mapped result`() {
        val def = mockAppDefinition(version = 3)
        whenever(query.singleResult()).thenReturn(def)
        whenever(mockAppDeploymentQuery.singleResult()).thenReturn(mockAppDeployment())

        val result = service.getApplication("my-app", 3)

        assertEquals("my-app", result.key)
        assertEquals(3, result.version)
        verify(query).appDefinitionKey("my-app")
        verify(query).appDefinitionVersion(3)
        verify(query, never()).latestVersion()
    }

    @Test
    fun `getApplication by key and version not found throws 404`() {
        whenever(query.singleResult()).thenReturn(null)

        val ex =
            assertThrows<ResponseStatusException> {
                service.getApplication("my-app", 99)
            }
        assertEquals(404, ex.statusCode.value())
    }

    // --- getApplication(id: UUID) ---

    @Test
    fun `getApplication by UUID returns mapped result`() {
        val uuid = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val def = mockAppDefinition(id = uuid.toString())
        whenever(appRepositoryService.getAppDefinition(uuid.toString())).thenReturn(def)
        whenever(mockAppDeploymentQuery.singleResult()).thenReturn(mockAppDeployment())

        val result = service.getApplication(uuid)

        assertEquals(uuid.toString(), result.id)
    }
}
