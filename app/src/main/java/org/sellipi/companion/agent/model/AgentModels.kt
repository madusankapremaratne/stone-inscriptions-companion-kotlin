package org.sellipi.companion.agent.model

import org.sellipi.companion.domain.model.Letter
import org.sellipi.companion.domain.model.LetterForm

enum class AgentStage {
    IDLE,
    FEATURE_EXTRACTION,
    IDENTIFICATION_REASONING,
    CRITIC_VALIDATION,
    LEARNING_PERSISTENCE,
    CONSENSUS_READY,
    FAILED
}

data class AgentThoughtStep(
    val stepNumber: Int,
    val agentName: String,
    val title: String,
    val description: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isCompleted: Boolean = true
)

data class GlyphCandidate(
    val letterId: String,
    val codepoint: String,
    val romanisation: String,
    val confidence: Float,
    val periodAttribution: String,
    val morphologicalReasoning: String
)

data class AgentWorkflowState(
    val stage: AgentStage = AgentStage.IDLE,
    val thoughtSteps: List<AgentThoughtStep> = emptyList(),
    val topCandidate: GlyphCandidate? = null,
    val alternatives: List<GlyphCandidate> = emptyList(),
    val criticValidationPassed: Boolean = false,
    val criticNotes: String? = null,
    val isLearnedAndSaved: Boolean = false,
    val errorMessage: String? = null
)
