package ru.briany.common.archive

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Useful flowable app archive unzipper
 */
fun InputStream.readZipEntries(): List<Pair<String, ByteArray>> =
    ZipInputStream(this).use { zip ->
        generateSequence { zip.nextEntry }
            .filterNot { it.isDirectory }
            .map { it.name to zip.readBytes() }
            .toList()
    }
