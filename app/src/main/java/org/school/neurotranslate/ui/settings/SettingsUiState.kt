package org.school.neurotranslate.ui.settings

enum class SettingsThemeMode {
    System,
    Light,
    Dark
}

data class SettingsUiState(
    val themeMode: SettingsThemeMode = SettingsThemeMode.System,
    val maxOutputLength: Int = 256
)
