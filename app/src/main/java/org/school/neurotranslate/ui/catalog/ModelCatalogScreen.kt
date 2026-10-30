package org.school.neurotranslate.ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.school.neurotranslate.R

@Composable
fun ModelCatalogScreen(
    state: ModelCatalogUiState,
    onInstall: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 24.dp,
                vertical = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.models_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(R.string.catalog_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                StorageSummaryCard(state = state)
            }
            items(state.items, key = ModelCatalogItem::id) { item ->
                ModelCard(
                    item = item,
                    onInstall = { onInstall(item.id) },
                    onCancelDownload = { onCancelDownload(item.id) },
                    onRemove = { onRemove(item.id) }
                )
            }
        }
    }
}

@Composable
private fun StorageSummaryCard(state: ModelCatalogUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.installed_models_count, state.installedCount),
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Metric(
                    icon = { Icon(Icons.Outlined.Storage, contentDescription = null) },
                    label = stringResource(R.string.disk_usage),
                    value = stringResource(R.string.disk_usage_value, state.diskUsageMegabytes),
                    modifier = Modifier.weight(1f)
                )
                Metric(
                    icon = { Icon(Icons.Outlined.Memory, contentDescription = null) },
                    label = stringResource(R.string.active_model_memory),
                    value = state.activeModelMemoryMegabytes?.let {
                        stringResource(R.string.disk_usage_value, it)
                    } ?: stringResource(R.string.not_measured),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun Metric(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        icon()
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun ModelCard(
    item: ModelCatalogItem,
    onInstall: () -> Unit,
    onCancelDownload: () -> Unit,
    onRemove: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = when (item.direction) {
                            ModelDirection.RussianToEnglish -> stringResource(R.string.model_ru_en)
                            ModelDirection.EnglishToRussian -> stringResource(R.string.model_en_ru)
                        },
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(item.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AssistChip(
                    onClick = {},
                    enabled = item.isInstalled,
                    label = {
                        Text(
                            stringResource(
                                if (item.isInstalled) R.string.model_installed else R.string.model_not_installed
                            )
                        )
                    }
                )
            }
            Text(
                text = stringResource(R.string.model_package_size, ((item.packageSizeBytes + BYTES_PER_MEGABYTE - 1) / BYTES_PER_MEGABYTE).toInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (item.errorMessage != null) {
                Text(
                    text = item.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            when {
                item.isDownloading -> {
                    LinearProgressIndicator(
                        progress = { item.downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(R.string.model_download_progress, (item.downloadProgress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onCancelDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.model_cancel_download))
                    }
                }

                item.isInstalled -> Button(
                    onClick = onRemove,
                    enabled = !item.isDeleting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(if (item.isDeleting) R.string.model_removing else R.string.model_remove))
                }

                else -> Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.model_install))
                }
            }
        }
    }
}

private const val BYTES_PER_MEGABYTE = 1024L * 1024L
