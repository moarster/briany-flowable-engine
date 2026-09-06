package ru.briany.security

import jakarta.servlet.http.HttpServletRequest
import org.flowable.idm.api.IdmIdentityService
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Authenticates against Flowable's built-in IDM.
 *
 * Reads `Authorization: Basic <base64(user:password)>` and checks the credentials
 * directly in the engine via [IdmIdentityService.checkPassword] (`ACT_ID_USER` table).
 * No external service involved - the engine itself is the source of truth, so unlike
 * [JwksStrategy] there is no user sync and no transient/fallback path: a DB call either
 * succeeds or is already an infrastructure failure.
 *
 * Result mapping: no `Authorization` header, or not `Basic`, -> `null` (not
 * applicable, filter moves on). Malformed base64, missing `:` separator, wrong
 * password, or unknown user -> [BadCredentialsException] ("applicable but invalid" -
 * filter answers 401, no fallback).
 *
 * Authorities are the user's IDM group ids (`ACT_ID_GROUP`) plus `access-task`, the
 * minimal permission to enter the Flowable Workflow App (same as the other
 * strategies, see [KeycloakJwtAuthoritiesMapper]).
 */
@Component
@ConditionalOnProperty(name = ["briany.security.auth.strategy"], havingValue = "flowable")
class FlowableAuthStrategy(
    private val idmIdentityService: IdmIdentityService,
) : AuthenticationStrategy {
    override val type: String = "flowable"

    private val log = LoggerFactory.getLogger(FlowableAuthStrategy::class.java)

    override fun authenticate(request: HttpServletRequest): Authentication? {
        val header = request.getHeader(AUTHORIZATION_HEADER) ?: return null
        if (!header.startsWith(BASIC_PREFIX, ignoreCase = true)) return null

        val credentials = decodeCredentials(header.substring(BASIC_PREFIX.length).trim())

        if (!idmIdentityService.checkPassword(credentials.userId, credentials.password)) {
            throw BadCredentialsException("Invalid username or password")
        }

        val authorities = loadAuthorities(credentials.userId)

        log.debug(
            "Authenticated via Flowable IDM: principal='{}', authorities={}",
            credentials.userId,
            authorities.map { it.authority },
        )

        return UsernamePasswordAuthenticationToken(credentials.userId, null, authorities).apply {
            details = credentials.userId
        }
    }

    private fun loadAuthorities(userId: String): List<SimpleGrantedAuthority> {
        val groupAuthorities =
            idmIdentityService
                .createGroupQuery()
                .groupMember(userId)
                .list()
                .map { SimpleGrantedAuthority(it.id) }
        return groupAuthorities + SimpleGrantedAuthority(ACCESS_TASK_AUTHORITY)
    }

    private fun decodeCredentials(encoded: String): Credentials {
        val decoded =
            try {
                String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                throw BadCredentialsException("Malformed Basic authentication header", e)
            }
        val separatorIndex = decoded.indexOf(':')
        if (separatorIndex < 0) {
            throw BadCredentialsException("Basic authentication token missing ':' delimiter")
        }
        return Credentials(
            userId = decoded.substring(0, separatorIndex),
            password = decoded.substring(separatorIndex + 1),
        )
    }

    private data class Credentials(
        val userId: String,
        val password: String,
    )

    companion object {
        private const val AUTHORIZATION_HEADER = "Authorization"
        private const val BASIC_PREFIX = "Basic "
        private const val ACCESS_TASK_AUTHORITY = "access-task"
    }
}
