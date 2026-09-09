package ru.briany.engine.config

import jakarta.annotation.PostConstruct
import org.flowable.common.engine.api.identity.AuthenticationContext
import org.flowable.spring.SpringProcessEngineConfiguration
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import ru.briany.engine.config.behavior.CustomActivityBehaviorFactory
import java.security.Principal

/**
 * Core Flowable engine configuration: swaps in [CustomActivityBehaviorFactory] when the
 * activity whitelist is enabled, and provides an [AuthenticationContext] that resolves
 * userId from the Spring Security context.
 *
 * Other engine config aspects live in separate @Configuration classes in this package,
 * e.g. [ru.briany.engine.config.validator.BpmnValidatorConfig] for deployment-time BPMN
 * validators.
 */
@Configuration
class FlowableConfig(
    private val whitelistConfig: BpmEngineProperties,
) {
    private val log = LoggerFactory.getLogger(FlowableConfig::class.java)

    @Autowired
    private lateinit var processEngineConfiguration: SpringProcessEngineConfiguration

    @PostConstruct
    fun configureProcessEngine() {
        if (whitelistConfig.whitelist.enabled) {
            processEngineConfiguration.activityBehaviorFactory = CustomActivityBehaviorFactory()
            log.info(
                "Activity whitelist is enabled with allowed activities: {}." +
                    " Using custom ActivityBehaviorFactory",
                whitelistConfig.whitelist.activities,
            )
        } else {
            log.info("Activity whitelist is disabled. Using default ActivityBehaviorFactory")
        }

        log.info("ProcessEngine configured")
    }

    @Bean
    fun authenticationContext(): AuthenticationContext =
        object : AuthenticationContext {
            private val log = LoggerFactory.getLogger(AuthenticationContext::class.java)
            private var principal: Principal? = null

            override fun getAuthenticatedUserId(): String? {
                val authentication = SecurityContextHolder.getContext().authentication
                log.debug(
                    "AuthenticationContext: Attempting to get AuthenticatedUserId. Authentication object: {}",
                    authentication,
                )
                if (authentication != null && authentication.isAuthenticated) {
                    val userId =
                        when (val details = authentication.details) {
                            is String -> {
                                log.debug("AuthenticationContext: Extracted userId from details: '{}'", details)
                                details
                            }

                            else -> {
                                log.debug(
                                    "AuthenticationContext: Falling back to authentication name: '{}'",
                                    authentication.name,
                                )
                                authentication.name
                            }
                        }
                    return userId
                }
                val fallbackUserId = principal?.name
                log.debug(
                    "AuthenticationContext: Authentication is null or not authenticated. Falling back to principal: '{}'",
                    fallbackUserId,
                )
                return fallbackUserId
            }

            override fun getPrincipal(): Principal? {
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication != null && authentication.isAuthenticated) {
                    return Principal { authentication.name }
                }
                return principal
            }

            override fun setPrincipal(p: Principal?) {
                this.principal = p
            }
        }
}
