package org.sellipi.companion.ui.researcher

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sellipi.companion.R
import org.sellipi.companion.core.common.AppLanguage
import org.sellipi.companion.core.theme.AttestedGreen
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.Stone700
import org.sellipi.companion.core.theme.Stone800
import org.sellipi.companion.core.theme.Stone900
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.ui.components.SellipiTopAppBar
import org.sellipi.companion.ui.components.StatusBadge
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearcherCaptureScreen(
    viewModel: ResearcherCaptureViewModel,
    onNavigateBack: () -> Unit,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isSunlightMode: Boolean,
    onToggleSunlightMode: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var passcodeInput by remember { mutableStateOf("") }
    var activeSector by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            SellipiTopAppBar(
                title = stringResource(R.string.researcher_title),
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
        if (!state.isUnlocked) {
            // Passcode Protection Gate
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = TerracottaPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Epigraphical Field Gate",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.researcher_gate_prompt),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = passcodeInput,
                            onValueChange = { passcodeInput = it },
                            placeholder = { Text("Passcode (e.g. 1837)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = state.passcodeError,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TerracottaPrimary,
                                unfocusedBorderColor = Stone700
                            )
                        )
                        if (state.passcodeError) {
                            Text(
                                text = "Invalid passcode. Use '1837' or 'sellipi'",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.unlockWithPasscode(passcodeInput) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Unlock Field Capture Mode")
                        }
                    }
                }
            }
        } else {
            // Unlocked Calibrated Field Capture Workspace
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Multi-Angle Coverage Guidance Dome
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Stone800)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.coverage_dome_title),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            StatusBadge(
                                text = "${state.capturedSectors.size}/8 Sectors",
                                backgroundColor = if (state.capturedSectors.size == 8) AttestedGreen else TerracottaPrimary.copy(alpha = 0.2f),
                                textColor = if (state.capturedSectors.size == 8) Color.White else TerracottaPrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Radar / Dome Visualizer
                        Box(
                            modifier = Modifier
                                .size(180.dp)
                                .clip(CircleShape)
                                .background(Stone900)
                                .border(1.dp, Stone700, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                                val center = Offset(size.width / 2, size.height / 2)
                                val radius = size.width / 2

                                // Concentric altitude rings
                                drawCircle(color = Stone700, radius = radius * 0.33f, style = Stroke(1.dp.toPx()))
                                drawCircle(color = Stone700, radius = radius * 0.66f, style = Stroke(1.dp.toPx()))
                                drawCircle(color = Stone700, radius = radius, style = Stroke(1.dp.toPx()))

                                // 8 radial sectors
                                for (i in 0 until 8) {
                                    val angle = Math.toRadians((i * 45.0) - 90.0)
                                    val endX = center.x + (radius * cos(angle)).toFloat()
                                    val endY = center.y + (radius * sin(angle)).toFloat()
                                    drawLine(color = Stone700, start = center, end = Offset(endX, endY), strokeWidth = 1.dp.toPx())

                                    val isSectorCaptured = state.capturedSectors.contains(i)
                                    val dotRadius = if (isSectorCaptured) 10.dp.toPx() else 6.dp.toPx()
                                    val dotColor = if (isSectorCaptured) AttestedGreen else if (i == activeSector) GoldPatina else Color.Gray

                                    val dotX = center.x + (radius * 0.7f * cos(angle)).toFloat()
                                    val dotY = center.y + (radius * 0.7f * sin(angle)).toFloat()
                                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, dotY))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                viewModel.captureCurrentSector(activeSector)
                                activeSector = (activeSector + 1) % 8
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Capture Sector ${(activeSector + 1)}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time Optical & Geospatial Telemetry
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Calibrated Sensor Telemetry",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "GPS: Lat ${String.format("%.4f", state.currentGpsLat)}, Lon ${String.format("%.4f", state.currentGpsLon)} (±${state.currentGpsAccuracy}m)",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = GoldPatina
                        )
                        Text(
                            text = "Optical Scale: ${String.format("%.3f", state.currentScaleMmPerPx)} mm/px",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Permit Ref: ${state.permitReference}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Export Button
                Button(
                    onClick = viewModel::exportEncryptedBundle,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Stone700)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.export_bundle))
                }

                if (state.exportedBundlePath != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.export_success, state.exportedBundlePath!!),
                        style = MaterialTheme.typography.bodySmall,
                        color = AttestedGreen
                    )
                }
            }
        }
    }
}
