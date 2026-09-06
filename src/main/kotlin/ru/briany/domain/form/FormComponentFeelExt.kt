package ru.briany.domain.form

import ru.briany.generated.model.FormComponent

/**
 * Returns all FEEL expressions used in this component, with the leading "=" stripped,
 * ready to be passed to FeelVariableExtractor.
 *
 * Covers both spec-defined FEEL fields (always expressions) and optionally-FEEL fields
 * (plain string or FEEL depending on whether the value starts with "=").
 */
private fun String.asFeelOrNull(): String? = if (startsWith("=")) substring(1) else null

fun FormComponent.feelExpressions(): Set<String> {
    // Spec-defined FEEL fields, always expressions when present, plus optionally-FEEL
    // fields (plain string or FEEL depending on a leading "=")
    val candidates =
        listOfNotNull(
            conditional?.hide,
            appearance?.prefixAdorner,
            appearance?.suffixAdorner,
            valuesExpression,
            expression,
            columnsExpression,
            label,
            description,
            accept,
            url,
            dataSource,
            text,
            content,
            source,
            alt,
            validate?.minLength,
            validate?.maxLength,
            readonly as? String,
            multiple as? String,
        )
    val own = candidates.mapNotNull { it.asFeelOrNull() }.toSet()
    val nested = components.orEmpty().flatMap { it.feelExpressions() }.toSet()
    return own + nested
}
