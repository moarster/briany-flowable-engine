package ru.briany.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val securityProperties: SecurityProperties,
) {
    @Bean
    fun authenticationFilterBean(strategy: AuthenticationStrategy?): AuthenticationFilter =
        AuthenticationFilter(
            strategy,
        )

    /**
     * `JwtDecoder` for the JWKS strategy. Only created if `jwks` is enabled in
     * `briany.security.auth.strategies`, so a blank default `jwk-set-uri` is harmless.
     *
     * Uses `withJwkSetUri`, not `withIssuerLocation`, deliberately: it's lazy, the JWKS
     * endpoint is only hit on the first request rather than at context startup, so the
     * app doesn't fail to boot if Keycloak is temporarily unreachable.
     *
     * Validators added on top of signature verification: [JwtTimestampValidator]
     * (`exp`/`nbf`), [JwtIssuerValidator] only if `issuer-uri` is configured, and
     * [JwtClaimValidator] on `aud` only if `audiences` is non-empty (accepts the token
     * if any expected audience is present; per RFC 7519 `aud` may be a string or an
     * array, Spring normalizes it to `List<String>`). With both settings empty, only
     * the timestamp validator applies.
     */
    @Bean
    @ConditionalOnBean(JwksStrategy::class)
    fun jwtDecoder(): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(securityProperties.auth.jwks.jwkSetUri).build()

        val validators = mutableListOf<OAuth2TokenValidator<Jwt>>(JwtTimestampValidator())
        if (securityProperties.auth.jwks.issuerUri
                .isNotBlank()
        ) {
            validators.add(JwtIssuerValidator(securityProperties.auth.jwks.issuerUri))
        }
        val expectedAudiences = securityProperties.auth.jwks.audiences
        if (expectedAudiences.isNotEmpty()) {
            validators.add(
                JwtClaimValidator<List<String>>("aud") { tokenAudiences ->
                    tokenAudiences != null && expectedAudiences.any { it in tokenAudiences }
                },
            )
        }
        decoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
        return decoder
    }

    /**
     * CORS for the `/api/` chain, driven by `briany.security.cors.allowed-origins`. When empty
     * (the default) no mapping is registered, so behavior stays same-origin only. Uses
     * `allowedOriginPatterns` so wildcard entries remain valid alongside `allowCredentials`.
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val source = UrlBasedCorsConfigurationSource()
        val origins = securityProperties.cors.allowedOrigins
        if (origins.isNotEmpty()) {
            val cfg =
                CorsConfiguration().apply {
                    allowedOriginPatterns = origins
                    allowedMethods = securityProperties.cors.allowedMethods
                    allowedHeaders = securityProperties.cors.allowedHeaders
                    allowCredentials = true
                }
            source.registerCorsConfiguration("/api/**", cfg)
        }
        return source
    }

    @Bean
    @Order(9) // Before Flowable's IDM security (IDM_API_SECURITY_ORDER was 10)
    fun filterChain(
        http: HttpSecurity,
        authenticationFilter: AuthenticationFilter,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        // Applies only to these paths; /actuator/** and other Flowable-managed paths
        // are left to their own configuration.
        return http
            .securityMatcher(
                "/api/**",
            ).cors { it.configurationSource(corsConfigurationSource) }
            .csrf { it.disable() } // CSRF is irrelevant for a stateless API
            .sessionManagement {
                it.sessionCreationPolicy(
                    SessionCreationPolicy.STATELESS,
                )
            }.authorizeHttpRequests { authz ->
                authz.anyRequest().authenticated()
            }.httpBasic { }
            .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()
    }
}
