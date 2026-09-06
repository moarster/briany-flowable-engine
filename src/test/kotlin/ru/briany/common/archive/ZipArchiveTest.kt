package ru.briany.common.archive

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipArchiveTest {
    @Test
    fun `readZipEntries returns each file entry name and content`() {
        val zip =
            buildZip {
                file("a.bpmn", "alpha")
                file("nested/b.bform", "beta")
            }

        val entries = ByteArrayInputStream(zip).readZipEntries()

        assertEquals(2, entries.size)
        assertEquals(listOf("a.bpmn", "nested/b.bform"), entries.map { it.first })
        assertEquals("alpha", String(entries[0].second))
        assertEquals("beta", String(entries[1].second))
    }

    @Test
    fun `readZipEntries skips directory entries`() {
        val zip =
            buildZip {
                directory("dir/")
                file("dir/c.flw", "gamma")
            }

        val entries = ByteArrayInputStream(zip).readZipEntries()

        assertEquals(listOf("dir/c.flw"), entries.map { it.first })
    }

    @Test
    fun `readZipEntries returns empty list for empty archive`() {
        val zip = buildZip { }

        val entries = ByteArrayInputStream(zip).readZipEntries()

        assertTrue(entries.isEmpty())
    }

    private fun buildZip(block: ZipBuilder.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> ZipBuilder(zip).block() }
        return out.toByteArray()
    }

    private class ZipBuilder(
        private val zip: ZipOutputStream,
    ) {
        fun file(
            name: String,
            content: String,
        ) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray())
            zip.closeEntry()
        }

        fun directory(name: String) {
            zip.putNextEntry(ZipEntry(name))
            zip.closeEntry()
        }
    }
}
