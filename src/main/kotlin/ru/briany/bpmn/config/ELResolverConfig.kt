package ru.briany.bpmn.config

import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.briany.bpmn.security.SafeBeanELResolver

@Configuration
class ELResolverConfig {
    private val log = LoggerFactory.getLogger(ELResolverConfig::class.java)

    @Bean
    fun safeBeanELResolverConfigurer(): EngineConfigurationConfigurer<SpringProcessEngineConfiguration> =
        EngineConfigurationConfigurer { cfg ->
            cfg.addPreBeanELResolver(SafeBeanELResolver())
            log.info("SafeBeanELResolver registered as pre-bean EL resolver")
        }
}
