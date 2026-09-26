package org.sellipi.companion.ui.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.sellipi.companion.R
import org.sellipi.companion.core.common.AppLanguage
import org.sellipi.companion.core.theme.GapAmber
import org.sellipi.companion.core.theme.GapAmberContainer
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.Stone700
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.domain.knowledge.ContentLanguage
import org.sellipi.companion.domain.knowledge.CurationStatus
import org.sellipi.companion.domain.knowledge.KnowledgeAnswer
import org.sellipi.companion.domain.knowledge.KnowledgeCard
import org.sellipi.companion.ui.components.SellipiTopAppBar
import org.sellipi.companion.ui.components.StatusBadge

fun AppLanguage.toContentLanguage(): ContentLanguage = when (this) {
    AppLanguage.ENGLISH -> ContentLanguage.EN
    AppLanguage.SINHALA -> ContentLanguage.SI
    AppLanguage.TAMIL -> ContentLanguage.TA
}

@Composable
fun AskScreen(
    viewModel: AskViewModel,
    onNavigateBack: () -> Unit,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isSunlightMode: Boolean,
    onToggleSunlightMode: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val language = currentLanguage.toContentLanguage()
    val submit = {
        viewModel.submit(language)
        keyboard?.hide()
    }

    Scaffold(
        topBar = {
            SellipiTopAppBar(
                title = stringResource(R.string.ask_title),
                canNavigateBack = true,
                onNavigateBack = onNavigateBack,
                currentLanguage = currentLanguage,
                onLanguageSelected = onLanguageSelected,
                isSunlightMode = isSunlightMode,
                onToggleSunlightMode = onToggleSunlightMode
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChanged,
                    placeholder = { Text(stringResource(R.string.ask_hint)) },
                    trailingIcon = {
                        IconButton(onClick = submit, enabled = !state.isLoading && state.query.isNotBlank()) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.ask_submit),
                                tint = TerracottaPrimary
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit() }),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TerracottaPrimary,
                        unfocusedBorderColor = Stone700
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                state.isLoading -> item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TerracottaPrimary)
                    }
                }

                state.packIsEmpty -> item { NoticeCard(stringResource(R.string.ask_pack_empty)) }

                else -> when (val answer = state.answer) {
                    null -> item {
                        Text(
                            text = stringResource(R.string.ask_intro),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    is KnowledgeAnswer.NotInRecords -> item {
                        NoticeCard(stringResource(R.string.ask_not_in_records))
                    }

                    is KnowledgeAnswer.Found -> items(answer.cards, key = { it.card.id }) { scored ->
                        AnswerCard(card = scored.card, language = language)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(card: KnowledgeCard, language: ContentLanguage) {
    val localizedBody = card.body(language)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = card.title(language),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = GoldPatina,
                    modifier = Modifier.weight(1f)
                )
                if (card.status == CurationStatus.DRAFT) {
                    StatusBadge(
                        text = stringResource(R.string.ask_draft_badge),
                        backgroundColor = GapAmberContainer,
                        textColor = GapAmber
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (localizedBody == null) {
                Text(
                    text = stringResource(R.string.ask_translation_pending),
                    style = MaterialTheme.typography.labelMedium,
                    color = GapAmber
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = localizedBody ?: card.bodyEn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            card.confidenceNote?.let { note ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.ask_confidence_note, note),
                    style = MaterialTheme.typography.bodySmall,
                    color = GapAmber
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.ask_sources),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = card.sources,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoticeCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp)
        )
    }
}
