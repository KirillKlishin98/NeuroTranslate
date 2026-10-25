package org.school.neurotranslate.ui.translation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.school.neurotranslate.R

@Composable
fun TranslationScreen(
    state: TranslationUiState,
    onInputChanged: (String) -> Unit,
    onSwapLanguages: () -> Unit,
    onTranslate: () -> Unit,
    onOpenCatalog: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.translation_headline),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = stringResource(R.string.translation_intro),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LanguageDirectionCard(
                sourceLanguage = state.sourceLanguage,
                targetLanguage = state.targetLanguage,
                onSwapLanguages = onSwapLanguages
            )

            OutlinedTextField(
                value = state.inputText,
                onValueChange = onInputChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.input_text_label)) },
                placeholder = { Text(stringResource(R.string.input_text_hint)) },
                minLines = 5,
                maxLines = 8,
                supportingText = {
                    Text(stringResource(R.string.characters_count, state.inputText.length))
                }
            )

            if (state.showEmptyInputMessage) {
                Text(
                    text = stringResource(R.string.no_text_to_translate),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            FilledTonalButton(
                onClick = onTranslate,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.translate_action))
            }

            if (state.showModelPrompt) {
                ModelRequiredCard(onOpenCatalog = onOpenCatalog)
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.translation_result),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.translation_result_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageDirectionCard(
    sourceLanguage: TranslationLanguage,
    targetLanguage: TranslationLanguage,
    onSwapLanguages: () -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LanguageLabel(
                label = stringResource(R.string.source_language),
                language = sourceLanguage,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSwapLanguages) {
                Icon(
                    imageVector = Icons.Outlined.SwapHoriz,
                    contentDescription = stringResource(R.string.swap_languages)
                )
            }
            LanguageLabel(
                label = stringResource(R.string.target_language),
                language = targetLanguage,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun LanguageLabel(
    label: String,
    language: TranslationLanguage,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign
        )
        Text(
            text = when (language) {
                TranslationLanguage.Russian -> stringResource(R.string.russian)
                TranslationLanguage.English -> stringResource(R.string.english)
            },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = textAlign
        )
    }
}

@Composable
private fun ModelRequiredCard(onOpenCatalog: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.model_required_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.model_required_body),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onOpenCatalog) {
                Text(stringResource(R.string.open_model_catalog))
            }
        }
    }
}
