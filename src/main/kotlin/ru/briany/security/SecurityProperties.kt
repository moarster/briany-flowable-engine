package ru.briany.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "briany.security")
@Validated
data class SecurityProperties(
    val auth: Auth = Auth(),
    val cors: Cors = Cors(),
    val allowUnauthenticatedEngineApi: Boolean = true,
) {
    /**
     * CORS for the `/api/` chain. Off by default (`allowedOrigins` empty), keeping the current
     * single-origin deployment (Vite proxy in dev, Traefik in prod) unchanged. A split or
     * contract-stand deployment opts in via `briany.security.cors.allowed-origins`.
     */
    data class Cors(
        val allowedOrigins: List<String> = emptyList(),
        val allowedMethods: List<String> = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"),
        val allowedHeaders: List<String> = listOf("*"),
    )

    data class Auth(
        val strategy: String = "",
        val jwks: Jwks = Jwks(),
    )

    data class Jwks(
        val issuerUri: String = "",
        val jwkSetUri: String = "",
        val audiences: List<String> = emptyList(),
        /**
         * Maps claim to `Authentication.name`.
         * Defaults to `preferred_username` because Flowable IDM expects human-readable userId.
         */
        @field:NotBlank
        val usernameClaim: String = "preferred_username",
    )
}
