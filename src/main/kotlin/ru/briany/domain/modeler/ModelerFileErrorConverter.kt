package ru.briany.domain.modeler

import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import org.springframework.stereotype.Component
import ru.briany.generated.model.ModelerFileError
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper

/**
 * JSONB AttributeConverter for the per-file error log, modeled on FormSchemaConverter.
 */
@Component
@Converter
class ModelerFileErrorConverter(
    objectMapper: ObjectMapper,
) : AttributeConverter<List<ModelerFileError>?, String?> {
    private val mapper =
        (objectMapper as JsonMapper)
            .rebuild()
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .build()

    private val listType = object : TypeReference<List<ModelerFileError>>() {}

    override fun convertToDatabaseColumn(attribute: List<ModelerFileError>?): String? =
        attribute?.let { mapper.writeValueAsString(it) }

    override fun convertToEntityAttribute(dbData: String?): List<ModelerFileError>? =
        dbData?.let { mapper.readValue(it, listType) }
}
