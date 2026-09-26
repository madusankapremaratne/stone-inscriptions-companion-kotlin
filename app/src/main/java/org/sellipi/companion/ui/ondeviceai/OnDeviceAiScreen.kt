package org.sellipi.companion.ui.ondeviceai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sellipi.companion.R
import org.sellipi.companion.core.common.AppLanguage
import org.sellipi.companion.core.theme.AttestedGreen
import org.sellipi.companion.core.theme.GapAmber
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.data.llm.DeviceCapability
import org.sellipi.companion.data.llm.ModelState
import org.sellipi.companion.ui.components.SellipiTopAppBar

@Composable
fun OnDeviceAiScreen(
    viewModel: OnDeviceAiViewModel,
    onNavigateBack: () -> Unit,
    onOpenEvaluation: (() -> Unit)?,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isSunlightMode: Boolean,
    onToggleSunlightMode: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val useGpu by viewModel.useGpu.collectAsState()
    val uriHandler = LocalUriHandler.current
    val spec = viewModel.spec
    val sizeMb = spec.sizeBytes / (1024 * 1024)

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            SellipiTopAppBar(
                title = stringResource(R.string.ondevice_title),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard {
                Text(stringResource(R.string.ondevice_what), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.ondevice_privacy), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            InfoCard {
                Text(spec.displayName, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = GoldPatina)
                Spacer(Modifier.height(8.dp))
                when (val s = state) {
                    ModelState.NotConfigured -> StatusText(stringResource(R.string.ondevice_not_configured), GapAmber)

                    is ModelState.Ineligible -> StatusText(
                        stringResource(
                            if (s.reason == DeviceCapability.IneligibleReason.LOW_RAM) R.string.ondevice_ineligible_ram
                            else R.string.ondevice_ineligible_abi,
                            s.ramGb
                        ),
                        GapAmber
                    )

                    ModelState.NotDownloaded, is ModelState.Failed -> {
                        if (s is ModelState.Failed) {
                            StatusText(stringResource(failureText(s.reason)), MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                        }
                        Text(stringResource(R.string.ondevice_license_notice), style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { uriHandler.openUri(spec.licenseUrl) }) { Text(stringResource(R.string.ondevice_terms)) }
                            TextButton(onClick = { uriHandler.openUri(spec.prohibitedUseUrl) }) { Text(stringResource(R.string.ondevice_prohibited_use)) }
                        }
                        Button(
                            onClick = viewModel::download,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                        ) { Text(stringResource(R.string.ondevice_download, sizeMb)) }
                        Text(stringResource(R.string.ondevice_wifi_only), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    is ModelState.Downloading -> {
                        StatusText(
                            if (s.waitingForWifi) stringResource(R.string.ondevice_waiting_wifi)
                            else stringResource(R.string.ondevice_downloading, s.bytes / (1024 * 1024), s.total / (1024 * 1024)),
                            MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (s.total > 0) (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = TerracottaPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ondevice_cancel))
                        }
                    }

                    is ModelState.Verifying -> {
                        StatusText(stringResource(R.string.ondevice_verifying), MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { if (s.total > 0) (s.bytes.toFloat() / s.total).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = GoldPatina
                        )
                    }

                    is ModelState.Ready -> {
                        StatusText(stringResource(R.string.ondevice_ready), AttestedGreen)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.ondevice_gpu), style = MaterialTheme.typography.bodyMedium)
                                Text(stringResource(R.string.ondevice_gpu_hint), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = useGpu, onCheckedChange = viewModel::setUseGpu)
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = viewModel::delete, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.ondevice_delete))
                        }
                    }
                }
            }

            if (onOpenEvaluation != null) {
                OutlinedButton(onClick = onOpenEvaluation, modifier = Modifier.fillMaxWidth()) {
                    Text("Run Q-dev evaluation (debug)")
                }
            }
        }
    }
}

private fun failureText(reason: ModelState.FailReason): Int = when (reason) {
    ModelState.FailReason.INSUFFICIENT_SPACE -> R.string.ondevice_failed_space
    ModelState.FailReason.SIZE_MISMATCH, ModelState.FailReason.HASH_MISMATCH -> R.string.ondevice_failed_verify
    ModelState.FailReason.DOWNLOAD_FAILED, ModelState.FailReason.STORAGE_ERROR -> R.string.ondevice_failed_download
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun StatusText(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = color)
}
