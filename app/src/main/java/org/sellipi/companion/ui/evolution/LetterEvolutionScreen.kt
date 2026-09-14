package org.sellipi.companion.ui.evolution

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sellipi.companion.R
import org.sellipi.companion.core.common.AppLanguage
import org.sellipi.companion.core.theme.AttestedGreen
import org.sellipi.companion.core.theme.AttestedGreenContainer
import org.sellipi.companion.core.theme.GapAmber
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.Stone700
import org.sellipi.companion.core.theme.Stone800
import org.sellipi.companion.core.theme.Stone900
import org.sellipi.companion.core.theme.TerracottaLight
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.engine.morph.VectorMorphEngine
import org.sellipi.companion.ui.components.EpigraphicGapCard
import org.sellipi.companion.ui.components.SellipiTopAppBar
import org.sellipi.companion.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LetterEvolutionScreen(
    viewModel: LetterEvolutionViewModel,
    onNavigateBack: () -> Unit,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isSunlightMode: Boolean,
    onToggleSunlightMode: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val morphEngine = remember { VectorMorphEngine() }

    Scaffold(
        topBar = {
            SellipiTopAppBar(
                title = stringResource(R.string.letter_evolution_title),
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
        if (state.isLoading || state.evolutionData == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = TerracottaPrimary)
            }
        } else {
            val data = state.evolutionData!!
            val currentForm = data.timeline.getOrNull(state.selectedPeriodIndex)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Horizontal Alphabet Picker
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.allLetters, key = { it.id }) { letter ->
                        val isSelected = letter.id == state.currentLetterId
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) TerracottaPrimary else MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    if (isSelected) GoldPatina else Stone700,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.selectLetter(letter.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter.modernSinhalaCodepoint,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // 2. Main Vector Glyph Morphing Canvas Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Stone800)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Letter Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "${data.letter.modernSinhalaCodepoint} (${data.letter.letterName})",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = TerracottaLight
                                )
                                Text(
                                    text = "ISO 15919: /${data.letter.romanisation}/",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = GoldPatina
                                )
                            }
                            StatusBadge(
                                text = if (currentForm?.isAttested == true) "Attested" else "Epigraphic Gap",
                                backgroundColor = if (currentForm?.isAttested == true) AttestedGreenContainer else GapAmber.copy(alpha = 0.2f),
                                textColor = if (currentForm?.isAttested == true) AttestedGreen else GapAmber
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Vector Morphing Canvas Viewport
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Stone900)
                                .border(1.dp, Stone700, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (currentForm?.isAttested == true) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                                    val nodes = morphEngine.parsePathToNodes(
                                        currentForm.vectorPath,
                                        data.letter.modernSinhalaCodepoint
                                    )
                                    val path = morphEngine.buildComposePath(nodes, size.width, size.height)
                                    drawPath(
                                        path = path,
                                        color = TerracottaPrimary,
                                        style = Stroke(width = 6.dp.toPx())
                                    )
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = "∅",
                                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                                        color = GapAmber
                                    )
                                    Text(
                                        text = "No Attested Evidence",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = GapAmber,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Current Period Badge
                        val periodLabel = currentForm?.period?.let {
                            when (currentLanguage) {
                                AppLanguage.SINHALA -> it.labelSi
                                AppLanguage.TAMIL -> it.labelTa
                                AppLanguage.ENGLISH -> it.labelEn
                            }
                        } ?: "Period ${state.selectedPeriodIndex + 1}"

                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        currentForm?.period?.let {
                            Text(
                                text = "Year: ${formatYear(it.yearStart)} – ${formatYear(it.yearEnd)} (${it.chartColumnRefs})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Historical Scrubber Slider across 18 Periods
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "3rd c. BCE (Early Brahmi)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "10th c. CE (Classical)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Slider(
                        value = state.morphProgress,
                        onValueChange = viewModel::updateScrubberProgress,
                        valueRange = 0f..17f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = TerracottaPrimary,
                            activeTrackColor = TerracottaPrimary,
                            inactiveTrackColor = Stone700
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 4. Gap Notification or Attested Provenance Details
                if (currentForm?.isAttested == false) {
                    EpigraphicGapCard(
                        periodLabel = periodLabel(currentForm, currentLanguage),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = stringResource(R.string.attested_form_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = AttestedGreen
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = currentForm?.sourceInscriptionRef ?: "Attested in Aksharamalawa Inscription Matrix",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Provenance: ${currentForm?.provenance ?: "Authentic Epigraphical Estampage"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun periodLabel(form: org.sellipi.companion.domain.model.LetterForm?, lang: AppLanguage): String {
    return form?.period?.let {
        when (lang) {
            AppLanguage.SINHALA -> it.labelSi
            AppLanguage.TAMIL -> it.labelTa
            AppLanguage.ENGLISH -> it.labelEn
        }
    } ?: "Selected Period"
}

private fun formatYear(year: Int): String {
    return if (year < 0) "${-year} BCE" else "$year CE"
}
