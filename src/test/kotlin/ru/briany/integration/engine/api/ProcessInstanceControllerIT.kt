package ru.briany.integration.engine.api

import org.flowable.engine.TaskService
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.briany.integration.BaseOrderedControllerIT

class ProcessInstanceControllerIT : BaseOrderedControllerIT() {
    companion object {
        const val ONLY_NATIVE_APP_ZIP = "deployments/only-native-app.zip"
        const val INSTANT_END = "processes/instant-end.bpmn"
        const val USER_TASK = "processes/user-task.bpmn"
    }

    @Autowired
    lateinit var taskService: TaskService

    // Phase 0: empty state

    @Test
    @Order(1)
    fun `listProcessInstances returns empty page when no instances`() {
        mockMvc
            .perform(get("/api/v1/process-instances").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    // Phase 1: deploy both processes, start 1 running + 1 completed

    @Test
    @Order(10)
    fun `listProcessInstances filters by running state`() {
        deployProcess(INSTANT_END)
        deployProcess(USER_TASK)
        runtimeService.startProcessInstanceByKey("instantEnd")
        runtimeService.startProcessInstanceByKey("userTaskProcess")

        mockMvc
            .perform(
                get("/api/v1/process-instances")
                    .param("state", "running")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].state").value("running"))
            .andExpect(jsonPath("$.data[0].processDefinition.key").value("userTaskProcess"))
    }

    @Test
    @Order(11)
    fun `listProcessInstances filters by completed state`() {
        mockMvc
            .perform(
                get("/api/v1/process-instances")
                    .param("state", "completed")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].state").value("completed"))
            .andExpect(jsonPath("$.data[0].processDefinition.key").value("instantEnd"))
    }

    @Test
    @Order(12)
    fun `listProcessInstances without state filter returns all`() {
        mockMvc
            .perform(get("/api/v1/process-instances").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    // Phase 2: clear instances, test single running instance details

    @Test
    @Order(20)
    fun `listProcessInstances returns running instances`() {
        deleteAllProcessInstances()
        runtimeService.startProcessInstanceByKey("userTaskProcess")

        mockMvc
            .perform(get("/api/v1/process-instances").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.data[0].state").value("running"))
            .andExpect(jsonPath("$.data[0].id").isString)
            .andExpect(jsonPath("$.data[0].processDefinition.key").value("userTaskProcess"))
    }

    // Phase 3: clear instances, test single completed instance details

    @Test
    @Order(30)
    fun `listProcessInstances returns completed instances`() {
        deleteAllProcessInstances()
        runtimeService.startProcessInstanceByKey("instantEnd")

        mockMvc
            .perform(get("/api/v1/process-instances").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].state").value("completed"))
            .andExpect(jsonPath("$.data[0].endTime").isNotEmpty)
    }

    // Phase 4: clear instances, test pagination with 3 running instances

    @Test
    @Order(40)
    fun `listProcessInstances supports pagination`() {
        deleteAllProcessInstances()
        repeat(3) { runtimeService.startProcessInstanceByKey("userTaskProcess") }

        mockMvc
            .perform(
                get("/api/v1/process-instances")
                    .param("page", "0")
                    .param("size", "2")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(true))

        mockMvc
            .perform(
                get("/api/v1/process-instances")
                    .param("page", "1")
                    .param("size", "2")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.hasPrevious").value(true))
    }

    // Phase 5: clear instances, test definition-scoped queries

    @Test
    @Order(50)
    fun `listProcessProcessInstances by key scoped to definition`() {
        deleteAllProcessInstances()
        runtimeService.startProcessInstanceByKey("instantEnd")
        runtimeService.startProcessInstanceByKey("userTaskProcess")
        runtimeService.startProcessInstanceByKey("userTaskProcess")

        mockMvc
            .perform(get("/api/v1/processes/userTaskProcess/process-instances").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.data[0].processDefinition.key").value("userTaskProcess"))
    }

    @Test
    @Order(52)
    fun `listProcessDefinitionVersionInstances filters by key and version`() {
        val version =
            repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey("userTaskProcess")
                .latestVersion()
                .singleResult()
                .version

        mockMvc
            .perform(
                get("/api/v1/processes/userTaskProcess/versions/$version/process-instances")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].processDefinition.key").value("userTaskProcess"))
            .andExpect(jsonPath("$.data[0].processDefinition.version").value(version))
    }

    // Phase 6: clear instances, test definition-scoped state filter

    @Test
    @Order(60)
    fun `listProcessProcessInstances with state filter`() {
        deleteAllProcessInstances()
        runtimeService.startProcessInstanceByKey("instantEnd")
        runtimeService.startProcessInstanceByKey("instantEnd")

        mockMvc
            .perform(
                get("/api/v1/processes/instantEnd/process-instances")
                    .param("state", "running")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))

        mockMvc
            .perform(
                get("/api/v1/processes/instantEnd/process-instances")
                    .param("state", "completed")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
    }
}
