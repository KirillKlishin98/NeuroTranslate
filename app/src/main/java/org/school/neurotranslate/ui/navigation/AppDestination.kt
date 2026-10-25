package org.school.neurotranslate.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object TranslationDestination : NavKey

@Serializable
data object ModelCatalogDestination : NavKey

@Serializable
data object SettingsDestination : NavKey
