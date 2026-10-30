package org.school.neurotranslate.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.school.neurotranslate.R
import org.school.neurotranslate.translation.FileSystemModelRepository
import org.school.neurotranslate.translation.MarianOnnxEngine
import org.school.neurotranslate.translation.ModelCatalog
import org.school.neurotranslate.translation.OnnxModelPackageValidator
import org.school.neurotranslate.translation.ResumableModelDownloadService
import org.school.neurotranslate.translation.TranslationService

class ModelCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val catalog = ModelCatalog.fromAssets(application.assets)
    private val modelRepository = FileSystemModelRepository(
        rootDirectory = File(application.filesDir, MODEL_STORAGE_DIRECTORY),
        catalog = catalog
    )
    private val downloadService = ResumableModelDownloadService(
        catalog = catalog,
        modelRepository = modelRepository,
        validator = OnnxModelPackageValidator()
    )
    private val translationService = TranslationService(
        engine = MarianOnnxEngine(),
        modelRepository = modelRepository
    )
    private val _uiState = MutableStateFlow(
        ModelCatalogUiState(items = catalog.all().map { spec ->
            ModelCatalogItem(
                id = spec.id,
                direction = if (spec.sourceLanguage == "ru") {
                    ModelDirection.RussianToEnglish
                } else {
                    ModelDirection.EnglishToRussian
                },
                packageSizeBytes = spec.totalBytes
            )
        })
    )
    val uiState = _uiState.asStateFlow()

    private var currentDownload: Job? = null

    init {
        refreshInstalledModels()
    }

    fun onInstall(modelId: String) {
        if (currentDownload?.isActive == true) return
        currentDownload = viewModelScope.launch {
            updateItem(modelId) { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null) }
            try {
                downloadService.downloadModel(modelId) { progress ->
                    updateItem(modelId) { it.copy(downloadProgress = progress.fraction) }
                }
                updateItem(modelId) { it.copy(isDownloading = false, isInstalled = true, downloadProgress = 1f) }
            } catch (cancellation: CancellationException) {
                updateItem(modelId) { it.copy(isDownloading = false) }
                throw cancellation
            } catch (failure: Exception) {
                updateItem(modelId) {
                    it.copy(isDownloading = false, errorMessage = getApplication<Application>().getString(R.string.model_download_failed))
                }
            } finally {
                currentDownload = null
                refreshInstalledModels()
            }
        }
    }

    fun onCancelDownload(modelId: String) {
        if (_uiState.value.items.none { it.id == modelId && it.isDownloading }) return
        currentDownload?.cancel()
    }

    fun onRemove(modelId: String) {
        if (currentDownload?.isActive == true) return
        viewModelScope.launch {
            updateItem(modelId) { it.copy(isDeleting = true, errorMessage = null) }
            try {
                translationService.deleteModel(modelId)
                updateItem(modelId) { it.copy(isDeleting = false, isInstalled = false) }
            } catch (cancellation: CancellationException) {
                updateItem(modelId) { it.copy(isDeleting = false) }
                throw cancellation
            } catch (failure: Exception) {
                updateItem(modelId) {
                    it.copy(isDeleting = false, errorMessage = getApplication<Application>().getString(R.string.model_delete_failed))
                }
            }
            refreshInstalledModels()
        }
    }

    private fun refreshInstalledModels() {
        viewModelScope.launch {
            try {
                val installed = modelRepository.installedModelIds()
                val diskBytes = modelRepository.installedBytes()
                _uiState.update { state ->
                    state.copy(
                        items = state.items.map { item ->
                            item.copy(isInstalled = item.id in installed, isDeleting = false)
                        },
                        installedCount = installed.size,
                        diskUsageMegabytes = ((diskBytes + BYTES_PER_MEGABYTE - 1) / BYTES_PER_MEGABYTE).toInt(),
                        isLoading = false
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private inline fun updateItem(modelId: String, transform: (ModelCatalogItem) -> ModelCatalogItem) {
        _uiState.update { state ->
            state.copy(items = state.items.map { if (it.id == modelId) transform(it) else it })
        }
    }

    private companion object {
        const val MODEL_STORAGE_DIRECTORY = "models"
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
