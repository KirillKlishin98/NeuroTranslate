package org.school.neurotranslate.ui.translation

enum class TranslationLanguage {
    Russian,
    English
}

data class TranslationUiState(
    val inputText: String = "",
    val sourceLanguage: TranslationLanguage = TranslationLanguage.Russian,
    val targetLanguage: TranslationLanguage = TranslationLanguage.English,
    val isCheckingModel: Boolean = false,
    val showModelPrompt: Boolean = false,
    val showEmptyInputMessage: Boolean = false,
    val showTranslationUnavailable: Boolean = false,
    val showModelStatusError: Boolean = false
)
