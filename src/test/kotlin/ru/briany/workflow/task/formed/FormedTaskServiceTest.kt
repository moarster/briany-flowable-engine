package ru.briany.workflow.task.formed

import org.flowable.engine.HistoryService
import org.flowable.engine.TaskService
import org.flowable.task.api.Task
import org.flowable.task.api.TaskQuery
import org.flowable.task.api.history.HistoricTaskInstance
import org.flowable.task.api.history.HistoricTaskInstanceQuery
import org.flowable.variable.api.history.HistoricVariableInstance
import org.flowable.variable.api.history.HistoricVariableInstanceQuery
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.briany.domain.form.FormService
import ru.briany.generated.model.Form
import ru.briany.generated.model.FormSchema
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.Date
import java.util.UUID

class FormedTaskServiceTest {
    @Mock
    private lateinit var taskService: TaskService

    @Mock
    private lateinit var historyService: HistoryService

    @Mock
    private lateinit var formService: FormService

    private val objectMapper = JsonMapper.builder().build()

    private lateinit var service: FormedTaskService

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = FormedTaskService(taskService, historyService, formService, objectMapper)
    }

    @Test
    fun `getFormedTask - returns runtime task with filtered variables`() {
        stubRuntimeTask("task-1", formKey = "myForm", processInstanceId = "proc-1", processDefinitionId = "procDef-1")
        whenever(formService.getFormByDeploymentWithFallback("procDef-1", "myForm"))
            .thenReturn(form(variables = listOf("approved", "amount")))
        whenever(taskService.getVariables("task-1"))
            .thenReturn(mapOf("approved" to true, "amount" to 1000, "internal" to "skip"))

        val result = service.getFormedTask("task-1")

        Assertions.assertNull(result.task.endTime)
        Assertions.assertEquals(true, result.variables["approved"]?.asBoolean())
        Assertions.assertEquals(1000, result.variables["amount"]?.asInt())
        Assertions.assertNull(result.variables["internal"])

        Mockito.verify(formService).getFormByDeploymentWithFallback("procDef-1", "myForm")
    }

    @Test
    fun `getFormedTask - falls back to historic task when runtime not found`() {
        stubNoRuntimeTask()
        val historic =
            mock<HistoricTaskInstance> {
                on { id } doReturn "task-2"
                on { formKey } doReturn "histForm"
                on { processInstanceId } doReturn "proc-2"
                on { processDefinitionId } doReturn "procDef-2"
                on { endTime } doReturn Date()
            }
        stubHistoricQuery(historic)
        whenever(formService.getFormByDeploymentWithFallback("procDef-2", "histForm"))
            .thenReturn(form(variables = listOf("score")))
        stubHistoricVarQuery(listOf(historicVar("score", 42), historicVar("other", "x")))

        val result = service.getFormedTask("task-2")

        Assertions.assertNotNull(result.task.endTime)
        Assertions.assertEquals("task-2", result.task.id)
        Assertions.assertEquals(42, result.variables["score"]?.asInt())
    }

    @Test
    fun `getFormedTask - throws 404 when task not found in runtime or history`() {
        stubNoRuntimeTask()
        val hq = Mockito.mock(HistoricTaskInstanceQuery::class.java)
        whenever(historyService.createHistoricTaskInstanceQuery()).thenReturn(hq)
        whenever(hq.taskId(ArgumentMatchers.anyString())).thenReturn(hq)
        whenever(hq.singleResult()).thenReturn(null)

        val ex = assertThrows<ResponseStatusException> { service.getFormedTask("ghost") }
        Assertions.assertEquals(HttpStatus.NOT_FOUND.value(), ex.statusCode.value())
    }

    @Test
    fun `getFormedTask - throws when task has no formKey`() {
        stubRuntimeTask("task-3", formKey = null, processInstanceId = "proc-3", processDefinitionId = "procDef-3")

        assertThrows<IllegalStateException> { service.getFormedTask("task-3") }
        Mockito.verifyNoInteractions(formService)
    }

    @Test
    fun `getFormedTask - skips variable fetch when form declares no variables`() {
        stubRuntimeTask(
            "task-4",
            formKey = "emptyForm",
            processInstanceId = "proc-4",
            processDefinitionId = "procDef-4",
        )
        whenever(formService.getFormByDeploymentWithFallback("procDef-4", "emptyForm"))
            .thenReturn(form(variables = emptyList()))

        val result = service.getFormedTask("task-4")

        Assertions.assertTrue(result.variables.isEmpty())
        Mockito.verify(taskService, Mockito.never()).getVariables(ArgumentMatchers.anyString())
    }

    @Test
    fun `getFormedTask - passes null processDefinitionId to form resolution`() {
        stubRuntimeTask("task-5", formKey = "someForm", processInstanceId = "proc-5", processDefinitionId = null)
        whenever(formService.getFormByDeploymentWithFallback(null, "someForm"))
            .thenReturn(form(variables = listOf("x")))
        whenever(taskService.getVariables("task-5")).thenReturn(mapOf("x" to 1))

        val result = service.getFormedTask("task-5")

        Assertions.assertEquals(1, result.variables["x"]?.asInt())
        Mockito.verify(formService).getFormByDeploymentWithFallback(null, "someForm")
    }

    @Test
    fun `getFormedTask - skips variable fetch when processInstanceId is null`() {
        stubRuntimeTask("task-6", formKey = "aForm", processInstanceId = null, processDefinitionId = "procDef-6")
        whenever(formService.getFormByDeploymentWithFallback("procDef-6", "aForm"))
            .thenReturn(form(variables = listOf("x")))

        val result = service.getFormedTask("task-6")

        Assertions.assertTrue(result.variables.isEmpty())
        Mockito.verify(taskService, Mockito.never()).getVariables(ArgumentMatchers.anyString())
    }

    private fun stubRuntimeTask(
        id: String,
        formKey: String?,
        processInstanceId: String?,
        processDefinitionId: String?,
    ) {
        val task =
            mock<Task> {
                on { getId() } doReturn id
                on { getFormKey() } doReturn formKey
                on { getProcessInstanceId() } doReturn processInstanceId
                on { getProcessDefinitionId() } doReturn processDefinitionId
                on { createTime } doReturn Date()
            }

        val tq = Mockito.mock(TaskQuery::class.java)
        whenever(taskService.createTaskQuery()).thenReturn(tq)
        whenever(tq.taskId(ArgumentMatchers.anyString())).thenReturn(tq)
        whenever(tq.singleResult()).thenReturn(task)
    }

    private fun stubNoRuntimeTask() {
        val tq = Mockito.mock(TaskQuery::class.java)
        whenever(taskService.createTaskQuery()).thenReturn(tq)
        whenever(tq.taskId(ArgumentMatchers.anyString())).thenReturn(tq)
        whenever(tq.singleResult()).thenReturn(null)
    }

    private fun stubHistoricQuery(result: HistoricTaskInstance) {
        val hq = Mockito.mock(HistoricTaskInstanceQuery::class.java)
        whenever(historyService.createHistoricTaskInstanceQuery()).thenReturn(hq)
        whenever(hq.taskId(ArgumentMatchers.anyString())).thenReturn(hq)
        whenever(hq.singleResult()).thenReturn(result)
    }

    private fun stubHistoricVarQuery(vars: List<HistoricVariableInstance>) {
        val vq = Mockito.mock(HistoricVariableInstanceQuery::class.java)
        whenever(historyService.createHistoricVariableInstanceQuery()).thenReturn(vq)
        whenever(vq.processInstanceId(ArgumentMatchers.anyString())).thenReturn(vq)
        whenever(vq.list()).thenReturn(vars)
    }

    private fun historicVar(
        name: String,
        value: Any,
    ): HistoricVariableInstance =
        mock<HistoricVariableInstance> {
            on { variableName } doReturn name
            on { getValue() } doReturn value
        }

    private fun form(variables: List<String>) =
        Form(
            id = UUID.randomUUID(),
            key = "test",
            name = "Test Form",
            version = 1,
            schema = FormSchema(),
            variables = variables,
            deploymentId = "dep-1",
            deployedAt = Instant.now(),
        )
}
