package ru.briany.domain.modeler

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.briany.generated.model.ModelerFileType
import java.util.UUID

interface ModelerAppFileRepository : JpaRepository<ModelerAppFileEntity, UUID> {
    fun findByAppId(appId: UUID): List<ModelerAppFileEntity>

    fun findByAppIdAndFileKey(
        appId: UUID,
        fileKey: String,
    ): ModelerAppFileEntity?

    @Query(
        "select f.appId as appId, f.type as type, f.fileKey as fileKey " +
            "from ModelerAppFileEntity f where f.appId in :appIds",
    )
    fun findMetaByAppIdIn(
        @Param("appIds") appIds: Collection<UUID>,
    ): List<AppFileMeta>
}

/** Closed projection: file metadata only, no content blob. */
interface AppFileMeta {
    val appId: UUID
    val type: ModelerFileType
    val fileKey: String
}
