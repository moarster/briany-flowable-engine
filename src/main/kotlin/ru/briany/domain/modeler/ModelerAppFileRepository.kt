package ru.briany.domain.modeler

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ModelerAppFileRepository : JpaRepository<ModelerAppFileEntity, UUID> {
    fun findByAppId(appId: UUID): List<ModelerAppFileEntity>

    fun findByAppIdAndFileKey(
        appId: UUID,
        fileKey: String,
    ): ModelerAppFileEntity?
}
