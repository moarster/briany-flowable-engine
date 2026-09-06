package ru.briany.engine.api

import org.flowable.app.api.AppRepositoryService
import org.flowable.app.api.repository.AppDefinition
import org.flowable.engine.HistoryService
import org.flowable.engine.RepositoryService
import org.flowable.engine.history.HistoricProcessInstance
import org.flowable.engine.history.HistoricProcessInstanceQuery
import org.flowable.engine.repository.Deployment
import org.flowable.engine.repository.DeploymentQuery
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
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
import ru.briany.common.api.params.StateFilter
import ru.briany.generated.model.ProcessInstanceState
import java.util.UUID

class ProcessInstanceFacadeTest {
    private lateinit var historyService: HistoryService
    private lateinit var repositoryService: RepositoryService
    private lateinit var appRepositoryService: AppRepositoryService
    private lateinit var facade: ProcessInstanceFacade
    private lateinit var mainQuery: HistoricProcessInstanceQuery
    private lateinit var involvedQuery: HistoricProcessInstanceQuery
    private lateinit var deploymentQuery: DeploymentQuery

    private val instanceUuid = "22222222-2222-2222-2222-222222222222"
    private val pdId = "key:1:11111111-1111-1111-1111-111111111111"

    @BeforeEach
    fun setUp() {
        mainQuery = mock(defaultAnswer = RETURNS_SELF)
        involvedQuery = mock(defaultAnswer = RETURNS_SELF)
        deploymentQuery = mock(defaultAnswer = RETURNS_SELF)

        historyService =
            mock {
                on { createHistoricProcessInstanceQuery() } doReturn mainQuery doReturn involvedQuery
            }
        appRepositoryService = mock()
        repositoryService =
            mock {
                on { createDeploymentQuery() } doReturn deploymentQuery
            }

        facade = ProcessInstanceFacade(historyService, repositoryService, appRepositoryService)
    }

    private fun mockInstance(
        id: String = instanceUuid,
        state: String = "active",
    ): HistoricProcessInstance =
        mock {
            on { getId() } doReturn id
            on { getState() } doReturn state
            on { processDefinitionId } doReturn pdId
        }

    private fun stubMainQuery(
        instances: List<HistoricProcessInstance>,
        total: Long = instances.size.toLong(),
    ) {
        whenever(mainQuery.count()).thenReturn(total)
        whenever(mainQuery.listPage(any(), any())).thenReturn(instances)
    }

    private fun stubInvolvedQuery(involvedIds: List<String>) {
        val involvedInstances = involvedIds.map { id -> mockInstance(id = id) }
        whenever(involvedQuery.list()).thenReturn(involvedInstances)
    }

    // --- getProcessInstances ---

    @Test
    fun `getProcessInstances - returns mapped page`() {
        val instance = mockInstance()
        stubMainQuery(listOf(instance))
        stubInvolvedQuery(listOf(instanceUuid))

        val result = facade.getProcessInstances(StateFilter.All, PageRequest.of(0, 20))

        assertEquals(1, result.data.size)
        assertEquals(instanceUuid, result.data[0].id)
        assertEquals(ProcessInstanceState.RUNNING, result.data[0].state)
    }

    @Test
    fun `getProcessInstances - empty result`() {
        stubMainQuery(emptyList(), 0L)

        val result = facade.getProcessInstances(StateFilter.All, PageRequest.of(0, 20))

        assertEquals(0, result.data.size)
        assertEquals(0, result.totalElements)
    }

    // --- state filtering ---

    @Test
    fun `StateFilter All does not apply state filter`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(StateFilter.All, PageRequest.of(0, 20))

        verify(mainQuery, never()).unfinished()
        verify(mainQuery, never()).finished()
    }

    @Test
    fun `StateFilter RUNNING calls unfinished`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(StateFilter.Of(ProcessInstanceState.RUNNING), PageRequest.of(0, 20))

        verify(mainQuery).unfinished()
    }

    @Test
    fun `StateFilter COMPLETED calls finished`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(StateFilter.Of(ProcessInstanceState.COMPLETED), PageRequest.of(0, 20))

        verify(mainQuery).finished()
    }

    @Test
    fun `StateFilter SUSPENDED calls unfinished`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(StateFilter.Of(ProcessInstanceState.SUSPENDED), PageRequest.of(0, 20))

        verify(mainQuery).unfinished()
    }

    @Test
    @Disabled
    fun `involvement check uses batch query with correct instance ids`() {
        val instances = listOf(mockInstance(id = "a"), mockInstance(id = "b"))
        stubMainQuery(instances)
        stubInvolvedQuery(listOf("a"))

        facade.getProcessInstances(StateFilter.All, PageRequest.of(0, 20))

        verify(involvedQuery).processInstanceIds(setOf("a", "b"))
        verify(involvedQuery).involvedUser("user1")
    }

    @Test
    fun `involvement query is skipped for empty page`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(StateFilter.All, PageRequest.of(0, 20))

        verify(involvedQuery, never()).processInstanceIds(any())
    }

    // --- sorting ---

    @Test
    fun `default sort is startTime desc`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(StateFilter.All, PageRequest.of(0, 20))

        verify(mainQuery).orderByProcessInstanceStartTime()
        verify(mainQuery).desc()
    }

    @Test
    fun `explicit sort is applied`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(
            StateFilter.All,
            PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "endTime")),
        )

        verify(mainQuery).orderByProcessInstanceEndTime()
        verify(mainQuery).asc()
    }

    @Test
    fun `unsupported sort throws BAD_REQUEST`() {
        val ex =
            assertThrows<ResponseStatusException> {
                facade.getProcessInstances(
                    StateFilter.All,
                    PageRequest.of(0, 20, Sort.by("badField")),
                )
            }
        assertEquals(400, ex.statusCode.value())
    }

    // --- pagination ---

    @Test
    fun `pagination metadata`() {
        val instances = (1..3).map { mockInstance(id = "inst-$it") }
        whenever(mainQuery.count()).thenReturn(13L)
        whenever(mainQuery.listPage(5, 5)).thenReturn(instances)
        stubInvolvedQuery(emptyList())

        val result = facade.getProcessInstances(StateFilter.All, PageRequest.of(1, 5))

        assertEquals(13, result.totalElements)
        assertEquals(3, result.totalPages)
        assertEquals(1, result.page)
        assertEquals(5, result.pageSize)
        assertTrue(result.hasNext)
        assertTrue(result.hasPrevious)
    }

    // --- getApplicationProcessInstances ---

    @Test
    fun `getApplicationProcessInstances - filters by app deployment id`() {
        val appId = UUID.randomUUID()
        val appDef = mock<AppDefinition> { on { deploymentId } doReturn "app-deploy-1" }
        val bpmnDeployment = mock<Deployment> { on { id } doReturn "bpmn-deploy-1" }
        whenever(appRepositoryService.getAppDefinition(appId.toString())).thenReturn(appDef)
        whenever(deploymentQuery.singleResult()).thenReturn(bpmnDeployment)
        stubMainQuery(emptyList(), 0L)

        facade.getApplicationProcessInstances(appId, StateFilter.All, PageRequest.of(0, 20))

        verify(mainQuery).deploymentId("bpmn-deploy-1")
    }

    @Test
    fun `getApplicationProcessInstances - app not found bubbles up`() {
        whenever(appRepositoryService.getAppDefinition(any()))
            .thenThrow(ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND))

        assertThrows<ResponseStatusException> {
            facade.getApplicationProcessInstances(
                UUID.randomUUID(),
                StateFilter.All,
                PageRequest.of(0, 20),
            )
        }
    }

    @Test
    fun `getApplicationProcessInstances - applies state filter`() {
        val appId = UUID.randomUUID()
        val appDef = mock<AppDefinition> { on { deploymentId } doReturn "app-deploy-1" }
        val bpmnDeployment = mock<Deployment> { on { id } doReturn "bpmn-deploy-1" }
        whenever(appRepositoryService.getAppDefinition(appId.toString())).thenReturn(appDef)
        whenever(deploymentQuery.singleResult()).thenReturn(bpmnDeployment)
        stubMainQuery(emptyList(), 0L)

        facade.getApplicationProcessInstances(
            appId,
            StateFilter.Of(ProcessInstanceState.COMPLETED),
            PageRequest.of(0, 20),
        )

        verify(mainQuery).finished()
        verify(mainQuery).deploymentId("bpmn-deploy-1")
    }

    // --- getProcessInstances(processDefId) ---

    @Test
    fun `getProcessInstances by processDefId uses processDefinitionId`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstances(
            pdId,
            StateFilter.All,
            PageRequest.of(0, 20),
        )

        verify(mainQuery).processDefinitionId(pdId)
    }

    // --- getProcessInstances(processDefKey, version?) ---

    @Test
    fun `getProcessInstances by key without version does not apply version filter`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstancesByProcessDefKey(
            processDefKey = "my-process",
            version = null,
            stateFilter = StateFilter.All,
            pageable = PageRequest.of(0, 20),
        )

        verify(mainQuery).processDefinitionKey("my-process")
        verify(mainQuery, never()).processDefinitionVersion(any())
    }

    @Test
    fun `getProcessInstances by key with version applies version filter`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstancesByProcessDefKey(
            processDefKey = "my-process",
            version = 2,
            stateFilter = StateFilter.All,
            pageable = PageRequest.of(0, 20),
        )

        verify(mainQuery).processDefinitionKey("my-process")
        verify(mainQuery).processDefinitionVersion(2)
    }

    @Test
    fun `getProcessInstances by key passes state filter`() {
        stubMainQuery(emptyList(), 0L)

        facade.getProcessInstancesByProcessDefKey(
            processDefKey = "my-process",
            version = null,
            stateFilter = StateFilter.Of(ProcessInstanceState.RUNNING),
            pageable = PageRequest.of(0, 20),
        )

        verify(mainQuery).processDefinitionKey("my-process")
        verify(mainQuery).unfinished()
    }
}
