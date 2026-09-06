package ru.briany.domain.form

import org.flowable.engine.RepositoryService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import ru.briany.common.api.params.IdOrKey
import ru.briany.config.JacksonConfig
import ru.briany.generated.model.FormComponent
import ru.briany.generated.model.FormComponentType
import ru.briany.generated.model.FormSchema
import tools.jackson.databind.json.JsonMapper
import java.util.UUID

class FormServiceTest {
    @Mock
    private lateinit var repo: FormRepository

    @Mock
    private lateinit var repositoryService: RepositoryService

    private val objectMapper =
        JsonMapper
            .builder()
            .addModule(
                tools.jackson.module.kotlin.KotlinModule
                    .Builder()
                    .build(),
            ).addModule(JacksonConfig().formComponentTypeModule())
            .build()
    private lateinit var service: FormService

    private val schemaWithId = """{"id":"myForm","components":[{"key":"amount", "label": "label","type":"number"}]}"""
    private val schemaWithIdDto =
        FormSchema(
            id = "myForm",
            components = listOf(FormComponent(key = "amount", label = "label", type = FormComponentType.NUMBER)),
        )
    private val schemaNoId = """{"components":[{"key":"amount", "label": "label", "type":"number"}]}"""

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = FormService(repo, objectMapper, repositoryService)
    }

    @Test
    fun `deployForm - first deployment starts at version 1`() {
        whenever(repo.findTopByKeyAndTenantIdOrderByVersionDesc("myForm", "")).thenReturn(null)
        whenever(repo.save(any(FormEntity::class.java))).thenAnswer { it.arguments[0] as FormEntity }

        val result = service.deployForm("form.bform", schemaWithId.toByteArray(), "dep-1", "")

        assertEquals("myForm", result.key)
        assertEquals(1, result.version)
        assertEquals("dep-1", result.deploymentId)
    }

    @Test
    fun `deployForm - increments version when form already exists`() {
        whenever(repo.findTopByKeyAndTenantIdOrderByVersionDesc("myForm", "")).thenReturn(entity(version = 3))
        whenever(repo.save(any(FormEntity::class.java))).thenAnswer { it.arguments[0] as FormEntity }

        val result = service.deployForm("form.bform", schemaWithId.toByteArray(), "dep-2", "")

        assertEquals(4, result.version)
    }

    @Test
    fun `deployForm - extracts variable bindings from schema`() {
        whenever(repo.findTopByKeyAndTenantIdOrderByVersionDesc("myForm", "")).thenReturn(null)
        whenever(repo.save(any(FormEntity::class.java))).thenAnswer { it.arguments[0] as FormEntity }

        val result = service.deployForm("form.bform", schemaWithId.toByteArray(), "dep-1", "")

        // Weak assertion: checking that the raw JSON string contains "amount" is fragile.
        // It would pass even if serialization is malformed. Should deserialize and assert
        // on the actual list, e.g. objectMapper.readValue(result.variableBindings, List::class.java).
        // Also, this test largely duplicates FormVariableExtractorTest — the extractor
        // is already unit-tested directly, so testing it again through FormService adds
        // little value.
        assertNotNull(result.variableBindings)
        assertTrue(result.variableBindings!!.contains("amount"))
    }

    @Test
    fun `deployForm - throws when schema has no id`() {
        assertThrows<IllegalArgumentException> {
            service.deployForm("form.bform", schemaNoId.toByteArray(), "dep-1", "")
        }
    }

    @Test
    fun `getLatestForm - throws when form not found`() {
        whenever(repo.findTopByKeyAndTenantIdOrderByVersionDesc("ghost", "")).thenReturn(null)

        assertThrows<NoSuchElementException> { service.getForm(IdOrKey.Key("ghost")) }
    }

    // MISSING: no happy-path test for getLatestForm (only the not-found case is covered).
    // MISSING: no test for listForms at all.
    // MISSING: no test that deployForm respects tenantId scoping (version query passes tenant).

    private fun entity(version: Int = 1) =
        FormEntity(
            id = UUID.randomUUID(),
            key = "myForm",
            version = version,
            schema = schemaWithIdDto,
            deploymentId = "dep-1",
            resourceName = "form.bform",
            resourceBytes = ByteArray(0),
        )
}
