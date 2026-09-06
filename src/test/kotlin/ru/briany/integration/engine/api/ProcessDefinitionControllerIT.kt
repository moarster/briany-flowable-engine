package ru.briany.integration.engine.api

import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.briany.integration.BaseOrderedControllerIT

class ProcessDefinitionControllerIT : BaseOrderedControllerIT() {
    companion object {
        const val ONLY_NATIVE_APP_ZIP = "deployments/only-native-app.zip"
    }

    // Phase 0: empty state

    @Test
    @Order(1)
    fun `listProcesses returns empty page when nothing deployed`() {
        mockMvc
            .perform(get("/api/v1/processes").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data").isArray)
            .andExpect(jsonPath("$.data.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))
    }

    @Test
    @Order(2)
    fun `listProcessDefinitions returns empty page when nothing deployed`() {
        mockMvc
            .perform(get("/api/v1/process-definitions").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(0))
    }

    // Phase 1: one deploy (v1)

    @Test
    @Order(10)
    fun `listProcesses returns definitions after deploy`() {
        deployAppZip(ONLY_NATIVE_APP_ZIP)

        mockMvc
            .perform(get("/api/v1/processes").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.data[*].key").isArray)
            .andExpect(jsonPath("$.data[*].deploymentId").isArray)
    }

    @Test
    @Order(11)
    fun `getProcess by key returns latest version`() {
        mockMvc
            .perform(get("/api/v1/processes/loop_simple").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("loop_simple"))
            .andExpect(jsonPath("$.name").value("Loop simple"))
            .andExpect(jsonPath("$.version").value(1))
            .andExpect(jsonPath("$.id").isString)
            .andExpect(jsonPath("$.deploymentId").isString)
    }

    @Test
    @Order(12)
    fun `getProcess returns startableByCurrentUser true when no candidate starters`() {
        mockMvc
            .perform(get("/api/v1/processes/loop_simple").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.startableByCurrentUser").value(true))
    }

    @Test
    @Order(13)
    fun `getProcessDefinition by UUID returns definition`() {
        val def =
            repositoryService
                .createProcessDefinitionQuery()
                .processDefinitionKey("loop_simple")
                .latestVersion()
                .singleResult()

        mockMvc
            .perform(get("/api/v1/process-definitions/${def.id}").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(def.id))
            .andExpect(jsonPath("$.key").value("loop_simple"))
            .andExpect(jsonPath("$.version").value(1))
    }

    @Test
    @Order(14)
    fun `getProcessDefinitionVersion returns specified version`() {
        mockMvc
            .perform(get("/api/v1/processes/loop_simple/versions/1").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("loop_simple"))
            .andExpect(jsonPath("$.version").value(1))
    }

    @Test
    @Order(15)
    fun `listProcessDefinitionVersions returns single version`() {
        mockMvc
            .perform(get("/api/v1/processes/loop_simple/versions").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].version").value(1))
    }

    @Test
    @Order(16)
    fun `listProcessDefinitions filters by key param`() {
        mockMvc
            .perform(
                get("/api/v1/process-definitions")
                    .param("key", "loop_simple")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].key").value("loop_simple"))
    }

    @Test
    @Order(17)
    fun `listApplicationProcessDefinitions returns scoped definitions`() {
        val appDef =
            appRepositoryService
                .createAppDefinitionQuery()
                .appDefinitionKey("native-samples")
                .latestVersion()
                .singleResult()

        mockMvc
            .perform(get("/api/v1/application-definitions/${appDef.id}/process-definitions").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.totalElements").value(3))
    }

    // Phase 2: second deploy (v2)

    @Test
    @Order(20)
    fun `listProcesses returns only latest versions`() {
        deployAppZip(ONLY_NATIVE_APP_ZIP)

        mockMvc
            .perform(get("/api/v1/processes").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[0].version").value(2))
    }

    @Test
    @Order(21)
    fun `listProcesses supports pagination`() {
        mockMvc
            .perform(
                get("/api/v1/processes")
                    .param("page", "0")
                    .param("size", "2")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.hasNext").value(true))
            .andExpect(jsonPath("$.hasPrevious").value(false))

        mockMvc
            .perform(
                get("/api/v1/processes")
                    .param("page", "1")
                    .param("size", "2")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.hasNext").value(false))
            .andExpect(jsonPath("$.hasPrevious").value(true))
    }

    @Test
    @Order(22)
    fun `listProcessDefinitionVersions returns all versions after second deploy`() {
        mockMvc
            .perform(get("/api/v1/processes/loop_simple/versions").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.totalElements").value(2))
    }

    @Test
    @Order(23)
    fun `getProcessDefinitionVersion returns historical v1 after second deploy`() {
        mockMvc
            .perform(get("/api/v1/processes/loop_simple/versions/1").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(1))

        mockMvc
            .perform(get("/api/v1/processes/loop_simple/versions/2").with(testUserAuth()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").value(2))
    }

    @Test
    @Order(24)
    fun `listProcessDefinitions with version=all returns every version`() {
        mockMvc
            .perform(
                get("/api/v1/process-definitions")
                    .param("key", "loop_simple")
                    .param("version", "all")
                    .with(testUserAuth()),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(2))
    }

    @Test
    @Order(25)
    fun `listProcesses propagates userId from security context`() {
        mockMvc
            .perform(get("/api/v1/processes").with(authAs("differentuser")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.length()").value(3))
    }

    // Phase 3: state-independent tests

    @Test
    @Order(30)
    fun `getProcess by unknown key returns 404`() {
        mockMvc
            .perform(get("/api/v1/processes/nonexistent").with(testUserAuth()))
            .andExpect(status().isNotFound)
    }

    @Test
    @Order(31)
    fun `getProcessDefinitionVersion with unknown version returns 404`() {
        mockMvc
            .perform(get("/api/v1/processes/loop_simple/versions/9999").with(testUserAuth()))
            .andExpect(status().isNotFound)
    }
}
