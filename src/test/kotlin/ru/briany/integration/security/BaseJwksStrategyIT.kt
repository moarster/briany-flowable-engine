package ru.briany.integration.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import org.springframework.mock.web.MockHttpServletRequest
import ru.briany.integration.BaseServiceIT
import ru.briany.security.JwksStrategy
import ru.briany.security.KeycloakJwtAuthoritiesMapper
import ru.briany.security.SecurityConfig
import ru.briany.security.SecurityProperties
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

// JwksStrategy assembled manually to avoid forcing auth.mode and breaking context cache
abstract class BaseJwksStrategyIT : BaseServiceIT() {
    protected fun strategy(): JwksStrategy {
        val props =
            SecurityProperties(
                auth =
                    SecurityProperties.Auth(
                        jwks =
                            SecurityProperties.Jwks(
                                issuerUri = ISSUER,
                                jwkSetUri = "http://localhost:${jwksServer.address.port}/.well-known/jwks.json",
                                audiences = listOf(AUDIENCE),
                                usernameClaim = "preferred_username",
                            ),
                    ),
            )
        return JwksStrategy(
            props,
            SecurityConfig(props).jwtDecoder(),
            KeycloakJwtAuthoritiesMapper(),
        )
    }

    protected fun signToken(
        signer: RSAKey = rsaKey,
        kid: String = KID,
        issuer: String? = ISSUER,
        audience: List<String>? = listOf(AUDIENCE),
        expiresAt: Instant = Instant.now().plusSeconds(60),
        username: String? = "alice",
    ): String = JwtTestTokens.sign(signer, kid, issuer, audience, expiresAt, username)

    protected fun signClaims(
        claims: JWTClaimsSet,
        signer: RSAKey = rsaKey,
        kid: String = KID,
    ): String = JwtTestTokens.signClaims(claims, signer, kid)

    protected fun baseClaims(): JWTClaimsSet.Builder =
        JWTClaimsSet
            .Builder()
            .issuer(ISSUER)
            .audience(AUDIENCE)
            .expirationTime(Date.from(Instant.now().plusSeconds(60)))
            .issueTime(Date.from(Instant.now()))
            .claim("preferred_username", "alice")

    protected fun req(token: String): MockHttpServletRequest =
        MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }

    companion object {
        const val KID: String = "test-kid"
        const val ISSUER: String = "https://test-idp.local/realms/test"
        const val AUDIENCE: String = "bpm"

        val rsaKey: RSAKey = RSAKeyGenerator(2048).keyID(KID).generate()

        val foreignRsaKey: RSAKey = RSAKeyGenerator(2048).keyID(KID).generate()

        @JvmStatic
        val jwksServer: HttpServer =
            HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
                createContext("/.well-known/jwks.json") { exchange ->
                    val body = JWKSet(rsaKey.toPublicJWK()).toString().toByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.use { it.write(body) }
                }
                start()
            }
    }
}

object JwtTestTokens {
    fun sign(
        signer: RSAKey,
        kid: String,
        issuer: String?,
        audience: List<String>?,
        expiresAt: Instant = Instant.now().plusSeconds(60),
        username: String? = "alice",
        notBefore: Instant? = null,
    ): String {
        val claims =
            JWTClaimsSet
                .Builder()
                .apply {
                    issuer?.let { issuer(it) }
                    audience?.let { audience(it) }
                    expirationTime(Date.from(expiresAt))
                    issueTime(Date.from(Instant.now()))
                    notBefore?.let { notBeforeTime(Date.from(it)) }
                    username?.let { claim("preferred_username", it) }
                }.build()
        return signClaims(claims, signer, kid)
    }

    fun signClaims(
        claims: JWTClaimsSet,
        signer: RSAKey,
        kid: String,
    ): String {
        val header =
            JWSHeader
                .Builder(JWSAlgorithm.RS256)
                .keyID(kid)
                .type(JOSEObjectType.JWT)
                .build()
        return SignedJWT(header, claims).apply { sign(RSASSASigner(signer)) }.serialize()
    }
}
