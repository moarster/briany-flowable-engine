package ru.briany.security

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementServerProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.web.server.autoconfigure.ServerProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableWebSecurity
@ConditionalOnWebApplication
@Order(8)
class ActuatorSecurityConfiguration(
    serverProperties: ServerProperties,
    managementServerProperties: ManagementServerProperties,
) {
    private val log = LoggerFactory.getLogger(ActuatorSecurityConfiguration::class.java)
    private val serverPort = serverProperties.port ?: 8080
    private val managementPort = managementServerProperties.port ?: serverPort
    private val isConfigSecure = serverPort != managementPort && managementPort > 0

    init {
        if (isConfigSecure) {
            log.info(
                "Management port ({}) differs from main port ({}) - Actuator endpoints available",
                managementPort,
                serverPort,
            )
        } else {
            log.warn(
                "SECURITY WARNING: Management port ({}) matches main application port ({})",
                managementPort,
                serverPort,
            )
            log.warn(
                "Actuator endpoints disabled for security. Configure management.server.port to use different port.",
            )
        }
    }

    @Bean
    @Order(9)
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        if (isConfigSecure) {
            http
                .securityMatcher(
                    "/actuator/health",
                    "/actuator/prometheus",
                ).authorizeHttpRequests { authz -> authz.anyRequest().permitAll() }
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        } else {
            http
                .securityMatcher("/actuator/**")
                .authorizeHttpRequests { authz -> authz.anyRequest().denyAll() }
                .csrf { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        }
        return http.build()
    }
}
