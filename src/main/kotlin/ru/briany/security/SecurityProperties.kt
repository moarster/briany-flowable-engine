package ru.briany.security

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@ConfigurationProperties(prefix = "briany.security")
@Validated
data class SecurityProperties(
    val auth: Auth = Auth(),
    val allowUnauthenticatedEngineApi: Boolean = true,
) {
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
