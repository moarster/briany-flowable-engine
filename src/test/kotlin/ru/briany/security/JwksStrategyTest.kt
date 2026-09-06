package ru.briany.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import java.net.ConnectException

class JwksStrategyTest {
    private lateinit var jwtDecoder: JwtDecoder

    private val securityProperties =
        SecurityProperties(
            auth =
                SecurityProperties.Auth(
                    jwks = SecurityProperties.Jwks(usernameClaim = "preferred_username"),
                ),
        )

    @BeforeEach
    fun setup() {
        jwtDecoder = mock()
    }

    @Test
    fun `returns null when no Authorization header`() {
        assertNull(strategy().authenticate(req()))
        verify(jwtDecoder, never()).decode(any())
    }

    @Test
    fun `returns null when Authorization is not Bearer`() {
        assertNull(strategy().authenticate(req("Basic xyz")))
        verify(jwtDecoder, never()).decode(any())
    }

    @Test
    fun `Bearer prefix is case-insensitive per RFC 6750`() {
        whenever(jwtDecoder.decode("tok")).thenReturn(
            jwtWithClaims("preferred_username" to "alice"),
        )

        assertEquals("alice", strategy().authenticate(req("Bearer tok"))?.name)
        assertEquals("alice", strategy().authenticate(req("bearer tok"))?.name)
        assertEquals("alice", strategy().authenticate(req("BEARER tok"))?.name)
        assertEquals("alice", strategy().authenticate(req("BeArEr tok"))?.name)
    }

    @Test
    fun `happy path - principal, details, authorities, userSync`() {
        whenever(jwtDecoder.decode("tok")).thenReturn(
            jwtWithClaims(
                "preferred_username" to "alice",
                "realm_access" to mapOf("roles" to listOf("admin", "user")),
            ),
        )

        val auth = strategy().authenticate(req("Bearer tok"))

        assertEquals("alice", auth?.name)
        assertEquals("alice", auth?.details)
        assertEquals(
            listOf("admin", "user", "access-task"),
            auth?.authorities?.map { it.authority },
        )
    }

    @Test
    fun `JwtDecoder failure is wrapped into InvalidBearerTokenException`() {
        whenever(jwtDecoder.decode("tok")).thenThrow(BadJwtException("Jwt expired"))

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req("Bearer tok")) }
    }

    @Test
    fun `JwtDecoder failure with network cause is wrapped into AuthenticationServiceException`() {
        whenever(jwtDecoder.decode("tok"))
            .thenThrow(JwtException("Failed to fetch JWKS", ConnectException("Connection refused")))

        assertThrows<AuthenticationServiceException> { strategy().authenticate(req("Bearer tok")) }
    }

    @Test
    fun `missing username-claim throws InvalidBearerTokenException`() {
        whenever(jwtDecoder.decode("tok")).thenReturn(
            jwtWithClaims("sub" to "uuid-only"),
        )

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req("Bearer tok")) }
    }

    @Test
    fun `blank username-claim throws InvalidBearerTokenException`() {
        whenever(jwtDecoder.decode("tok")).thenReturn(
            jwtWithClaims("preferred_username" to ""),
        )

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req("Bearer tok")) }
    }

    // Principal must be a plain String, since controllers use @AuthenticationPrincipal
    // userId: String - a UserDetails/Jwt principal would break resolution.
    @Test
    fun `principal type is String`() {
        whenever(jwtDecoder.decode("tok")).thenReturn(
            jwtWithClaims("preferred_username" to "alice"),
        )

        val principal = strategy().authenticate(req("Bearer tok"))?.principal
        assertNotNull(principal)
        assertEquals(String::class.java, principal!!::class.java)
        assertEquals("alice", principal)
    }

    private fun strategy(): JwksStrategy =
        JwksStrategy(
            securityProperties = securityProperties,
            jwtDecoder = jwtDecoder,
            authoritiesMapper = KeycloakJwtAuthoritiesMapper(),
        )

    private fun req(authValue: String? = null): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            if (authValue != null) addHeader("Authorization", authValue)
        }

    private fun jwtWithClaims(vararg claims: Pair<String, Any>): Jwt {
        val builder = Jwt.withTokenValue("tok").header("alg", "RS256")
        claims.forEach { (k, v) -> builder.claim(k, v) }
        return builder.build()
    }
}
