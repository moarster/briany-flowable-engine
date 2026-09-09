package ru.briany.integration.workflow.application

import org.hamcrest.Matchers
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import ru.briany.integration.BaseControllerIT

class PlatformControllerIT : BaseControllerIT() {
    @Test
    fun `engine-capabilities reports the whitelist without any script or shell flag`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/engine-capabilities")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.allowedActivityTypes", Matchers.hasItem("user_task")))
            .andExpect(
                MockMvcResultMatchers.jsonPath(
                    "$.allowedActivityTypes",
                    Matchers.not(Matchers.hasItem("script_task")),
                ),
            ).andExpect(MockMvcResultMatchers.jsonPath("$.allowedDelegateBeans[0]").value("kvDelegate"))
            .andExpect(MockMvcResultMatchers.jsonPath("$.allowedClassPrefixes[0]").value("ru.briany."))
            .andExpect(MockMvcResultMatchers.jsonPath("$.allowedClassPrefixes[1]").value("org.flowable."))
            .andExpect(MockMvcResultMatchers.jsonPath("$.activityWhitelistEnabled").value(true))
            .andExpect(MockMvcResultMatchers.jsonPath("$.flowableVersion").isString)
            .andExpect(MockMvcResultMatchers.jsonPath("$.allowedScriptFormats").doesNotExist())
    }

    @Test
    fun `bpmn-palette returns an empty elements array when no descriptors are on the classpath`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/bpmn-palette")
                    .with(SecurityMockMvcRequestPostProcessors.user(TEST_USER)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.jsonPath("$.elements").isArray)
            .andExpect(MockMvcResultMatchers.jsonPath("$.elements.length()").value(0))
    }
}
