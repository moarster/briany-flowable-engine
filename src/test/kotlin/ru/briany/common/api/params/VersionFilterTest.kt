package ru.briany.common.api.params

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VersionFilterTest {
    @Test
    fun `null returns Latest`() {
        assertEquals(VersionFilter.Latest, VersionFilter.parse(null))
    }

    @Test
    fun `latest string returns Latest`() {
        assertEquals(VersionFilter.Latest, VersionFilter.parse("latest"))
    }

    @Test
    fun `latest is case insensitive`() {
        assertEquals(VersionFilter.Latest, VersionFilter.parse("LATEST"))
        assertEquals(VersionFilter.Latest, VersionFilter.parse("Latest"))
    }

    @Test
    fun `all string returns All`() {
        assertEquals(VersionFilter.All, VersionFilter.parse("all"))
    }

    @Test
    fun `all is case insensitive`() {
        assertEquals(VersionFilter.All, VersionFilter.parse("ALL"))
        assertEquals(VersionFilter.All, VersionFilter.parse("All"))
    }

    @Test
    fun `numeric string returns Exact`() {
        assertEquals(VersionFilter.Exact(1), VersionFilter.parse("1"))
        assertEquals(VersionFilter.Exact(42), VersionFilter.parse("42"))
        assertEquals(VersionFilter.Exact(0), VersionFilter.parse("0"))
    }

    @Test
    fun `negative number returns Exact`() {
        assertEquals(VersionFilter.Exact(-1), VersionFilter.parse("-1"))
    }

    @Test
    fun `non-numeric non-keyword throws`() {
        val ex =
            assertThrows<IllegalArgumentException> {
                VersionFilter.parse("foo")
            }
        assertTrue(ex.message!!.contains("foo"))
    }

    @Test
    fun `empty string throws`() {
        assertThrows<IllegalArgumentException> {
            VersionFilter.parse("")
        }
    }

    @Test
    fun `decimal string throws`() {
        assertThrows<IllegalArgumentException> {
            VersionFilter.parse("1.5")
        }
    }
}
