package org.school.neurotranslate.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.school.neurotranslate.data.settings.SettingsRepository
import org.school.neurotranslate.settings.normalizeMaxOutputLength

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository.from(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _uiState.update {
                    it.copy(
                        themeMode = SettingsThemeMode.entries.firstOrNull { mode -> mode.name == settings.themeModeName }
                            ?: SettingsThemeMode.System,
                        maxOutputLength = settings.maxOutputLength
                    )
                }
            }
        }
    }

    fun onThemeModeChanged(themeMode: SettingsThemeMode) {
        _uiState.update { it.copy(themeMode = themeMode) }
        viewModelScope.launch { settingsRepository.setThemeMode(themeMode.name) }
    }

    fun onMaxOutputLengthChanged(value: Float) {
        val length = normalizeMaxOutputLength(value)
        _uiState.update { it.copy(maxOutputLength = length) }
        viewModelScope.launch { settingsRepository.setMaxOutputLength(length) }
    }
}
