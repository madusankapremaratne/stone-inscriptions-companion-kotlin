package org.sellipi.companion.ui.ar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sellipi.companion.R
import org.sellipi.companion.core.theme.AttestedGreen
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.Stone800
import org.sellipi.companion.core.theme.Stone900
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.engine.ar.ArTrackingStatus
import org.sellipi.companion.ui.components.StatusBadge

@Composable
fun ArViewScreen(
    viewModel: ArViewModel,
    onNavigateBack: () -> Unit,
    onFallbackToOverlay: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!state.isArSupported) {
                // Non-ARCore Fallback Prompt Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Stone900)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = TerracottaPrimary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ARCore Unsupported",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ar_not_supported_msg),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { state.inscription?.id?.let(onFallbackToOverlay) },
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(stringResource(R.string.switch_to_overlay))
                        }
                    }
                }
            } else {
                // Simulated AR Viewport with Surface Plane Anchor
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Stone900),
                    contentAlignment = Alignment.Center
                ) {
                    // Simulated 3D Text Overlay on Tracked Surface
                    if (state.trackingStatus == ArTrackingStatus.TRACKING_SURFACE) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .padding(16.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = TerracottaPrimary.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                StatusBadge(
                                    text = "3D ANCHORED (ARCore Tracking Active)",
                                    backgroundColor = AttestedGreen,
                                    textColor = Color.White
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                state.lines.forEach { line ->
                                    Text(
                                        text = "Line ${line.lineNumber}: ${line.textOriginal}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = GoldPatina
                                    )
                                    Text(
                                        text = line.textModernSinhala,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ViewInAr,
                                contentDescription = null,
                                tint = GoldPatina,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.ar_scanning),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Top Bar Overlay
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.background(Stone900.copy(alpha = 0.8f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Stone900.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = if (state.trackingStatus == ArTrackingStatus.TRACKING_SURFACE) "Tracking Active" else "Scanning Surface…",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = if (state.trackingStatus == ArTrackingStatus.TRACKING_SURFACE) AttestedGreen else GoldPatina
                    )
                }

                Button(
                    onClick = { state.inscription?.id?.let(onFallbackToOverlay) },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Stone800)
                ) {
                    Text("Overlay Mode", fontSize = 12.sp)
                }
            }
        }
    }
}
