package ru.briany.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint
import org.springframework.web.filter.OncePerRequestFilter

class AuthenticationFilter(
    private val strategy: AuthenticationStrategy?,
) : OncePerRequestFilter() {
    private val bearerEntryPoint = BearerTokenAuthenticationEntryPoint()

    private sealed interface AuthOutcome {
        data class Success(
            val authentication: Authentication?,
        ) : AuthOutcome

        // Error response already written to `response`
        data object Handled : AuthOutcome
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (strategy == null) {
            logger.debug("Authentication disabled (no strategies), skipping for ${request.requestURI}")
            filterChain.doFilter(request, response)
            return
        }

        when (val outcome = tryAuthenticate(strategy, request, response)) {
            is AuthOutcome.Handled -> {
                return
            }

            is AuthOutcome.Success -> {
                SecurityContextHolder.getContext().authentication = outcome.authentication
                logger.debug("Authenticated via ${strategy.type}: principal='${outcome.authentication?.name}'")
                filterChain.doFilter(request, response)
            }
        }
    }

    private fun tryAuthenticate(
        strategy: AuthenticationStrategy,
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): AuthOutcome =
        try {
            AuthOutcome.Success(strategy.authenticate(request))
        } catch (e: AuthenticationServiceException) {
            logger.warn("Auth service unavailable in ${strategy.type}: ${e.message}", e)
            response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
            response.setHeader("Retry-After", "15")
            AuthOutcome.Handled
        } catch (e: OAuth2AuthenticationException) {
            logger.warn(
                "OAuth2 auth failed in ${strategy.type}: " +
                    "${e.error.errorCode} - ${e.error.description}",
            )
            bearerEntryPoint.commence(request, response, e)
            AuthOutcome.Handled
        } catch (e: AuthenticationException) {
            logger.warn("Auth failed in ${strategy.type}: ${e.message}")
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            AuthOutcome.Handled
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception,
        ) {
            logger.error("Unexpected error in ${strategy.type}", e)
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            AuthOutcome.Handled
        }
}
