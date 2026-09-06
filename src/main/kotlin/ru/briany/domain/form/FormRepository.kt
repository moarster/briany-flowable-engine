package ru.briany.domain.form

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface FormRepository : JpaRepository<FormEntity, UUID> {
    fun findTopByIdOrderByVersionDesc(id: UUID): FormEntity?

    fun findTopByKeyOrderByVersionDesc(key: String): FormEntity?

    fun findByDeploymentId(deploymentId: String): List<FormEntity>

    fun findTopByKeyAndTenantIdOrderByVersionDesc(
        key: String,
        tenantId: String,
    ): FormEntity?

    fun findByKeyAndTenantId(
        key: String,
        tenantId: String,
    ): List<FormEntity>

    fun findByDeploymentIdAndKey(
        deploymentId: String,
        key: String,
    ): FormEntity?

    fun deleteByDeploymentId(deploymentId: String)

    @Query(
        value = """
            SELECT DISTINCT ON (id) *
            FROM public.brn_form_definition
            ORDER BY id, version DESC
        """,
        nativeQuery = true,
    )
    fun findLatest(): List<FormEntity>
}
