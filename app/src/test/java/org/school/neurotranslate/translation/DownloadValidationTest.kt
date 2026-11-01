package org.school.neurotranslate.translation

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadValidationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun acceptsRangeOnlyWithCorrectStatusRangeAndEtag() {
        assertTrue(
            DownloadRangePolicy.accepts(
                statusCode = 206,
                contentRange = "bytes 300-999/1000",
                offset = 300,
                expectedSize = 1000,
                savedValidator = DownloadResumeValidator(etag = "\"revision-1\"", lastModified = null),
                responseValidator = DownloadResumeValidator(etag = "\"revision-1\"", lastModified = null)
            )
        )
        assertFalse(acceptsRange(status = 200, range = "bytes 300-999/1000"))
        assertFalse(acceptsRange(status = 206, range = "bytes 299-999/1000"))
        assertFalse(
            DownloadRangePolicy.accepts(
                statusCode = 206,
                contentRange = "bytes 300-999/1000",
                offset = 300,
                expectedSize = 1000,
                savedValidator = DownloadResumeValidator(etag = "\"revision-1\"", lastModified = null),
                responseValidator = DownloadResumeValidator(etag = "\"revision-2\"", lastModified = null)
            )
        )
    }

    @Test
    fun acceptsMatchingLastModifiedWhenEtagIsUnavailable() {
        assertTrue(
            DownloadRangePolicy.accepts(
                statusCode = 206,
                contentRange = "bytes 300-999/1000",
                offset = 300,
                expectedSize = 1000,
                savedValidator = DownloadResumeValidator(etag = null, lastModified = "Sat, 31 Oct 2026 10:00:00 GMT"),
                responseValidator = DownloadResumeValidator(etag = null, lastModified = "Sat, 31 Oct 2026 10:00:00 GMT")
            )
        )
    }

    @Test
    fun validatesArtifactSizeAndSha256() {
        val artifact = File(temporaryFolder.root, "artifact.bin").apply { writeText("abc") }
        val validSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertTrue(ArtifactDigest.matches(artifact, expectedSize = 3, expectedSha256 = validSha256))
        assertFalse(ArtifactDigest.matches(artifact, expectedSize = 4, expectedSha256 = validSha256))
        assertFalse(ArtifactDigest.matches(artifact, expectedSize = 3, expectedSha256 = "0".repeat(64)))
    }

    private fun acceptsRange(status: Int, range: String) = DownloadRangePolicy.accepts(
        statusCode = status,
        contentRange = range,
        offset = 300,
        expectedSize = 1000,
        savedValidator = DownloadResumeValidator(etag = "\"revision-1\"", lastModified = null),
        responseValidator = DownloadResumeValidator(etag = "\"revision-1\"", lastModified = null)
    )
}
