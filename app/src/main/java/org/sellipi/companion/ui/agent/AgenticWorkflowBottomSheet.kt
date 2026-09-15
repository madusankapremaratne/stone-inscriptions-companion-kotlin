package org.sellipi.companion.ui.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.sellipi.companion.agent.model.AgentStage
import org.sellipi.companion.agent.model.AgentThoughtStep
import org.sellipi.companion.agent.model.AgentWorkflowState
import org.sellipi.companion.agent.model.GlyphCandidate
import org.sellipi.companion.core.theme.AttestedGreen
import org.sellipi.companion.core.theme.AttestedGreenContainer
import org.sellipi.companion.core.theme.GoldPatina
import org.sellipi.companion.core.theme.Stone700
import org.sellipi.companion.core.theme.Stone800
import org.sellipi.companion.core.theme.Stone900
import org.sellipi.companion.core.theme.TerracottaLight
import org.sellipi.companion.core.theme.TerracottaPrimary
import org.sellipi.companion.ui.components.StatusBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgenticWorkflowBottomSheet(
    state: AgentWorkflowState,
    onDismiss: () -> Unit,
    onApproveAndLearn: (GlyphCandidate) -> Unit,
    sheetState: SheetState
) {
    var isThoughtLogExpanded by remember { mutableStateOf(false) }
    var selectedCandidate by remember(state.topCandidate) { mutableStateOf(state.topCandidate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header: Agentic Pipeline Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = GoldPatina,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Agentic Epigraphic CoT",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                StatusBadge(
                    text = when (state.stage) {
                        AgentStage.IDLE -> "Standby"
                        AgentStage.FEATURE_EXTRACTION -> "Vision Pre-Process"
                        AgentStage.IDENTIFICATION_REASONING -> "Identify Agent (SLM)"
                        AgentStage.CRITIC_VALIDATION -> "Critic Agent (Validation)"
                        AgentStage.LEARNING_PERSISTENCE -> "Learning Agent (DB Sync)"
                        AgentStage.CONSENSUS_READY -> "Consensus Ready"
                        AgentStage.FAILED -> "Error"
                    },
                    backgroundColor = if (state.stage == AgentStage.CONSENSUS_READY) AttestedGreenContainer else TerracottaPrimary.copy(alpha = 0.2f),
                    textColor = if (state.stage == AgentStage.CONSENSUS_READY) AttestedGreen else TerracottaLight
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Loading Indicator during multi-agent inference
            if (state.stage == AgentStage.FEATURE_EXTRACTION ||
                state.stage == AgentStage.IDENTIFICATION_REASONING ||
                state.stage == AgentStage.CRITIC_VALIDATION ||
                state.stage == AgentStage.LEARNING_PERSISTENCE
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = TerracottaPrimary,
                    trackColor = Stone700
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Consensus Result Display
            if (selectedCandidate != null) {
                val candidate = selectedCandidate!!

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Stone800)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Stone900)
                                        .border(1.dp, GoldPatina, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = candidate.codepoint,
                                        style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                        color = GoldPatina
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "Letter ${candidate.letterId} (/ ${candidate.romanisation} /)",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Period Attribution: ${candidate.periodAttribution}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "${(candidate.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (candidate.confidence > 0.85f) AttestedGreen else TerracottaLight
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = candidate.morphologicalReasoning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state.criticNotes != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AttestedGreenContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = AttestedGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Critic: ${state.criticNotes}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = AttestedGreen
                                    )
                                }
                            }
                        }
                    }
                }

                // Alternative Candidates Row
                if (state.alternatives.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Alternative Candidates Considered by SLM:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.alternatives.forEach { alt ->
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedCandidate = alt },
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedCandidate == alt) TerracottaPrimary else Stone800,
                                border = if (selectedCandidate == alt) null else androidx.compose.foundation.BorderStroke(1.dp, Stone700)
                            ) {
                                Text(
                                    text = "${alt.codepoint} (${(alt.confidence * 100).toInt()}%)",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Collapsible Chain-of-Thought Reasoning Log
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isThoughtLogExpanded = !isThoughtLogExpanded },
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Stone900)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "View Multi-Agent Reasoning Trace (${state.thoughtSteps.size} steps)",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = GoldPatina
                        )
                        Icon(
                            imageVector = if (isThoughtLogExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = GoldPatina
                        )
                    }

                    AnimatedVisibility(visible = isThoughtLogExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            state.thoughtSteps.forEach { step ->
                                Row(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "${step.stepNumber}. ",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = TerracottaLight
                                    )
                                    Column {
                                        Text(
                                            text = "[${step.agentName}] ${step.title}",
                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White
                                        )
                                        Text(
                                            text = step.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action: Approve & Commit to Database (Learning Agent)
            if (state.isLearnedAndSaved) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = AttestedGreenContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AttestedGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Learned & Synced to Local Epigraphical Database!",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = AttestedGreen
                        )
                    }
                }
            } else {
                Button(
                    onClick = {
                        selectedCandidate?.let { onApproveAndLearn(it) }
                    },
                    enabled = selectedCandidate != null && state.stage == AgentStage.CONSENSUS_READY,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TerracottaPrimary)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Approve & Teach Learning Agent")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
