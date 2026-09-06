package ru.briany.bpmn

import org.flowable.common.engine.api.delegate.FlowableFunctionDelegate
import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Discovers every [FlowableFunctionDelegate] bean and registers it in
 * `customFlowableFunctionDelegates`, exposing it as `${prefix:localName(args)}`
 * in all BPMN expressions. Just implement the interface and mark it `@Component`.
 */
@Configuration
class FlowableFunctionDelegatesConfigurer(
    private val functionDelegates: List<FlowableFunctionDelegate>,
) {
    private val log = LoggerFactory.getLogger(FlowableFunctionDelegatesConfigurer::class.java)

    @Bean
    fun customFlowableFunctionDelegatesConfigurer(): EngineConfigurationConfigurer<SpringProcessEngineConfiguration> =
        EngineConfigurationConfigurer { cfg ->
            if (functionDelegates.isEmpty()) {
                log.info("No FlowableFunctionDelegate beans found, skipping registration")
                return@EngineConfigurationConfigurer
            }

            val existing = cfg.customFlowableFunctionDelegates ?: mutableListOf()
            val merged = (existing + functionDelegates).toMutableList()
            cfg.customFlowableFunctionDelegates = merged

            log.info(
                "Registered {} custom FlowableFunctionDelegate(s): {}",
                functionDelegates.size,
                functionDelegates.flatMap { d -> d.prefixes().flatMap { p -> d.localNames().map { "$p:$it" } } },
            )
        }
}
