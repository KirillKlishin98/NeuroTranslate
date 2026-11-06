package org.school.neurotranslate.ui.translation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.school.neurotranslate.translation.ModelDownloadRuntime

class TranslationViewModel(application: Application) : AndroidViewModel(application) {
    private val modelRepository = ModelDownloadRuntime.get(application).repository
    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.update {
            it.copy(
                inputText = text,
                showEmptyInputMessage = false,
                showModelPrompt = false,
                showTranslationUnavailable = false,
                showModelStatusError = false
            )
        }
    }

    fun onSwapLanguages() {
        _uiState.update {
            it.copy(
                sourceLanguage = it.targetLanguage,
                targetLanguage = it.sourceLanguage,
                showModelPrompt = false,
                showTranslationUnavailable = false,
                showModelStatusError = false
            )
        }
    }

    fun onTranslate() {
        val state = _uiState.value
        if (state.isCheckingModel) return

        _uiState.update {
            if (it.inputText.isBlank()) {
                it.copy(
                    showEmptyInputMessage = true,
                    showModelPrompt = false,
                    showTranslationUnavailable = false,
                    showModelStatusError = false
                )
            } else {
                it.copy(
                    showEmptyInputMessage = false,
                    isCheckingModel = true,
                    showModelPrompt = false,
                    showTranslationUnavailable = false,
                    showModelStatusError = false
                )
            }
        }

        if (state.inputText.isBlank()) return

        val modelId = when (state.sourceLanguage to state.targetLanguage) {
            TranslationLanguage.Russian to TranslationLanguage.English -> MODEL_RU_EN
            TranslationLanguage.English to TranslationLanguage.Russian -> MODEL_EN_RU
            else -> return
        }
        viewModelScope.launch {
            try {
                val isInstalled = modelRepository.findInstalledModel(modelId) != null
                _uiState.update {
                    it.copy(
                        isCheckingModel = false,
                        showModelPrompt = !isInstalled,
                        showTranslationUnavailable = isInstalled
                    )
                }
            } catch (cancellation: CancellationException) {
                _uiState.update { it.copy(isCheckingModel = false) }
                throw cancellation
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isCheckingModel = false, showModelStatusError = true)
                }
            }
        }
    }

    private companion object {
        const val MODEL_RU_EN = "ru-en"
        const val MODEL_EN_RU = "en-ru"
    }
}
