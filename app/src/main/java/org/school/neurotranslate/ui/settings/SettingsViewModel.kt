package org.school.neurotranslate.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

class SettingsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    fun onThemeModeChanged(themeMode: SettingsThemeMode) {
        _uiState.update { it.copy(themeMode = themeMode) }
    }

    fun onMaxOutputLengthChanged(value: Float) {
        val step = 16
        val length = (value / step).roundToInt() * step
        _uiState.update { it.copy(maxOutputLength = length.coerceIn(64, 400)) }
    }
}
