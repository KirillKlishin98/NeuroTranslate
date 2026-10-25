package org.school.neurotranslate.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.school.neurotranslate.R

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeModeChanged: (SettingsThemeMode) -> Unit,
    onMaxOutputLengthChanged: (Float) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(androidx.compose.ui.Alignment.TopCenter),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 24.dp,
                vertical = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_appearance)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeModeChip(
                            label = stringResource(R.string.theme_system),
                            mode = SettingsThemeMode.System,
                            selected = state.themeMode == SettingsThemeMode.System,
                            onClick = onThemeModeChanged
                        )
                        ThemeModeChip(
                            label = stringResource(R.string.theme_light),
                            mode = SettingsThemeMode.Light,
                            selected = state.themeMode == SettingsThemeMode.Light,
                            onClick = onThemeModeChanged
                        )
                        ThemeModeChip(
                            label = stringResource(R.string.theme_dark),
                            mode = SettingsThemeMode.Dark,
                            selected = state.themeMode == SettingsThemeMode.Dark,
                            onClick = onThemeModeChanged
                        )
                    }
                }
            }
            item {
                SettingsSection(title = stringResource(R.string.settings_translation)) {
                    Text(
                        text = stringResource(R.string.max_output_length),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = stringResource(R.string.max_output_length_value, state.maxOutputLength),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = state.maxOutputLength.toFloat(),
                        onValueChange = onMaxOutputLengthChanged,
                        valueRange = 64f..400f,
                        steps = 20
                    )
                    Text(
                        text = stringResource(R.string.settings_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ThemeModeChip(
    label: String,
    mode: SettingsThemeMode,
    selected: Boolean,
    onClick: (SettingsThemeMode) -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = { onClick(mode) },
        label = { Text(label) }
    )
}
