package ru.briany.domain.modeler

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.persistence.Version
import ru.briany.generated.model.ModelerAppState
import java.time.Instant
import java.util.UUID

/**
 * A mutable modeling workspace. Container-level metadata plus a reference to the deployed
 * Flowable version. Files live in [ModelerAppFileEntity]; content is the source of truth.
 */
@Entity
@Table(
    name = "brn_modeler_app",
    uniqueConstraints = [
        UniqueConstraint(
            name = "brn_uniq_modeler_app",
            columnNames = ["key", "tenant_id"],
        ),
    ],
)
class ModelerAppEntity(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    @Column(name = "key", nullable = false, length = 255)
    val key: String,
    @Column(length = 255)
    var name: String? = null,
    @Column(length = 4000)
    var description: String? = null,
    @Column(columnDefinition = "text")
    var readme: String? = null,
    @Convert(converter = ModelerAppStateConverter::class)
    @Column(nullable = false, length = 16)
    var state: ModelerAppState = ModelerAppState.DRAFT,
    @Column(name = "app_definition_id", length = 64)
    var appDefinitionId: String? = null,
    @Column(name = "app_definition_key", length = 255)
    var appDefinitionKey: String? = null,
    @Column(name = "deployment_id", length = 64)
    var deploymentId: String? = null,
    @Column(name = "deployed_version")
    var deployedVersion: Int? = null,
    @Column(name = "deployed_content_hash", length = 64)
    var deployedContentHash: String? = null,
    @Column(name = "deployed_at")
    var deployedAt: Instant? = null,
    @Column(name = "tenant_id", nullable = false, length = 255)
    val tenantId: String = "",
    @Version
    @Column(name = "lock_version", nullable = false)
    var lockVersion: Long = 0,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)
