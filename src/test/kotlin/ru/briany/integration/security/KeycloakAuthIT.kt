package ru.briany.integration.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
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
    fun `IDM sync is idempotent - two requests with same token, single user row`() {
        val token = obtainAccessToken()

        repeat(2) {
            mockMvc
                .perform(get("/api/v1/processes").header("Authorization", "Bearer $token"))
                .andExpect(status().isOk)
        }

        val user = idmIdentityService.createUserQuery().userId(TEST_USER).singleResult()
        assertNotNull(user, "User must be synced into Flowable IDM after JWT auth")
        assertEquals(
            1L,
            idmIdentityService.createUserQuery().userId(TEST_USER).count(),
            "JWT auth must be idempotent: same userId should not produce duplicate IDM rows",
        )
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
