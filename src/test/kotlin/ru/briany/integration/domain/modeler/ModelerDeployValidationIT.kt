package ru.briany.integration.domain.modeler

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.briany.integration.BaseControllerIT

class ModelerDeployValidationIT : BaseControllerIT() {
    companion object {
        const val APP_KEY = "scriptValidationApp"
        const val BASE = "/api/v1/modeler-apps"
    }

    @BeforeEach
    fun truncateModelerTables() {
        dataSource.connection.use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute(
                    "TRUNCATE brn_modeler_app, brn_modeler_app_file, brn_form_definition RESTART IDENTITY CASCADE",
                )
            }
        }
    }

    @Test
    fun `deploying a process with a scriptTask is rejected with a structured 409`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post(BASE)
                    .contentType("application/json")
                    .content("""{"key":"$APP_KEY","name":"Script Validation App"}""")
                    .with(user(TEST_USER)),
            ).andExpect(status().isCreated)

        val bytes = javaClass.classLoader.getResourceAsStream("modeler/script-process.bpmn")!!.readAllBytes()
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .multipart("$BASE/$APP_KEY/files")
                    .file(MockMultipartFile("file", "script-process.bpmn", "application/octet-stream", bytes))
                    .with(user(TEST_USER)),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("$BASE/$APP_KEY/deploy")
                    .with(user(TEST_USER)),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("DEPLOY_VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors[0].field").value(startsWith("scriptProcess")))
            .andExpect(jsonPath("$.errors[0].message").isNotEmpty)
    }
}
