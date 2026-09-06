package ru.briany.domain.modeler

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import org.hibernate.annotations.ColumnTransformer
import ru.briany.generated.model.ModelerAppState
import ru.briany.generated.model.ModelerFileError
import ru.briany.generated.model.ModelerFileType
import java.time.Instant
import java.util.UUID

/**
 * A single editable source file (bpmn/dmn/bform). The blob [content] is the source of truth;
 * [fileKey]/[name]/[description] are a read-only projection derived from it.
 */
@Entity
@Table(
    name = "brn_modeler_app_file",
    uniqueConstraints = [
        UniqueConstraint(
            name = "brn_uniq_modeler_file",
            columnNames = ["app_id", "file_key"],
        ),
    ],
)
class ModelerAppFileEntity(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    @Column(name = "app_id", nullable = false)
    val appId: UUID,
    @Column(name = "file_key", nullable = false, length = 255)
    val fileKey: String,
    @Column(length = 255)
    var name: String? = null,
    @Column(length = 4000)
    var description: String? = null,
    @Convert(converter = ModelerFileTypeConverter::class)
    @Column(nullable = false, length = 16)
    var type: ModelerFileType,
    @Column(name = "resource_name", nullable = false, length = 4000)
    var resourceName: String,
    @Column(nullable = false, columnDefinition = "bytea")
    var content: ByteArray,
    @Column(name = "content_hash", nullable = false, length = 64)
    var contentHash: String,
    @Column(name = "deployed_hash", length = 64)
    var deployedHash: String? = null,
    @Convert(converter = ModelerAppStateConverter::class)
    @Column(nullable = false, length = 16)
    var state: ModelerAppState = ModelerAppState.DRAFT,
    @Column(name = "engine_resource_id", length = 255)
    var engineResourceId: String? = null,
    @Convert(converter = ModelerFileErrorConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "error_log", columnDefinition = "jsonb")
    var errorLog: List<ModelerFileError>? = null,
    @Version
    @Column(name = "lock_version", nullable = false)
    var lockVersion: Long = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
