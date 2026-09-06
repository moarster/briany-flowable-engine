package ru.briany.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.briany.generated.model.FormComponentType
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JacksonModule
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule

@Configuration
class JacksonConfig {
    @Bean
    fun formComponentTypeModule(): JacksonModule =
        SimpleModule("FormComponentTypeTolerantModule").apply {
            addDeserializer(
                FormComponentType::class.java,
                object : ValueDeserializer<FormComponentType>() {
                    override fun deserialize(
                        p: JsonParser,
                        ctxt: DeserializationContext,
                    ): FormComponentType? =
                        p.valueAsString?.let { v ->
                            FormComponentType.entries.firstOrNull { it.value == v }
                        }
                },
            )
        }
}
