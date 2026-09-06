package ru.briany.domain.form

import java.time.Instant
import java.util.UUID

interface FormResponse<out T> {
    val id: UUID
    val key: String
    val name: String?
    val version: Int
    val schema: T
    val variables: List<String>
    val deploymentId: String
    val deployedAt: Instant
}
