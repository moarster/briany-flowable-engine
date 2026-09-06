package ru.briany.domain.modeler

import java.security.MessageDigest

/**
 * Content-addressable hashing for modeler files. The file content is the source of truth;
 * synchronization state is derived from SHA-256 hashes.
 */
object ModelerHashing {
    private const val ALGORITHM = "SHA-256"
    private const val HEX_RADIX = 16
    private const val BYTE_MASK = 0xff
    private const val HEX_PADDED_WIDTH = 2

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM).digest(bytes)
        return buildString(digest.size * HEX_PADDED_WIDTH) {
            digest.forEach { byte ->
                val value = byte.toInt() and BYTE_MASK
                append(value.toString(HEX_RADIX).padStart(HEX_PADDED_WIDTH, '0'))
            }
        }
    }

    /**
     * Aggregate hash over the whole workspace. Covers add/remove/change of files in one column:
     * sha256hex( sortedByFileKey.joinToString("\n") { "fileKey:contentHash" } ).
     */
    fun aggregate(fileHashes: List<Pair<String, String>>): String {
        val joined =
            fileHashes
                .sortedBy { it.first }
                .joinToString("\n") { "${it.first}:${it.second}" }
        return sha256Hex(joined.toByteArray(Charsets.UTF_8))
    }
}
