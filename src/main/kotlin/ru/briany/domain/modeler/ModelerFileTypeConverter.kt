package ru.briany.domain.modeler

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import ru.briany.generated.model.ModelerFileType

/**
 * Persists [ModelerFileType] as its lowercase wire value (bpmn/dmn/bform).
 */
@Converter
class ModelerFileTypeConverter : AttributeConverter<ModelerFileType, String> {
    override fun convertToDatabaseColumn(attribute: ModelerFileType): String = attribute.value

    override fun convertToEntityAttribute(dbData: String): ModelerFileType =
        ModelerFileType.entries.first { it.value == dbData }
}
