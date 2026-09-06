package ru.briany.domain.form

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import ru.briany.generated.model.FormComponent
import ru.briany.generated.model.FormComponentAppearance
import ru.briany.generated.model.FormComponentConditional
import ru.briany.generated.model.FormComponentType
import ru.briany.generated.model.FormComponentValidate
import ru.briany.generated.model.FormSchema

class FormVariableExtractorTest {
    @Test
    fun `extracts keys from flat components`() {
        val schema =
            schema(
                component("firstName", FormComponentType.TEXTFIELD),
                component("lastName", FormComponentType.TEXTFIELD),
            )
        assertEquals(setOf("firstName", "lastName"), FormVariableExtractor.extract(schema))
    }

    @Test
    fun `skips no-key types`() {
        val schema =
            schema(
                component("name", FormComponentType.TEXTFIELD),
                component("submit", FormComponentType.BUTTON),
                component("hint", FormComponentType.TEXTFIELD),
                component("divider", FormComponentType.SEPARATOR),
                component("gap", FormComponentType.SPACER),
            )
        assertEquals(setOf("name", "hint"), FormVariableExtractor.extract(schema))
    }

    @Test
    fun `ignore nested keys`() {
        val schema =
            schema(
                FormComponent(
                    label = "label",
                    type = FormComponentType.GROUP,
                    path = "billing",
                    components =
                        listOf(
                            FormComponent(
                                label = "label",
                                type = FormComponentType.GROUP,
                                path = "address",
                                components = listOf(component("city", FormComponentType.TEXTFIELD)),
                            ),
                        ),
                ),
            )
        assertEquals(setOf("billing"), FormVariableExtractor.extract(schema))
    }

    @Test
    fun `extracts variables from feel expressions`() {
        val schema =
            schema(
                FormComponent(
                    label = "label",
                    type = FormComponentType.TEXTFIELD,
                    key = "name",
                    conditional = FormComponentConditional(hide = "=isHidden"),
                ),
            )
        val vars = FormVariableExtractor.extract(schema)
        assert("name" in vars)
        assert("isHidden" in vars)
    }

    @Test
    fun `deduplicates variables`() {
        val schema =
            schema(
                component("status", FormComponentType.SELECT),
                component("status", FormComponentType.SELECT),
            )
        assertEquals(setOf("status"), FormVariableExtractor.extract(schema))
    }

    @Test
    fun `returns empty for empty schema`() {
        assertEquals(emptySet<String>(), FormVariableExtractor.extract(FormSchema()))
    }

    @Test
    fun `properly extract from group component`() {
        val groupComponent =
            FormComponent(
                type = FormComponentType.GROUP,
                path = "groupPath.thisShouldntAppear",
                conditional = FormComponentConditional(hide = "=someX >= someY.noBs"),
                label = "=groupLabelVar",
                components =
                    listOf(
                        FormComponent(
                            type = FormComponentType.TEXTAREA,
                            label = "=inGroupTextLabel.noBs",
                            key = "hiddenKey1",
                            defaultValue = "nothing",
                            disabled = true,
                            validate = FormComponentValidate(minLength = "=124*someZ"),
                        ),
                        FormComponent(
                            type = FormComponentType.NUMBER,
                            label = "nothing",
                            key = "hiddenKey1",
                            increment = "4",
                            decimalDigits = 2,
                            appearance = FormComponentAppearance(prefixAdorner = "=someW+\" $\""),
                        ),
                    ),
            )
        val schema = schema(groupComponent)
        assertEquals(
            setOf("groupPath", "someX", "someY", "someZ", "someW", "groupLabelVar", "inGroupTextLabel"),
            FormVariableExtractor.extract(schema),
        )
    }

    private fun schema(vararg components: FormComponent) = FormSchema(id = "f1", components = components.toList())

    private fun component(
        key: String,
        type: FormComponentType,
    ) = FormComponent(label = "label", key = key, type = type)
}
