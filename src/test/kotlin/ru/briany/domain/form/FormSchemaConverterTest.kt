package ru.briany.domain.form

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.briany.config.JacksonConfig
import ru.briany.generated.model.FormComponent
import ru.briany.generated.model.FormComponentAppearance
import ru.briany.generated.model.FormComponentConditional
import ru.briany.generated.model.FormComponentType
import ru.briany.generated.model.FormComponentValidate
import ru.briany.generated.model.FormSchema
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class FormSchemaConverterTest {
    private val objectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JacksonConfig().formComponentTypeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build()

    private val converter = FormSchemaConverter(objectMapper)

    @Test
    fun `roundtrip preserves a realistic form schema fully`() {
        val original =
            FormSchema(
                id = "loanApplication",
                type = "default",
                components =
                    listOf(
                        FormComponent(
                            type = FormComponentType.TEXTFIELD,
                            key = "applicantName",
                            label = "Applicant name",
                            validate = FormComponentValidate(required = true, minLength = "2", maxLength = "100"),
                        ),
                        FormComponent(
                            type = FormComponentType.NUMBER,
                            key = "amount",
                            label = "=amountLabel",
                            appearance = FormComponentAppearance(prefixAdorner = "$", suffixAdorner = "=currency"),
                            readonly = "=isReadonly",
                        ),
                        FormComponent(
                            label = "label",
                            type = FormComponentType.GROUP,
                            path = "address",
                            conditional = FormComponentConditional(hide = "=isAddressHidden"),
                            components =
                                listOf(
                                    FormComponent(label = "label", type = FormComponentType.TEXTFIELD, key = "street"),
                                    FormComponent(label = "label", type = FormComponentType.TEXTFIELD, key = "city"),
                                ),
                        ),
                    ),
                schemaVersion = 16,
            )

        val json = converter.convertToDatabaseColumn(original)
        val restored = converter.convertToEntityAttribute(json)

        assertEquals(original, restored)
    }

    @Test
    fun `roundtrip preserves empty schema`() {
        val original = FormSchema()

        val json = converter.convertToDatabaseColumn(original)
        val restored = converter.convertToEntityAttribute(json)

        assertEquals(original, restored)
    }
}
