package ru.briany.bpmn

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "briany.expressions")
class ExpressionProperties {
    var secrets: Map<String, String> = emptyMap()
}
