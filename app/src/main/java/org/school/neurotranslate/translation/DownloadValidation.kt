package org.school.neurotranslate.translation

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

internal data class DownloadResumeValidator(val etag: String?, val lastModified: String?)

internal object DownloadRangePolicy {
    private val contentRangePattern = Regex("bytes (\\d+)-(\\d+)/(\\d+)")

    fun accepts(
        statusCode: Int,
        contentRange: String?,
        offset: Long,
        expectedSize: Long,
        savedValidator: DownloadResumeValidator?,
        responseValidator: DownloadResumeValidator?
    ): Boolean {
        if (statusCode != 206 || savedValidator == null || responseValidator == null) return false
        val range = contentRangePattern.matchEntire(contentRange.orEmpty()) ?: return false
        val matchesValidator = if (savedValidator.etag != null) {
            responseValidator.etag == savedValidator.etag
        } else {
            responseValidator.lastModified == savedValidator.lastModified
        }
        return range.groupValues[1].toLongOrNull() == offset &&
            range.groupValues[2].toLongOrNull() == expectedSize - 1L &&
            range.groupValues[3].toLongOrNull() == expectedSize &&
            matchesValidator
    }
}

internal object ArtifactDigest {
    fun matches(file: File, expectedSize: Long, expectedSha256: String): Boolean =
        file.isFile && file.length() == expectedSize && sha256(file) == expectedSha256.lowercase(Locale.ROOT)

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private const val HASH_BUFFER_BYTES = 64 * 1024
}
