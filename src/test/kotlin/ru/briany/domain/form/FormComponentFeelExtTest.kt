package ru.briany.domain.form

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.briany.config.JacksonConfig
import ru.briany.generated.model.FormComponent
import ru.briany.generated.model.FormComponentAppearance
import ru.briany.generated.model.FormComponentConditional
import ru.briany.generated.model.FormComponentType
import ru.briany.generated.model.FormComponentValidate
import ru.briany.generated.model.FormSchema
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinInvalidNullException
import tools.jackson.module.kotlin.KotlinModule

class FormComponentFeelExtTest {
    // ObjectMapper assembled identically to production (Spring's JacksonAutoConfiguration
    // + spring.jackson.deserialization.fail-on-unknown-properties=false + JacksonConfig bean).
    // Keeps tolerance tests self-contained without booting Spring.
    private val objectMapper =
        JsonMapper
            .builder()
            .addModule(KotlinModule.Builder().build())
            .addModule(JacksonConfig().formComponentTypeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .build()

    @Test
    fun `feelExpressions returns empty for plain component`() {
        val component =
            FormComponent(
                type = FormComponentType.TEXTFIELD,
                key = "name",
                label = "Name",
            )
        assertTrue(component.feelExpressions().isEmpty())
    }

    @Test
    fun `feelExpressions extracts conditional hide`() {
        val component =
            FormComponent(
                label = "label",
                conditional = FormComponentConditional(hide = "=isHidden"),
            )
        assertEquals(setOf("isHidden"), component.feelExpressions())
    }

    @Test
    fun `feelExpressions support complex FEEL expressions`() {
        val component =
            FormComponent(
                label = "label",
                readonly = """={
                                                          a:"abc",
                                                          x:10
                                                        }.x=10""",
            )
        assertEquals(
            setOf(
                """{
                                                          a:"abc",
                                                          x:10
                                                        }.x=10""",
            ),
            component.feelExpressions(),
        )
    }

    @Test
    fun `feelExpressions extracts feel label but not plain label`() {
        val feel = FormComponent(label = "=myVar")
        val plain = FormComponent(label = "My Label")
        assertEquals(setOf("myVar"), feel.feelExpressions())
        assertTrue(plain.feelExpressions().isEmpty())
    }

    @Test
    fun `feelExpressions skips boolean readonly but extracts feel readonly`() {
        val literal = FormComponent(label = "label", readonly = true)
        val feel = FormComponent(label = "label", readonly = "=canEdit")
        assertTrue(literal.feelExpressions().isEmpty())
        assertEquals(setOf("canEdit"), feel.feelExpressions())
    }

    @Test
    fun `feelExpressions skips boolean multiple but extracts feel multiple`() {
        val literal = FormComponent(label = "label", multiple = true)
        val feel = FormComponent(label = "label", multiple = "=allowMultiple")
        assertTrue(literal.feelExpressions().isEmpty())
        assertEquals(setOf("allowMultiple"), feel.feelExpressions())
    }

    @Test
    fun `feelExpressions extracts valuesExpression`() {
        val component =
            FormComponent(
                label = "label",
                type = FormComponentType.SELECT,
                valuesExpression = "=options",
            )
        assertEquals(setOf("options"), component.feelExpressions())
    }

    @Test
    fun `feelExpressions extracts appearance adorners`() {
        val component =
            FormComponent(
                label = "label",
                appearance =
                    FormComponentAppearance(
                        prefixAdorner = "=prefix",
                        suffixAdorner = "=suffix",
                    ),
            )
        assertEquals(setOf("prefix", "suffix"), component.feelExpressions())
    }

    @Test
    fun `feelExpressions extracts feel validate constraints but not plain`() {
        val feel =
            FormComponent(
                label = "label",
                validate = FormComponentValidate(minLength = "=minLen", maxLength = "=maxLen"),
            )
        val plain =
            FormComponent(
                label = "label",
                validate = FormComponentValidate(minLength = "5", maxLength = "100"),
            )
        assertEquals(setOf("minLen", "maxLen"), feel.feelExpressions())
        assertTrue(plain.feelExpressions().isEmpty())
    }

    @Test
    fun `feelExpressions strips leading equals sign`() {
        val component =
            FormComponent(
                label = "label",
                expression = "=score + bonus",
                conditional = FormComponentConditional(hide = "=isVisible"),
            )
        assertEquals(setOf("score + bonus", "isVisible"), component.feelExpressions())
    }

    @Test
    fun `feelExpressions collects all feel fields from fully-loaded component`() {
        val component =
            FormComponent(
                key = "key",
                type = FormComponentType.TEXTFIELD,
                label = "=myLabel",
                description = "=myDesc",
                conditional = FormComponentConditional(hide = "=isHidden"),
                appearance = FormComponentAppearance(prefixAdorner = "=pre", suffixAdorner = "=suf"),
                validate = FormComponentValidate(minLength = "=minLen", maxLength = "=maxLen"),
                readonly = "=canEdit",
            )
        assertEquals(
            setOf("isHidden", "pre", "suf", "myLabel", "myDesc", "minLen", "maxLen", "canEdit"),
            component.feelExpressions(),
        )
    }

    // ─── Jackson tolerance tests (mirror of @JsonIgnoreProperties on the old hand-written DTOs) ─

    @Test
    fun `FormSchema tolerates unknown top-level fields`() {
        val json =
            """
            {
              "id": "f1",
              "components": [],
              "label": "label",
              "futureField": "form-js v3 might add this"
            }
            """.trimIndent()
        val schema = objectMapper.readValue(json, FormSchema::class.java)
        assertEquals("f1", schema.id)
    }

    @Test
    fun `FormComponent tolerates unknown fields`() {
        val json =
            """
            {
              "type": "textfield",
              "key": "name",
              "label": "label",
              "experimentalProp": { "nested": true }
            }
            """.trimIndent()
        val component = objectMapper.readValue(json, FormComponent::class.java)
        assertEquals(FormComponentType.TEXTFIELD, component.type)
        assertEquals("name", component.key)
    }

    @Test
    fun `FormComponentType deserializes unknown enum value as null`() {
        val json = """{ "type": "signaturePadV2030" , "label": "label" }"""
        assertThrows<KotlinInvalidNullException> { objectMapper.readValue(json, FormComponent::class.java) }
    }

    @Test
    fun `FormComponentType deserializes explicit json null as null`() {
        val json = """{  "type": null , "label": "label"}"""
        assertThrows<KotlinInvalidNullException> { objectMapper.readValue(json, FormComponent::class.java) }
    }

    @Test
    fun `FormComponentType deserializes known enum value normally`() {
        val json = """{ "type": "documentPreview" , "label": "label"}"""
        val component = objectMapper.readValue(json, FormComponent::class.java)
        assertEquals(FormComponentType.DOCUMENT_PREVIEW, component.type)
    }
}
