package ru.briany.domain.form

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.ColumnTransformer
import ru.briany.generated.model.FormSchema
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "brn_form_definition",
    uniqueConstraints = [
        UniqueConstraint(
            name = "brn_uniq_form_def",
            columnNames = ["key", "version", "tenant_id"],
        ),
    ],
)
class FormEntity(
    @Id
    @Column
    val id: UUID = UUID.randomUUID(),
    @Column(name = "key", nullable = false, length = 255)
    val key: String,
    @Column(length = 255)
    val name: String? = null,
    @Column(nullable = false)
    val version: Int = 1,
    @Convert(converter = FormSchemaConverter::class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "schema_json", columnDefinition = "jsonb", nullable = false)
    val schema: FormSchema,
    @Column(length = 255)
    val category: String? = null,
    @Column(name = "deployment_id", nullable = false, length = 64)
    val deploymentId: String,
    @Column(name = "resource_name", nullable = false, length = 4000)
    val resourceName: String,
    @Column(name = "resource_bytes", nullable = false, columnDefinition = "bytea")
    val resourceBytes: ByteArray,
    @Column(length = 4000)
    val description: String? = null,
    @Column(name = "tenant_id", nullable = false, length = 255)
    val tenantId: String = "",
    @Column(columnDefinition = "text")
    val variableBindings: String? = null,
    @Column(name = "deployed_at", nullable = false)
    val deployedAt: Instant = Instant.now(),
)
