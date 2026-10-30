package org.school.neurotranslate.ui.catalog

enum class ModelDirection {
    RussianToEnglish,
    EnglishToRussian
}

data class ModelCatalogItem(
    val id: String,
    val direction: ModelDirection,
    val packageSizeBytes: Long,
    val isInstalled: Boolean = false,
    val isDownloading: Boolean = false,
    val isDeleting: Boolean = false,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null
)

data class ModelCatalogUiState(
    val items: List<ModelCatalogItem> = emptyList(),
    val installedCount: Int = 0,
    val diskUsageMegabytes: Int = 0,
    val activeModelMemoryMegabytes: Int? = null,
    val isLoading: Boolean = true
)
