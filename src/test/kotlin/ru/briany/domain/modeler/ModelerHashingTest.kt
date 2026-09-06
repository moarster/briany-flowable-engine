package ru.briany.domain.modeler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ModelerHashingTest {
    @Test
    fun `sha256Hex matches the well-known empty-input digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ModelerHashing.sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `sha256Hex is stable for the same content`() {
        val bytes = "modeler".toByteArray()
        assertEquals(ModelerHashing.sha256Hex(bytes), ModelerHashing.sha256Hex(bytes))
    }

    @Test
    fun `aggregate is order-independent over file keys`() {
        val ascending = listOf("a" to "h1", "b" to "h2", "c" to "h3")
        val shuffled = listOf("c" to "h3", "a" to "h1", "b" to "h2")
        assertEquals(ModelerHashing.aggregate(ascending), ModelerHashing.aggregate(shuffled))
    }

    @Test
    fun `aggregate changes when a content hash changes`() {
        val before = listOf("a" to "h1", "b" to "h2")
        val after = listOf("a" to "h1", "b" to "h2-changed")
        assertNotEquals(ModelerHashing.aggregate(before), ModelerHashing.aggregate(after))
    }
}
