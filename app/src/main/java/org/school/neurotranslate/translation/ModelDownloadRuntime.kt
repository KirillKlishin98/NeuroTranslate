package org.school.neurotranslate.translation

import android.app.Application
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ModelDownloadStatus(
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val error: Boolean = false
)

class ModelDownloadRuntime private constructor(application: Application) {
    val catalog = ModelCatalog.fromAssets(application.assets)
    val repository = FileSystemModelRepository(
        rootDirectory = File(application.filesDir, MODEL_STORAGE_DIRECTORY),
        catalog = catalog
    )
    private val downloadService = ResumableModelDownloadService(
        catalog = catalog,
        modelRepository = repository,
        validator = OnnxModelPackageValidator()
    )

    private val _status = MutableStateFlow<Map<String, ModelDownloadStatus>>(emptyMap())
    val status = _status.asStateFlow()

    suspend fun download(modelId: String, onProgress: (DownloadProgress) -> Unit = {}) {
        setStatus(modelId, ModelDownloadStatus(isRunning = true))
        var lastProgressAt = 0L
        var lastProgressPercent = -1
        try {
            downloadService.downloadModel(modelId) { progress ->
                val now = android.os.SystemClock.elapsedRealtime()
                val percent = (progress.fraction * 100).toInt()
                if (progress.fraction >= 1f ||
                    (percent != lastProgressPercent && now - lastProgressAt >= PROGRESS_UPDATE_INTERVAL_MILLIS)
                ) {
                    lastProgressAt = now
                    lastProgressPercent = percent
                    setStatus(modelId, ModelDownloadStatus(isRunning = true, progress = progress.fraction))
                    onProgress(progress)
                }
            }
            setStatus(modelId, ModelDownloadStatus(progress = 1f))
        } catch (cancelled: CancellationException) {
            setStatus(modelId, ModelDownloadStatus())
            throw cancelled
        } catch (failure: Exception) {
            setStatus(modelId, ModelDownloadStatus(error = true))
            throw failure
        }
    }

    fun statusFor(modelId: String): ModelDownloadStatus = status.value[modelId] ?: ModelDownloadStatus()

    fun markFailed(modelId: String) {
        setStatus(modelId, ModelDownloadStatus(error = true))
    }

    fun markCancelled(modelId: String) {
        setStatus(modelId, ModelDownloadStatus())
    }

    private fun setStatus(modelId: String, value: ModelDownloadStatus) {
        _status.update { it + (modelId to value) }
    }

    companion object {
        private const val MODEL_STORAGE_DIRECTORY = "models"
        private const val PROGRESS_UPDATE_INTERVAL_MILLIS = 1_000L

        @Volatile
        private var instance: ModelDownloadRuntime? = null

        fun get(application: Application): ModelDownloadRuntime = instance ?: synchronized(this) {
            instance ?: ModelDownloadRuntime(application).also { instance = it }
        }
    }
}
