package org.sellipi.companion.ui.ondeviceai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.sellipi.companion.core.theme.AttestedGreen
import org.sellipi.companion.core.theme.GapAmber
import org.sellipi.companion.ui.components.SellipiTopAppBar

/** Debug-only researcher tool; English-only by design. */
@Composable
fun KnowledgeEvalScreen(viewModel: KnowledgeEvalViewModel, onNavigateBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = { SellipiTopAppBar(title = "Q-dev evaluation", canNavigateBack = true, onNavigateBack = onNavigateBack) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { viewModel.run(withModel = true) }, enabled = !state.running, modifier = Modifier.weight(1f)) {
                        Text("With model")
                    }
                    OutlinedButton(onClick = { viewModel.run(withModel = false) }, enabled = !state.running, modifier = Modifier.weight(1f)) {
                        Text("Retrieval only")
                    }
                }
            }
            if (state.running) item {
                Column {
                    Text("Running ${state.done}/${state.total}…")
                    LinearProgressIndicator(
                        progress = { if (state.total > 0) state.done.toFloat() / state.total else 0f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            state.report?.let { r ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Backend ${r.backend} · ${r.questions} questions", fontWeight = FontWeight.Bold)
                            Text("Grounded answer rate %.2f".format(r.groundedAnswerRate))
                            Text("Abstention precision %.2f".format(r.abstentionPrecision))
                            Text("Wrong answers ${r.wrongAnswers}", color = if (r.wrongAnswers == 0) AttestedGreen else MaterialTheme.colorScheme.error)
                            Text("Generated share (EN answered) %.2f".format(r.generatedShare))
                            Text("Median latency ${r.medianLatencyMs} ms")
                            if (r.verdictCounts.isNotEmpty()) Text("Verifier: " + r.verdictCounts.entries.joinToString { "${it.key}=${it.value}" })
                            state.exportedPath?.let { Text("Saved: $it", style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                items(r.rows) { row ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "${if (row.wrong) "WRONG" else if (row.correct) "ok" else "miss"}  ${row.outcome}  ${row.latencyMs}ms",
                            color = when { row.wrong -> MaterialTheme.colorScheme.error; row.correct -> AttestedGreen; else -> GapAmber },
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(row.question, style = MaterialTheme.typography.bodyMedium)
                        row.answerText?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        row.verdict?.let { Text("verifier: $it", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
                    }
                }
            }
        }
    }
}
