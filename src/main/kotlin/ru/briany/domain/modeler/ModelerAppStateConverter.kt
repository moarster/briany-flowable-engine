package ru.briany.domain.modeler

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import ru.briany.generated.model.ModelerAppState

/**
 * Persists [ModelerAppState] as its lowercase wire value (draft/synced/ahead).
 */
@Converter
class ModelerAppStateConverter : AttributeConverter<ModelerAppState, String> {
    override fun convertToDatabaseColumn(attribute: ModelerAppState): String = attribute.value

    override fun convertToEntityAttribute(dbData: String): ModelerAppState =
        ModelerAppState.entries.first { it.value == dbData }
}
