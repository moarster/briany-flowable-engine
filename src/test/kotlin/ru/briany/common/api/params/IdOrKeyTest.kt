package ru.briany.common.api.params

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IdOrKeyTest {
    @Test
    fun `lowercase UUID parsed as Id`() {
        val result = IdOrKey.parse("550e8400-e29b-41d4-a716-446655440000")
        assertTrue(result is IdOrKey.Id)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", (result as IdOrKey.Id).value)
    }

    @Test
    fun `uppercase UUID parsed as Id`() {
        val result = IdOrKey.parse("550E8400-E29B-41D4-A716-446655440000")
        assertTrue(result is IdOrKey.Id)
    }

    @Test
    fun `mixed case UUID parsed as Id`() {
        val result = IdOrKey.parse("550e8400-E29B-41d4-a716-446655440000")
        assertTrue(result is IdOrKey.Id)
    }

    @Test
    fun `simple string parsed as Key`() {
        val result = IdOrKey.parse("my-app")
        assertTrue(result is IdOrKey.Key)
        assertEquals("my-app", (result as IdOrKey.Key).value)
    }

    @Test
    fun `numeric string parsed as Key`() {
        val result = IdOrKey.parse("12345")
        assertTrue(result is IdOrKey.Key)
    }

    @Test
    fun `UUID without dashes parsed as Key`() {
        val result = IdOrKey.parse("550e8400e29b41d4a716446655440000")
        assertTrue(result is IdOrKey.Key)
    }

    @Test
    fun `UUID with extra characters parsed as Key`() {
        val result = IdOrKey.parse("550e8400-e29b-41d4-a716-446655440000-extra")
        assertTrue(result is IdOrKey.Key)
    }

    @Test
    fun `empty-like UUID structure with wrong length parsed as Key`() {
        val result = IdOrKey.parse("550e8400-e29b-41d4-a716-44665544000")
        assertTrue(result is IdOrKey.Key)
    }
}
