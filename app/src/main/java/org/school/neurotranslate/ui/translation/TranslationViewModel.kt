package org.school.neurotranslate.ui.translation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TranslationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TranslationUiState())
    val uiState = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.update {
            it.copy(
                inputText = text,
                showEmptyInputMessage = false,
                showModelPrompt = false
            )
        }
    }

    fun onSwapLanguages() {
        _uiState.update {
            it.copy(
                sourceLanguage = it.targetLanguage,
                targetLanguage = it.sourceLanguage
            )
        }
    }

    fun onTranslate() {
        _uiState.update {
            if (it.inputText.isBlank()) {
                it.copy(showEmptyInputMessage = true, showModelPrompt = false)
            } else {
                it.copy(showEmptyInputMessage = false, showModelPrompt = true)
            }
        }
    }
}
