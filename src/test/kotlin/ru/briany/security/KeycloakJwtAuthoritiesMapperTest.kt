package ru.briany.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt

class KeycloakJwtAuthoritiesMapperTest {
    private val mapper = KeycloakJwtAuthoritiesMapper()

    @Test
    fun `realm_access roles plus access-task`() {
        val jwt =
            jwtWithClaims(
                "realm_access" to mapOf("roles" to listOf("admin", "user")),
            )

        val authorities = mapper.map(jwt).map { it.authority }

        assertEquals(listOf("admin", "user", "access-task"), authorities)
    }

    @Test
    fun `only access-task when realm_access missing`() {
        val jwt = jwtWithClaims("preferred_username" to "alice")

        val authorities = mapper.map(jwt).map { it.authority }

        assertEquals(listOf("access-task"), authorities)
    }

    @Test
    fun `only access-task when realm_access has empty roles list`() {
        val jwt =
            jwtWithClaims(
                "realm_access" to mapOf("roles" to emptyList<String>()),
            )

        val authorities = mapper.map(jwt).map { it.authority }

        assertEquals(listOf("access-task"), authorities)
    }

    @Test
    fun `only access-task when realm_access has no roles key`() {
        val jwt =
            jwtWithClaims(
                "realm_access" to mapOf("clientRoles" to listOf("admin")),
            )

        val authorities = mapper.map(jwt).map { it.authority }

        assertEquals(listOf("access-task"), authorities)
    }

    private fun jwtWithClaims(vararg claims: Pair<String, Any>): Jwt {
        val builder = Jwt.withTokenValue("tok").header("alg", "RS256")
        claims.forEach { (k, v) -> builder.claim(k, v) }
        return builder.build()
    }
}
