package ru.briany.integration.domain.modeler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import ru.briany.integration.BaseOrderedControllerIT

class ModelerAppControllerIT : BaseOrderedControllerIT() {
    companion object {
        const val APP_KEY = "modelerAppIT"
        const val PROCESS_KEY = "modelerProcessA"
        const val DECISION_KEY = "modelerDecisionA"
        const val FORM_KEY = "modelerFormA"
        const val BASE = "/api/v1/modeler-apps"
    }

    private fun truncateModelerTables() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "TRUNCATE brn_modeler_app, brn_modeler_app_file, brn_form_definition RESTART IDENTITY CASCADE",
                )
            }
        }
    }

    @Test
    @Order(1)
    fun `create app then it is draft with no files`() {
        truncateModelerTables()
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post(BASE)
                    .contentType("application/json")
                    .content("""{"key":"$APP_KEY","name":"Modeler App IT"}""")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.key").value(APP_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("draft"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.files.length()").value(0))
    }

    @Test
    @Order(2)
    fun `duplicate key is rejected with 409`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post(BASE)
                    .contentType("application/json")
                    .content("""{"key":"$APP_KEY"}""")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isConflict)
    }

    @Test
    @Order(10)
    fun `upload files derives keys and app stays draft`() {
        upload("process-a.bpmn").andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.fileKey").value(PROCESS_KEY))
            .andExpect(MockMvcResultMatchers.jsonPath("$.type").value("bpmn"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("draft"))
        upload("decision-a.dmn").andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.fileKey").value(DECISION_KEY))
        upload("form-a.bform").andExpect(MockMvcResultMatchers.status().isCreated)
            .andExpect(MockMvcResultMatchers.jsonPath("$.fileKey").value(FORM_KEY))

        mockMvc
            .perform(get("$BASE/$APP_KEY/files"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(3))
    }

    @Test
    @Order(15)
    fun `draft app stats carry composition but no instances`() {
        mockMvc
            .perform(get("$BASE/$APP_KEY").param("includeStats", "true"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.processDefinitions").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.decisions").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.forms").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.instances").doesNotExist())
    }

    @Test
    @Order(11)
    fun `multi-process file is rejected with 400`() {
        upload("multi-process.bpmn").andExpect(MockMvcResultMatchers.status().isBadRequest)
    }

    @Test
    @Order(20)
    fun `deploy links engine resources and marks synced`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("$BASE/$APP_KEY/deploy")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("synced"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.appDefinitionId").isString)
            .andExpect(MockMvcResultMatchers.jsonPath("$.deploymentId").isString)
            .andExpect(MockMvcResultMatchers.jsonPath("$.deployedVersion").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.deployedApplication.key").value(APP_KEY))

        mockMvc
            .perform(get("$BASE/$APP_KEY/files/$PROCESS_KEY"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("synced"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.engineResourceId").isString)
    }

    @Test
    @Order(30)
    fun `mutating a file moves the app to ahead`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart(HttpMethod.PUT, "$BASE/$APP_KEY/files/$PROCESS_KEY")
                    .file(multipart("process-a-v2.bpmn"))
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("ahead"))

        mockMvc
            .perform(get("$BASE/$APP_KEY"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("ahead"))
    }

    @Test
    @Order(40)
    fun `redeploy returns to synced`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("$BASE/$APP_KEY/deploy")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.state").value("synced"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.deployedVersion").value(2))
    }

    @Test
    @Order(45)
    fun `deployed app stats include summed instance counts`() {
        runtimeService.startProcessInstanceByKey(PROCESS_KEY)

        mockMvc
            .perform(get("$BASE/$APP_KEY").param("includeStats", "true"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.processDefinitions").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.decisions").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.forms").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.instances.total").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats.instances.running").value(1))

        mockMvc
            .perform(get("$BASE/$APP_KEY"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.stats").doesNotExist())

        mockMvc
            .perform(get(BASE).param("includeStats", "true"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].stats.processDefinitions").value(1))
            .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].stats.instances.total").value(1))

        mockMvc
            .perform(get(BASE))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.data[0].stats").doesNotExist())
    }

    @Test
    @Order(50)
    fun `content endpoint returns raw bytes`() {
        mockMvc
            .perform(get("$BASE/$APP_KEY/files/$FORM_KEY/content"))
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith("application/json"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(FORM_KEY))
    }

    @Test
    @Order(60)
    fun `delete cascades undeploy and removes engine deployments`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .delete("$BASE/$APP_KEY")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isNoContent)

        mockMvc
            .perform(get("$BASE/$APP_KEY"))
            .andExpect(MockMvcResultMatchers.status().isNotFound)

        assertEquals(0, appRepositoryService.createDeploymentQuery().count())
        assertEquals(0, repositoryService.createDeploymentQuery().count())
    }

    private fun get(path: String) =
        MockMvcRequestBuilders
            .get(path)
            .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER))

    private fun multipart(resource: String): MockMultipartFile {
        val bytes = javaClass.classLoader.getResourceAsStream("modeler/$resource")!!.readAllBytes()
        return MockMultipartFile("file", resource, "application/octet-stream", bytes)
    }

    private fun upload(resource: String): ResultActions =
        mockMvc.perform(
            MockMvcRequestBuilders
                .multipart("$BASE/$APP_KEY/files")
                .file(multipart(resource))
                .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
        )
}
