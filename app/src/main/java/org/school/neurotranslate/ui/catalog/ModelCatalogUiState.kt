package org.school.neurotranslate.ui.catalog

enum class ModelDirection {
    RussianToEnglish,
    EnglishToRussian
}

data class ModelCatalogItem(
    val id: String,
    val direction: ModelDirection,
    val isInstalled: Boolean = false
)

data class ModelCatalogUiState(
    val items: List<ModelCatalogItem> = listOf(
        ModelCatalogItem(id = "opus-mt-ru-en", direction = ModelDirection.RussianToEnglish),
        ModelCatalogItem(id = "opus-mt-en-ru", direction = ModelDirection.EnglishToRussian)
    ),
    val installedCount: Int = 0,
    val diskUsageMegabytes: Int = 0,
    val activeModelMemoryMegabytes: Int? = null
)
