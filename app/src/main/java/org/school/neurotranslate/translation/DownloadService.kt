package org.school.neurotranslate.translation

import android.os.StatFs
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface DownloadService {
    suspend fun downloadModel(modelId: String, onProgress: (DownloadProgress) -> Unit = {})
}

data class DownloadProgress(val modelId: String, val downloadedBytes: Long, val totalBytes: Long) {
    val fraction: Float
        get() = if (totalBytes == 0L) 0f else (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}

class ResumableModelDownloadService(
    private val catalog: ModelCatalog,
    private val modelRepository: ModelRepository,
    private val validator: ModelPackageValidator,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DownloadService {
    private val mutex = Mutex()

    override suspend fun downloadModel(modelId: String, onProgress: (DownloadProgress) -> Unit) =
        withContext(ioDispatcher) {
            mutex.withLock {
                val model = catalog.get(modelId)
                modelRepository.recoverInterruptedInstalls()
                if (modelRepository.findInstalledModel(modelId) != null) return@withLock
                val staging = modelRepository.createStagingDirectory(modelId)
                checkAvailableSpace(model, staging)
                var completedBytes = 0L
                onProgress(DownloadProgress(modelId, 0L, model.totalBytes))
                try {
                    for (artifact in model.artifacts) {
                        currentCoroutineContext().ensureActive()
                        downloadArtifact(model, artifact, staging, completedBytes, onProgress)
                        completedBytes += artifact.sizeBytes
                        onProgress(DownloadProgress(modelId, completedBytes, model.totalBytes))
                    }
                    modelRepository.writeStagedManifest(modelId, staging)
                    validator.validate(model, staging)
                    currentCoroutineContext().ensureActive()
                    modelRepository.installStagedModel(modelId, staging)
                    onProgress(DownloadProgress(modelId, model.totalBytes, model.totalBytes))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: ModelDownloadException) {
                    throw failure
                } catch (failure: IOException) {
                    throw ModelDownloadException.Transfer(modelId, failure)
                }
            }
        }

    private suspend fun downloadArtifact(
        model: ModelPackageSpec,
        artifact: ModelArtifactSpec,
        staging: File,
        completedBytes: Long,
        onProgress: (DownloadProgress) -> Unit
    ) {
        val destination = childFile(staging, artifact.path)
        val partial = File(destination.parentFile, "${destination.name}.part")
        val validatorFile = validatorFile(staging, artifact.path)
        destination.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create staging folder.") }
        if (destination.isFile && destination.length() == artifact.sizeBytes && sha256(destination) == artifact.sha256) return
        if (destination.exists() && !destination.delete()) throw IOException("Could not replace incomplete artifact.")

        val savedValidator = readValidator(validatorFile)
        var offset = if (savedValidator != null && partial.isFile && partial.length() < artifact.sizeBytes) partial.length() else 0L
        if (offset == 0L) deleteIfPresent(validatorFile)

        var connection = openConnection(artifact.url, offset, savedValidator.takeIf { offset > 0L })
        var code = connection.responseCode
        var append = false
        if (offset > 0L && code == HttpURLConnection.HTTP_PARTIAL && validPartial(connection, offset, artifact, savedValidator)) {
            append = true
        } else if (offset > 0L) {
            connection.disconnect()
            offset = 0L
            connection = openConnection(artifact.url, 0L, null)
            code = connection.responseCode
        }
        if (code != HttpURLConnection.HTTP_OK && !(append && code == HttpURLConnection.HTTP_PARTIAL)) {
            connection.disconnect()
            throw IOException("Download request for ${artifact.path} returned HTTP $code.")
        }

        try {
            if (!append) {
                validateFullResponse(connection, artifact)
                deleteIfPresent(partial)
            }
            writeValidator(validatorFile, connection)
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            if (contentLength >= 0L && offset + contentLength != artifact.sizeBytes) {
                throw IOException("Unexpected content length for ${artifact.path}.")
            }
            val context = currentCoroutineContext()
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(TRANSFER_BUFFER_BYTES)
                    var currentBytes = offset
                    while (true) {
                        context.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        currentBytes += read
                        if (currentBytes > artifact.sizeBytes) throw IOException("Downloaded ${artifact.path} exceeds its trusted size.")
                        output.write(buffer, 0, read)
                        onProgress(DownloadProgress(model.id, completedBytes + currentBytes, model.totalBytes))
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
        } finally {
            connection.disconnect()
        }

        if (!partial.isFile || partial.length() != artifact.sizeBytes) {
            throw EOFException("Download of ${artifact.path} is incomplete; retry to resume it.")
        }
        if (sha256(partial) != artifact.sha256) {
            deleteIfPresent(partial)
            deleteIfPresent(validatorFile)
            throw ModelDownloadException.Integrity(model.id, artifact.path)
        }
        if (!partial.renameTo(destination)) throw IOException("Could not finalize ${artifact.path}.")
        deleteIfPresent(validatorFile)
    }

    private fun openConnection(url: String, offset: Long, validator: ResumeValidator?): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = true
            useCaches = false
            requestMethod = "GET"
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("User-Agent", USER_AGENT)
            if (offset > 0L && validator != null) {
                setRequestProperty("Range", "bytes=$offset-")
                setRequestProperty("If-Range", validator.etag ?: validator.lastModified)
            }
        }

    private fun validPartial(
        connection: HttpURLConnection,
        offset: Long,
        artifact: ModelArtifactSpec,
        saved: ResumeValidator?
    ): Boolean {
        if (saved == null) return false
        val range = CONTENT_RANGE.matchEntire(connection.getHeaderField("Content-Range") ?: "") ?: return false
        val actualValidator = responseValidator(connection)
        val validatorMatches = if (saved.etag != null) {
            actualValidator?.etag == saved.etag
        } else {
            actualValidator?.lastModified == saved.lastModified
        }
        return range.groupValues[1].toLongOrNull() == offset &&
            range.groupValues[2].toLongOrNull() == artifact.sizeBytes - 1L &&
            range.groupValues[3].toLongOrNull() == artifact.sizeBytes &&
            validatorMatches
    }

    private fun validateFullResponse(connection: HttpURLConnection, artifact: ModelArtifactSpec) {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Expected a full HTTP response.")
        val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
        if (contentLength >= 0L && contentLength != artifact.sizeBytes) throw IOException("Unexpected content length for ${artifact.path}.")
    }

    private fun responseValidator(connection: HttpURLConnection) = ResumeValidator(
        etag = connection.getHeaderField("ETag")?.takeIf(String::isNotBlank),
        lastModified = connection.getHeaderField("Last-Modified")?.takeIf(String::isNotBlank)
    ).takeIf { it.etag != null || it.lastModified != null }

    private fun readValidator(file: File): ResumeValidator? {
        if (!file.isFile) return null
        val properties = Properties()
        FileInputStream(file).use(properties::load)
        return ResumeValidator(
            etag = properties.getProperty(ETAG_KEY),
            lastModified = properties.getProperty(LAST_MODIFIED_KEY)
        ).takeIf { it.etag != null || it.lastModified != null }
    }

    private fun writeValidator(file: File, connection: HttpURLConnection) {
        val validator = responseValidator(connection)
        if (validator == null) {
            deleteIfPresent(file)
            return
        }
        file.parentFile?.let { if (!it.exists() && !it.mkdirs()) throw IOException("Could not create resume metadata folder.") }
        val properties = Properties().apply {
            validator.etag?.let { setProperty(ETAG_KEY, it) }
            validator.lastModified?.let { setProperty(LAST_MODIFIED_KEY, it) }
        }
        FileOutputStream(file).use { output ->
            properties.store(output, null)
            output.fd.sync()
        }
    }

    private fun validatorFile(staging: File, path: String): File = childFile(staging, "$RESUME_DIRECTORY/$path.properties")

    private fun childFile(directory: File, path: String): File {
        val child = File(directory, path).canonicalFile
        check(child.path.startsWith(directory.canonicalPath + File.separator)) { "Artifact path escaped its staging directory." }
        return child
    }

    private fun sha256(file: File): String {
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

    private fun checkAvailableSpace(model: ModelPackageSpec, staging: File) {
        val reusableBytes = model.artifacts.sumOf { artifact ->
            val destination = childFile(staging, artifact.path)
            val partial = File(destination.parentFile, "${destination.name}.part")
            when {
                destination.isFile && destination.length() == artifact.sizeBytes -> artifact.sizeBytes
                partial.isFile && readValidator(validatorFile(staging, artifact.path)) != null && partial.length() < artifact.sizeBytes -> partial.length()
                else -> 0L
            }
        }
        val needed = (model.totalBytes - reusableBytes).coerceAtLeast(0L) + STORAGE_SAFETY_BYTES
        val available = StatFs(staging.absolutePath).availableBytes
        if (available < needed) throw ModelDownloadException.InsufficientStorage(model.id, needed, available)
    }

    private fun deleteIfPresent(file: File) {
        if (file.exists() && !file.delete()) throw IOException("Could not remove ${file.name}.")
    }

    private data class ResumeValidator(val etag: String?, val lastModified: String?)

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 20_000
        const val READ_TIMEOUT_MILLIS = 30_000
        const val TRANSFER_BUFFER_BYTES = 128 * 1024
        const val HASH_BUFFER_BYTES = 64 * 1024
        const val STORAGE_SAFETY_BYTES = 16L * 1024 * 1024
        const val ETAG_KEY = "etag"
        const val LAST_MODIFIED_KEY = "lastModified"
        const val RESUME_DIRECTORY = ".resume"
        const val USER_AGENT = "NeuroTranslate/1.0"
        val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)")
    }
}

sealed class ModelDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Transfer(modelId: String, cause: IOException) :
        ModelDownloadException("Download for $modelId failed; retry to resume it.", cause)

    class Integrity(modelId: String, artifactPath: String) :
        ModelDownloadException("SHA-256 validation failed for $artifactPath in $modelId.")

    class InsufficientStorage(modelId: String, requiredBytes: Long, availableBytes: Long) :
        ModelDownloadException("Not enough storage for $modelId: need $requiredBytes bytes, have $availableBytes.")
}
