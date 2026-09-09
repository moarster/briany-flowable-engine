package ru.briany.integration.security

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class KeycloakAuthIT : BaseKeycloakIT() {
    @Test
    fun `valid Bearer token - protected endpoint returns 200`() {
        val token = obtainAccessToken()

        mockMvc
            .perform(get("/api/v1/processes").header("Authorization", "Bearer $token"))
            .andExpect(status().isOk)
    }

    @Test
    fun `Basic auth header but no Bearer with strategies=jwks - 401 (flowable strategy not loaded)`() {
        val basic =
            java.util.Base64
                .getEncoder()
                .encodeToString("someone:secret".toByteArray())
        mockMvc
            .perform(get("/api/v1/processes").header("Authorization", "Basic $basic"))
            .andExpect(status().isUnauthorized)
    }
}
