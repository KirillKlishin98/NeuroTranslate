package org.school.neurotranslate.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.school.neurotranslate.R
import org.school.neurotranslate.ui.catalog.ModelCatalogScreen
import org.school.neurotranslate.ui.catalog.ModelCatalogViewModel
import org.school.neurotranslate.ui.navigation.ModelCatalogDestination
import org.school.neurotranslate.ui.navigation.SettingsDestination
import org.school.neurotranslate.ui.navigation.TranslationDestination
import org.school.neurotranslate.ui.settings.SettingsScreen
import org.school.neurotranslate.ui.settings.SettingsThemeMode
import org.school.neurotranslate.ui.settings.SettingsViewModel
import org.school.neurotranslate.ui.theme.NeuroTranslateTheme
import org.school.neurotranslate.ui.translation.TranslationScreen
import org.school.neurotranslate.ui.translation.TranslationViewModel

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NeuroTranslateApp() {
    val settingsViewModel: SettingsViewModel = viewModel()
    val translationViewModel: TranslationViewModel = viewModel()
    val modelCatalogViewModel: ModelCatalogViewModel = viewModel()

    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val translationState by translationViewModel.uiState.collectAsStateWithLifecycle()
    val modelCatalogState by modelCatalogViewModel.uiState.collectAsStateWithLifecycle()

    NeuroTranslateTheme(themeMode = settingsState.themeMode) {
        val backStack = rememberNavBackStack(TranslationDestination)
        val currentDestination = backStack.lastOrNull()

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = when (currentDestination) {
                                is ModelCatalogDestination -> stringResource(R.string.models_title)
                                is SettingsDestination -> stringResource(R.string.settings_title)
                                else -> stringResource(R.string.translation_title)
                            }
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination is TranslationDestination,
                        onClick = { navigateTo(backStack, TranslationDestination) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Home,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_translation)) }
                    )
                    NavigationBarItem(
                        selected = currentDestination is ModelCatalogDestination,
                        onClick = { navigateTo(backStack, ModelCatalogDestination) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_models)) }
                    )
                    NavigationBarItem(
                        selected = currentDestination is SettingsDestination,
                        onClick = { navigateTo(backStack, SettingsDestination) },
                        icon = {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.nav_settings)) }
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) { innerPadding ->
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                modifier = Modifier.padding(innerPadding),
                entryProvider = { destination ->
                    when (destination) {
                        is TranslationDestination -> NavEntry(destination) {
                            TranslationScreen(
                                state = translationState,
                                onInputChanged = translationViewModel::onInputChanged,
                                onSwapLanguages = translationViewModel::onSwapLanguages,
                                onTranslate = translationViewModel::onTranslate,
                                onOpenCatalog = { navigateTo(backStack, ModelCatalogDestination) }
                            )
                        }

                        is ModelCatalogDestination -> NavEntry(destination) {
                            ModelCatalogScreen(
                                state = modelCatalogState,
                                onInstall = modelCatalogViewModel::onInstall,
                                onCancelDownload = modelCatalogViewModel::onCancelDownload,
                                onRemove = modelCatalogViewModel::onRemove
                            )
                        }

                        is SettingsDestination -> NavEntry(destination) {
                            SettingsScreen(
                                state = settingsState,
                                onThemeModeChanged = settingsViewModel::onThemeModeChanged,
                                onMaxOutputLengthChanged = settingsViewModel::onMaxOutputLengthChanged
                            )
                        }

                        else -> error("Unknown destination: $destination")
                    }
                }
            )
        }
    }
}

private fun navigateTo(
    backStack: MutableList<androidx.navigation3.runtime.NavKey>,
    destination: androidx.navigation3.runtime.NavKey
) {
    if (destination in backStack) {
        while (backStack.lastOrNull() != destination) backStack.removeLastOrNull()
    } else {
        backStack.add(destination)
    }
}
