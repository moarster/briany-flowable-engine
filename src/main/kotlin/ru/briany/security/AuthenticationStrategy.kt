package ru.briany.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.core.Authentication

/**
 * One authentication method (Flowable IDM, JWKS/OIDC, ...).
 *
 * `authenticate` returns [Authentication] on success, `null` if this strategy doesn't
 * apply to the request (no matching header/token - the filter tries the next
 * strategy), or throws [org.springframework.security.core.AuthenticationException] if
 * it applies but fails. The exception subclass matters:
 * [org.springframework.security.authentication.AuthenticationServiceException] means a
 * transient error (upstream 5xx, network timeout) - in `both` mode the filter falls
 * back to the next strategy; any other subclass
 * ([org.springframework.security.oauth2.server.resource.InvalidBearerTokenException],
 * [org.springframework.security.authentication.BadCredentialsException]) means
 * "applicable but invalid" - the filter answers 401 immediately, no fallback.
 * [io.github.resilience4j.circuitbreaker.CallNotPermittedException] (circuit open) is
 * also treated as transient.
 *
 * `type` is a short strategy name used in the `auth.type` span tag and in the
 * `bpm_auth_fallback_total{from,to}` metric tags.
 */
interface AuthenticationStrategy {
    val type: String

    fun authenticate(request: HttpServletRequest): Authentication?
}
