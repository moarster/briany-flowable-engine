package ru.briany.integration.security

import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.briany.integration.BaseControllerIT
import java.net.InetSocketAddress

@TestPropertySource(
    properties = [
        "briany.security.auth.strategy=jwks",
        "briany.security.auth.jwks.issuer-uri=https://test-idp.local/realms/test",
        "briany.security.auth.jwks.audiences=bpm",
    ],
)
class JwksDowntimeIT : BaseControllerIT() {
    @Test
    fun `JWKS cache survives downtime AND uncached kid - 503 with Retry-After`() {
        // Phase A. Fetch-Cache-Down-HitCache

        // 1. First request is ok, fetch successful
        val tokenBefore = signValidToken()
        mockMvc
            .perform(get("/api/v1/processes").header("Authorization", "Bearer $tokenBefore"))
            .andExpect(status().isOk)
        val fetchesAfterFirst = jwksFetchCount
        check(fetchesAfterFirst >= 1) { "JWKS must have been fetched at least once on first request" }

        // 2. Imitate JWKS down
        jwksHandlerBroken = true

        // 3. Second request, no fetch, retrieve from cache
        val tokenAfter = signValidToken()
        mockMvc
            .perform(get("/api/v1/processes").header("Authorization", "Bearer $tokenAfter"))
            .andExpect(status().isOk)

        // 4. Fetch counter like after first request
        check(jwksFetchCount == fetchesAfterFirst) {
            "Expected cache hit (no new JWKS fetch), but counter went $fetchesAfterFirst → $jwksFetchCount"
        }

        // Phase B
        // 5. Stop for good
        jwksServer.stop(0)

        // 6. New token is uncached - have to call
        val token =
            JwtTestTokens.sign(
                signer = rsaKey,
                kid = "uncached-kid",
                issuer = ISSUER,
                audience = listOf(AUDIENCE),
            )
        mockMvc
            .perform(get("/api/v1/processes").header("Authorization", "Bearer $token"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Retry-After", "15"))
    }

    private fun signValidToken(): String =
        JwtTestTokens.sign(
            signer = rsaKey,
            kid = KID,
            issuer = ISSUER,
            audience = listOf(AUDIENCE),
        )

    companion object {
        private const val KID = "test-kid"
        private const val ISSUER = "https://test-idp.local/realms/test"
        private const val AUDIENCE = "bpm"

        private val rsaKey: RSAKey = RSAKeyGenerator(2048).keyID(KID).generate()

        @JvmStatic
        var jwksFetchCount: Int = 0

        @JvmStatic
        var jwksHandlerBroken: Boolean = false

        @AfterAll
        @JvmStatic
        fun resetState() {
            jwksHandlerBroken = false
            jwksFetchCount = 0
        }

        @JvmStatic
        private val jwksServer: HttpServer =
            HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
                createContext("/.well-known/jwks.json") { exchange ->
                    jwksFetchCount++
                    if (jwksHandlerBroken) {
                        val body = "internal error".toByteArray()
                        exchange.sendResponseHeaders(500, body.size.toLong())
                        exchange.responseBody.use { it.write(body) }
                        return@createContext
                    }
                    val body = JWKSet(rsaKey.toPublicJWK()).toString().toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }

        @DynamicPropertySource
        @JvmStatic
        fun overrideJwks(registry: DynamicPropertyRegistry) {
            registry.add("briany.security.auth.jwks.jwk-set-uri") {
                "http://localhost:${jwksServer.address.port}/.well-known/jwks.json"
            }
        }
    }
}
