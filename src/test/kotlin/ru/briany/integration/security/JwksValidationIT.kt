package ru.briany.integration.security

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.PlainJWT
import com.nimbusds.jwt.SignedJWT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import java.time.Instant
import java.util.Date

@ExtendWith(OutputCaptureExtension::class)
class JwksValidationIT : BaseJwksStrategyIT() {
    @Test
    fun `valid token - Authentication returned`() {
        val auth = strategy().authenticate(req(signToken()))

        assertEquals("alice", auth?.name)
    }

    @Test
    fun `alg=none rejected`() {
        val plainJwt = PlainJWT(baseClaims().build()).serialize()

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(plainJwt)) }
    }

    @Test
    fun `HS256 with RSA public key as HMAC secret rejected`() {
        val publicKeyBytes = rsaKey.toPublicKey().encoded
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.HS256)
                .keyID(KID)
                .type(JOSEObjectType.JWT)
                .build()
        val jwt = SignedJWT(header, baseClaims().build()).apply { sign(MACSigner(publicKeyBytes)) }.serialize()

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(jwt)) }
    }

    @Test
    fun `expired token rejected`() {
        val token = signToken(expiresAt = Instant.now().minusSeconds(300))

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(token)) }
    }

    @Test
    fun `nbf in future rejected`() {
        val claims =
            baseClaims()
                .notBeforeTime(Date.from(Instant.now().plusSeconds(3600)))
                .build()
        val token = signClaims(claims)

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(token)) }
    }

    @Test
    fun `wrong issuer rejected`() {
        val token = signToken(issuer = "https://evil.example.com/realms/test")

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(token)) }
    }

    @Test
    fun `wrong audience rejected`() {
        val token = signToken(audience = listOf("not-bpm"))

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(token)) }
    }

    @Test
    fun `missing audience rejected`() {
        val token = signToken(audience = null)

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(token)) }
    }

    @Test
    fun `tampered payload rejected`() {
        val original = signToken()
        val parts = original.split(".")
        val tamperedPayloadJson =
            """{"iss":"$ISSUER","aud":["$AUDIENCE"],"exp":${Instant.now().plusSeconds(3600).epochSecond},""" +
                """"iat":${Instant.now().epochSecond},"preferred_username":"injected_admin"}"""
        val tampered = "${parts[0]}.${Base64URL.encode(tamperedPayloadJson)}.${parts[2]}"

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(tampered)) }
    }

    @Test
    fun `kid pointing to key outside JWKS rejected`() {
        val attackerKey = RSAKeyGenerator(2048).keyID(ATTACKER_KID).generate()
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(ATTACKER_KID)
                .type(JOSEObjectType.JWT)
                .build()
        val jwt =
            SignedJWT(header, baseClaims().build())
                .apply { sign(RSASSASigner(attackerKey)) }
                .serialize()

        assertThrows<InvalidBearerTokenException> { strategy().authenticate(req(jwt)) }
    }

    @Test
    fun `bad token must not appear in logs`(output: CapturedOutput) {
        val securityLogger = LoggerFactory.getLogger("ru.briany.security") as Logger
        val originalLevel = securityLogger.level
        securityLogger.level = Level.TRACE
        try {
            val token = signToken(signer = foreignRsaKey)
            runCatching { strategy().authenticate(req(token)) }

            assertFalse(
                token in output.all,
                "Full JWT value must not appear in logs",
            )
        } finally {
            securityLogger.level = originalLevel
        }
    }

    companion object {
        private const val ATTACKER_KID = "attacker-kid"
    }
}
