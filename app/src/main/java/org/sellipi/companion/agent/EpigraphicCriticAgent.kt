package org.sellipi.companion.agent

import org.json.JSONObject
import org.sellipi.companion.agent.model.AgentThoughtStep
import org.sellipi.companion.agent.model.GlyphCandidate
import org.sellipi.companion.engine.slm.PalaeographicPromptTemplates
import org.sellipi.companion.engine.slm.SlmInferenceEngine
import org.sellipi.companion.engine.slm.SlmInferenceRequest

data class CriticAgentOutput(
    val isValidated: Boolean,
    val calibratedConfidence: Float,
    val criticNotes: String,
    val thoughtSteps: List<AgentThoughtStep>
)

/**
 * Critic Agent:
 * Validates candidate identifications against grammatical context, phonology,
 * regnal timelines, and epigraphical formulas.
 */
class EpigraphicCriticAgent(
    private val slmEngine: SlmInferenceEngine
) {

    suspend fun validateCandidate(
        candidate: GlyphCandidate,
        transcriptionLineContext: String,
        inscriptionDateRange: String
    ): CriticAgentOutput {
        val thoughtSteps = mutableListOf<AgentThoughtStep>()

        thoughtSteps.add(
            AgentThoughtStep(
                stepNumber = 4,
                agentName = "CriticAgent",
                title = "Evaluating Syntactic & Historical Era Compatibility",
                description = "Checking if candidate '${candidate.codepoint}' conforms to line context '$transcriptionLineContext' ($inscriptionDateRange)."
            )
        )

        val prompt = "Candidate: '${candidate.codepoint}' (${candidate.letterId}). Line Context: '$transcriptionLineContext'. Era: '$inscriptionDateRange'."
        val response = slmEngine.generateEpigraphicReasoning(
            SlmInferenceRequest(
                systemPrompt = PalaeographicPromptTemplates.CRITIC_AGENT_SYSTEM_PROMPT,
                userPrompt = prompt,
                temperature = 0.1f
            )
        )

        val jsonStr = response.structuredJson ?: response.rawText
        val jsonObj = JSONObject(jsonStr)

        val isValidated = jsonObj.optBoolean("isValidated", true)
        val contextFit = jsonObj.optString("contextualFit", "Contextual fit validated.")
        val adjustment = jsonObj.optDouble("confidenceAdjustment", 0.0).toFloat()
        val calibratedConfidence = (candidate.confidence + adjustment).coerceIn(0.01f, 0.99f)

        thoughtSteps.add(
            AgentThoughtStep(
                stepNumber = 5,
                agentName = "CriticAgent",
                title = "Historical Validation Complete",
                description = "$contextFit (Confidence calibrated to ${(calibratedConfidence * 100).toInt()}%)."
            )
        )

        return CriticAgentOutput(
            isValidated = isValidated,
            calibratedConfidence = calibratedConfidence,
            criticNotes = contextFit,
            thoughtSteps = thoughtSteps
        )
    }
}
