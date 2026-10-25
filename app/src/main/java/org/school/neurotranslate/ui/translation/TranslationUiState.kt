package org.school.neurotranslate.ui.translation

enum class TranslationLanguage {
    Russian,
    English
}

data class TranslationUiState(
    val inputText: String = "",
    val sourceLanguage: TranslationLanguage = TranslationLanguage.Russian,
    val targetLanguage: TranslationLanguage = TranslationLanguage.English,
    val showModelPrompt: Boolean = false,
    val showEmptyInputMessage: Boolean = false
)
