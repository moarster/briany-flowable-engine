package ru.briany.domain.modeler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import ru.briany.generated.model.ModelerFileType
import tools.jackson.databind.json.JsonMapper

class ModelerFileIntrospectorTest {
    private val introspector = ModelerFileIntrospector(JsonMapper.builder().build())

    private fun resource(name: String): ByteArray =
        javaClass.classLoader.getResourceAsStream("modeler/$name")!!.readAllBytes()

    @Test
    fun `bpmn with a single process derives key, name and description`() {
        val result = introspector.introspect("process-a.bpmn", resource("process-a.bpmn"))

        assertEquals(ModelerFileType.BPMN, result.type)
        assertEquals("modelerProcessA", result.fileKey)
        assertEquals("Modeler Process A", result.name)
        assertEquals("First modeler process", result.description)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `bpmn with multiple processes fails validation`() {
        val result = introspector.introspect("multi-process.bpmn", resource("multi-process.bpmn"))

        assertNull(result.fileKey)
        assertEquals(ModelerFileIntrospector.ELEMENT_COUNT, result.errors.single().code)
    }

    @Test
    fun `dmn with a single decision derives key and name`() {
        val result = introspector.introspect("decision-a.dmn", resource("decision-a.dmn"))

        assertEquals(ModelerFileType.DMN, result.type)
        assertEquals("modelerDecisionA", result.fileKey)
        assertEquals("Modeler Decision A", result.name)
    }

    @Test
    fun `bform derives key from id and name from first component label`() {
        val result = introspector.introspect("form-a.bform", resource("form-a.bform"))

        assertEquals(ModelerFileType.BFORM, result.type)
        assertEquals("modelerFormA", result.fileKey)
        assertEquals("Field one", result.name)
    }

    @Test
    fun `unsupported extension is rejected`() {
        assertThrows(ResponseStatusException::class.java) {
            introspector.introspect("notes.txt", "hello".toByteArray())
        }
    }

    @Test
    fun `malformed json bform fails validation`() {
        val result = introspector.introspect("broken.bform", "{ not json".toByteArray())

        assertNull(result.fileKey)
        assertEquals(ModelerFileIntrospector.PARSE_ERROR, result.errors.single().code)
    }
}
