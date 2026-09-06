package ru.briany.security

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException
import org.springframework.stereotype.Component
import java.io.IOException

/**
 * Authenticates Bearer JWTs via JWKS.
 *
 * We depend on `spring-boot-starter-oauth2-resource-server` but never call
 * `oauth2ResourceServer().jwt()`. We only use its low-level pieces: `JwtDecoder`
 * (`NimbusJwtDecoder`) for parsing/signature verification/key caching+rotation, and the
 * `OAuth2TokenValidator` chain for `exp`/`nbf`/`iss`/`aud` checks. We deliberately skip
 * `BearerTokenAuthenticationFilter` (we have our own strategy composition in
 * [AuthenticationFilter]; two parallel filters would race on `SecurityContextHolder`),
 * the `http.oauth2ResourceServer { jwt() }` DSL, and `JwtAuthenticationProvider`.
 *
 * Building this flow by hand instead of enabling the stock Resource Server is
 * deliberate: with multiple strategies enabled, "JWKS endpoint is down -> fall back to
 * the next strategy" must be distinguishable from "token is invalid -> 401, no
 * fallback," and the standard `ProviderManager` chain doesn't offer that distinction.
 *
 * Flow: applies only if the request carries `Authorization: Bearer <token>`, else
 * `null`. Decodes and validates the token via [JwtDecoder] (signature, `exp`/`nbf`,
 * `iss`, `aud`), maps claims (`principal` from `securityProperties.jwks.usernameClaim`,
 * `authorities` via [KeycloakJwtAuthoritiesMapper]), then syncs the user into Flowable
 * IDM.
 *
 * [JwtDecoder] error mapping: a network cause anywhere in the `cause` chain
 * ([IOException] and subclasses - connection refused, timeout, unknown host) becomes
 * [AuthenticationServiceException] (transient; falls back to the next strategy in
 * `both` mode). Any other [JwtException] (bad signature, expired `exp`, wrong
 * `iss`/`aud`, missing/blank `username-claim`) becomes [InvalidBearerTokenException]
 * (401 + `WWW-Authenticate: Bearer error="invalid_token"`, no fallback).
 */
@Component
@ConditionalOnProperty(name = ["briany.security.auth.strategy"], havingValue = "jwks")
class JwksStrategy(
    private val securityProperties: SecurityProperties,
    private val jwtDecoder: JwtDecoder,
    private val authoritiesMapper: KeycloakJwtAuthoritiesMapper,
) : AuthenticationStrategy {
    override val type: String = "jwks"

    private val log = LoggerFactory.getLogger(JwksStrategy::class.java)

    override fun authenticate(request: HttpServletRequest): Authentication? {
        val header = request.getHeader("Authorization") ?: return null
        // RFC 6750 SS2.1: the auth scheme name is case-insensitive (`Bearer`, `bearer`,
        // `BEARER` are all valid), matching Spring's own BearerTokenAuthenticationFilter.
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null

        val token = header.substring(BEARER_PREFIX.length).trim()
        val jwt = decodeToken(token)

        val usernameClaim = securityProperties.auth.jwks.usernameClaim
        // principal is the `username-claim` value (default `preferred_username`), not `sub`:
        // `sub` is a stable Keycloak UUID, `preferred_username` is human-readable but can
        // change in the IdP. Chosen for compatibility with Flowable IDM's human-readable userId.
        val userId = jwt.getClaimAsString(usernameClaim)
        if (userId.isNullOrBlank()) {
            throw InvalidBearerTokenException("Missing or blank claim: $usernameClaim")
        }

        val authorities = authoritiesMapper.map(jwt)

        log.debug(
            "Authenticated via JWKS: principal='{}', authorities={}",
            userId,
            authorities.map { it.authority },
        )

        return UsernamePasswordAuthenticationToken(userId, null, authorities).apply {
            details = userId
        }
    }

    private fun decodeToken(token: String): Jwt =
        try {
            jwtDecoder.decode(token)
        } catch (e: JwtException) {
            if (hasNetworkCause(e)) {
                log.warn("JWKS endpoint unreachable: ${e.message}", e)
                throw AuthenticationServiceException("JWKS endpoint unreachable: ${e.message}", e)
            }
            throw InvalidBearerTokenException(e.message ?: "Invalid JWT", e)
        }

    private fun hasNetworkCause(throwable: Throwable): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is IOException) return true
            current = current.cause
        }
        return false
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
