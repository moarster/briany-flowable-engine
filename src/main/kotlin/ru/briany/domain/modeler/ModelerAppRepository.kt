package ru.briany.domain.modeler

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import ru.briany.generated.model.ModelerAppState
import java.util.UUID

interface ModelerAppRepository : JpaRepository<ModelerAppEntity, UUID> {
    fun findByKeyAndTenantId(
        key: String,
        tenantId: String,
    ): ModelerAppEntity?

    fun existsByKeyAndTenantId(
        key: String,
        tenantId: String,
    ): Boolean

    fun findByState(
        state: ModelerAppState,
        pageable: Pageable,
    ): Page<ModelerAppEntity>
}
