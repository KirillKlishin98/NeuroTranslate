package org.school.neurotranslate.ui.catalog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.school.neurotranslate.R
import org.school.neurotranslate.translation.ModelDownloadRuntime
import org.school.neurotranslate.translation.ModelDownloadScheduler
import org.school.neurotranslate.translation.TranslationService
import org.school.neurotranslate.translation.onnx.MarianOnnxEngine

class ModelCatalogViewModel(application: Application) : AndroidViewModel(application) {
    private val downloadRuntime = ModelDownloadRuntime.get(application)
    private val catalog = downloadRuntime.catalog
    private val modelRepository = downloadRuntime.repository
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
    private val refreshedDownloads = mutableSetOf<String>()

    init {
        refreshInstalledModels()
        viewModelScope.launch {
            downloadRuntime.status.collect { statuses ->
                _uiState.update { state ->
                    state.copy(items = state.items.map { item ->
                        val status = statuses[item.id] ?: return@map item
                        item.copy(
                            isDownloading = status.isRunning,
                            downloadProgress = status.progress,
                            errorMessage = if (status.error) getApplication<Application>().getString(R.string.model_download_failed) else null
                        )
                    })
                }
                val newlyCompleted = statuses.filterValues { !it.isRunning && it.progress >= 1f }
                    .keys.any(refreshedDownloads::add)
                if (newlyCompleted) {
                    refreshInstalledModels()
                }
            }
        }
    }

    fun onInstall(modelId: String) {
        if (_uiState.value.items.any { it.isDownloading }) return
        refreshedDownloads.remove(modelId)
        updateItem(modelId) { it.copy(isDownloading = true, downloadProgress = 0f, errorMessage = null) }
        viewModelScope.launch {
            try {
                ModelDownloadScheduler.enqueue(getApplication(), modelId)
            } catch (failure: Exception) {
                updateItem(modelId) {
                    it.copy(isDownloading = false, errorMessage = getApplication<Application>().getString(R.string.model_download_failed))
                }
            }
        }
    }

    fun onCancelDownload(modelId: String) {
        if (_uiState.value.items.none { it.id == modelId && it.isDownloading }) return
        ModelDownloadScheduler.cancel(getApplication(), modelId)
    }

    fun onRemove(modelId: String) {
        if (_uiState.value.items.any { it.id == modelId && it.isDownloading }) return
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
        const val BYTES_PER_MEGABYTE = 1024L * 1024L
    }
}
