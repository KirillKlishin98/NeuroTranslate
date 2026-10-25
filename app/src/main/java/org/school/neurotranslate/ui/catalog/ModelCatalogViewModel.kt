package org.school.neurotranslate.ui.catalog

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ModelCatalogViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ModelCatalogUiState())
    val uiState = _uiState.asStateFlow()
}
