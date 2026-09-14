package org.sellipi.companion.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sellipi.companion.R
import org.sellipi.companion.core.common.AppLanguage
import org.sellipi.companion.core.theme.AttestedGreen
import org.sellipi.companion.core.theme.AttestedGreenContainer
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.Stone700
import org.sellipi.companion.core.theme.Stone800
import org.sellipi.companion.core.theme.Stone900
import org.sellipi.companion.core.theme.TerracottaLight
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.domain.model.Inscription
import org.sellipi.companion.domain.model.Site
import org.sellipi.companion.ui.components.SellipiTopAppBar
import org.sellipi.companion.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToInscription: (String) -> Unit,
    onNavigateToEvolution: () -> Unit,
    onNavigateToResearcher: () -> Unit,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isSunlightMode: Boolean,
    onToggleSunlightMode: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SellipiTopAppBar(
                title = stringResource(R.string.app_name),
                canNavigateBack = false,
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
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Hero Banner with Ancient Inscriptions Motif
            item {
                HeroBanner(
                    onNavigateToEvolution = onNavigateToEvolution,
                    onNavigateToResearcher = onNavigateToResearcher
                )
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = { Text(stringResource(R.string.search_inscriptions_hint)) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TerracottaPrimary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = TerracottaPrimary,
                        unfocusedBorderColor = Stone700
                    ),
                    singleLine = true
                )
            }

            // Site Filter Chips
            item {
                Text(
                    text = stringResource(R.string.nearby_sites),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedSiteId == null,
                            onClick = { viewModel.onSiteSelected(null) },
                            label = { Text(stringResource(R.string.all_sites)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TerracottaPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    items(state.sites) { site ->
                        val siteName = when (currentLanguage) {
                            AppLanguage.SINHALA -> site.nameSi
                            AppLanguage.TAMIL -> site.nameTa
                            AppLanguage.ENGLISH -> site.nameEn
                        }
                        FilterChip(
                            selected = state.selectedSiteId == site.id,
                            onClick = { viewModel.onSiteSelected(site.id) },
                            label = { Text(siteName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = TerracottaPrimary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }

            // Inscriptions List
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Registered Stone Inscriptions (${state.allInscriptions.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(state.allInscriptions, key = { it.id }) { inscription ->
                InscriptionCard(
                    inscription = inscription,
                    currentLanguage = currentLanguage,
                    onClick = { onNavigateToInscription(inscription.id) }
                )
            }
        }
    }
}

@Composable
fun HeroBanner(
    onNavigateToEvolution: () -> Unit,
    onNavigateToResearcher: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Stone800)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Stone800, Stone900)
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ViewInAr,
                        contentDescription = null,
                        tint = GoldPatina,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "සෙල්ලිපි (Sellipi)",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = GoldPatina
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onNavigateToEvolution,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.nav_evolution), fontSize = 13.sp)
                    }
                    Button(
                        onClick = onNavigateToResearcher,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Stone700)
                    ) {
                        Icon(Icons.Default.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.nav_researcher), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun InscriptionCard(
    inscription: Inscription,
    currentLanguage: AppLanguage,
    onClick: () -> Unit
) {
    val title = when (currentLanguage) {
        AppLanguage.SINHALA -> inscription.nameSi
        AppLanguage.TAMIL -> inscription.nameTa
        AppLanguage.ENGLISH -> inscription.nameEn
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = if (inscription.alignmentStrategy == "image_target") "AR Supported (Score ${inscription.arcoreTargetScore})" else "Overlay Guided",
                    backgroundColor = if (inscription.alignmentStrategy == "image_target") AttestedGreenContainer else TerracottaPrimary.copy(alpha = 0.2f),
                    textColor = if (inscription.alignmentStrategy == "image_target") AttestedGreen else TerracottaLight
                )
                Text(
                    text = formatYearRange(inscription.dateRangeStart, inscription.dateRangeEnd),
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldPatina
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = inscription.sourceCitation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun formatYearRange(start: Int, end: Int): String {
    val startStr = if (start < 0) "${-start} BCE" else "$start CE"
    val endStr = if (end < 0) "${-end} BCE" else "$end CE"
    return "$startStr – $endStr"
}
