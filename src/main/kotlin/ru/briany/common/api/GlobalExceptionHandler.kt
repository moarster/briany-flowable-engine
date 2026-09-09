package ru.briany.common.api

import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import ru.briany.generated.model.Problem

@RestControllerAdvice(basePackages = ["ru.briany"])
class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(ex: MethodArgumentNotValidException): Problem {
        val errors = ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }
        log.warn("Request body validation failed: {}", errors.joinToString("; "))
        return Problem("Request body validation error occurred: ${errors.joinToString("; ")}")
    }

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleConstraintViolation(ex: ConstraintViolationException): Problem {
        val errors =
            ex.constraintViolations.map { violation ->
                val field = violation.propertyPath.toString().substringAfterLast('.')
                "$field: ${violation.message}"
            }
        log.warn("Constraint violation: {}", errors.joinToString("; "))
        return Problem("Request param validation error occurred: ${errors.joinToString("; ")}")
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleUnreadable(ex: HttpMessageNotReadableException): Problem {
        log.warn("Malformed request body: {}", ex.message)
        return Problem("Invalid request format")
    }

    @ExceptionHandler(ApiProblemException::class)
    fun handleApiProblem(ex: ApiProblemException): ResponseEntity<Problem> {
        log.warn("API problem [{}]: {}", ex.code ?: ex.status.value(), ex.detail)
        return ResponseEntity.status(ex.status).body(
            Problem(
                detail = ex.detail,
                status = ex.status.value(),
                code = ex.code,
                errors = ex.errors.ifEmpty { null },
            ),
        )
    }
}
