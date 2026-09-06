package ru.briany.domain.form

import ru.briany.domain.form.feel.extractVariables
import ru.briany.generated.model.FormComponent
import ru.briany.generated.model.FormComponentType
import ru.briany.generated.model.FormSchema

object FormVariableExtractor {
    // They use `path` instead of `key` and contain nested components
    private val PATH_TYPES = setOf(FormComponentType.GROUP, FormComponentType.DYNAMICLIST)

    // They don't use neither `key` nor `path`
    private val KEY_TYPES =
        setOf(
            FormComponentType.TEXTAREA,
            FormComponentType.TEXTFIELD,
            FormComponentType.DATETIME,
            FormComponentType.EXPRESSION,
            FormComponentType.FILEPICKER,
            FormComponentType.CHECKBOX,
            FormComponentType.CHECKLIST,
            FormComponentType.RADIO,
            FormComponentType.SELECT,
            FormComponentType.TAGLIST,
            FormComponentType.NUMBER,
        )

    fun extract(schema: FormSchema): Set<String> = schema.components?.flatMapTo(mutableSetOf()) { extractFromComponent(it) } ?: emptySet()

    private fun extractFromComponent(component: FormComponent): Set<String> {
        val vars = mutableSetOf<String>()

        if (component.type in KEY_TYPES) {
            component.key?.let { vars.add(it.substringBefore(".")) }
        } else if (component.type in PATH_TYPES) {
            component.path?.let { vars.add(it.substringBefore(".")) }
        }

        component.feelExpressions().forEach { expr ->
            vars.addAll(extractVariables(expr))
        }

        return vars
    }
}
