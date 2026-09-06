package ru.briany.bpmn

import org.flowable.engine.delegate.JavaDelegate
import org.flowable.spring.SpringProcessEngineConfiguration
import org.flowable.spring.boot.EngineConfigurationConfigurer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.getBeansOfType
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ExpressionBeansConfigurer(
    private val applicationContext: ApplicationContext,
    private val properties: ExpressionProperties,
) {
    private val log = LoggerFactory.getLogger(ExpressionBeansConfigurer::class.java)

    @Bean
    fun expressionBeansEngineConfigurer(): EngineConfigurationConfigurer<SpringProcessEngineConfiguration> =
        EngineConfigurationConfigurer { cfg ->
            val expressionBeans = discoverExpressionBeans()

            val existing = cfg.beans
            if (existing != null) {
                @Suppress("UNCHECKED_CAST")
                existing.putAll(expressionBeans)
            } else {
                cfg.beans = expressionBeans
            }

            log.info(
                "Registered {} expression beans in Flowable engine context: {}",
                expressionBeans.size,
                expressionBeans.keys,
            )
        }

    private fun discoverExpressionBeans(): Map<in Any, Any?> {
        val beans = mutableMapOf<Any, Any?>("secrets" to properties.secrets)
        registerJavaDelegates(beans)
        return beans
    }

    /**
     * Exposes every [JavaDelegate] Spring bean under its bean name so it can be referenced from a
     * `flowable:delegateExpression="${beanName}`".
     */
    private fun registerJavaDelegates(beans: MutableMap<Any, Any?>) {
        val delegates = applicationContext.getBeansOfType<JavaDelegate>()
        for ((beanName, bean) in delegates) {
            beans.putIfAbsent(beanName, bean)
        }
    }
}
