package ru.briany.common.api

import org.springframework.http.HttpStatus
import ru.briany.generated.model.ValidationError

/**
 * Carries everything needed to render a contract `Problem`: an HTTP status, an optional stable
 * `code` the client can branch on, and optional structured field errors. Handled by
 * [GlobalExceptionHandler]. Use for typed, client-facing failures (a task with no form, a
 * deploy that fails validation) where a plain message is not enough.
 */
class ApiProblemException(
    val status: HttpStatus,
    val detail: String,
    val code: String? = null,
    val errors: List<ValidationError> = emptyList(),
) : RuntimeException(detail)
