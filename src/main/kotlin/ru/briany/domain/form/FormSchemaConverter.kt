package ru.briany.domain.form

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component
import ru.briany.generated.model.FormSchema
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

@Component
@Converter
class FormSchemaConverter(
    objectMapper: ObjectMapper,
) : AttributeConverter<FormSchema, String> {
    private val mapper =
        (objectMapper as JsonMapper)
            .rebuild()
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .build()

    override fun convertToDatabaseColumn(attribute: FormSchema): String = mapper.writeValueAsString(attribute)

    override fun convertToEntityAttribute(dbData: String): FormSchema = mapper.readValue(dbData, FormSchema::class.java)
}
