package ru.briany.engine.config

import org.springframework.boot.context.properties.ConfigurationProperties
import ru.briany.engine.config.BpmnActivityType

@ConfigurationProperties(prefix = "briany.engine")
data class BpmEngineProperties(
    val whitelist: Whitelist = Whitelist(),
) {
    data class Whitelist(
        val enabled: Boolean = false,
        val activities: List<BpmnActivityType> = emptyList(),
    )
}
