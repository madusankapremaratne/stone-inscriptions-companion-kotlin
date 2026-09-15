package org.sellipi.companion.agent

import org.json.JSONObject
import org.sellipi.companion.agent.model.AgentThoughtStep
import org.sellipi.companion.agent.model.GlyphCandidate
import org.sellipi.companion.engine.slm.PalaeographicPromptTemplates
import org.sellipi.companion.engine.slm.SlmInferenceEngine
import org.sellipi.companion.engine.slm.SlmInferenceRequest

data class IdentifyAgentOutput(
    val topCandidate: GlyphCandidate,
    val alternatives: List<GlyphCandidate>,
    val thoughtSteps: List<AgentThoughtStep>
)

/**
 * Identify Agent:
 * Performs stroke morphology analysis and runs SLM Chain-of-Thought reasoning
 * against the 38-letter × 18-period Aksharamalawa matrix.
 */
class EpigraphicIdentifyAgent(
    private val slmEngine: SlmInferenceEngine
) {

    suspend fun identifyGlyph(
        strokeDescription: String,
        inscriptionEra: String,
        siteContext: String
    ): IdentifyAgentOutput {
        val thoughtSteps = mutableListOf<AgentThoughtStep>()

        thoughtSteps.add(
            AgentThoughtStep(
                stepNumber = 1,
                agentName = "IdentifyAgent",
                title = "Extracting Visual Stroke Topology",
                description = "Detected features: '$strokeDescription' under site context '$siteContext'."
            )
        )

        thoughtSteps.add(
            AgentThoughtStep(
                stepNumber = 2,
                agentName = "IdentifyAgent",
                title = "Consulting On-Device SLM Epigraphic Knowledge",
                description = "Evaluating historical morpho-phonology for era '$inscriptionEra' across 18 periods."
            )
        )

        val prompt = "Analyze glyph with strokes: '$strokeDescription'. Historical era: '$inscriptionEra'. Site: '$siteContext'."
        val response = slmEngine.generateEpigraphicReasoning(
            SlmInferenceRequest(
                systemPrompt = PalaeographicPromptTemplates.IDENTIFY_AGENT_SYSTEM_PROMPT,
                userPrompt = prompt,
                temperature = 0.1f
            )
        )

        val jsonStr = response.structuredJson ?: response.rawText
        val jsonObj = JSONObject(jsonStr)
        val topObj = jsonObj.getJSONObject("topCandidate")

        val topCandidate = GlyphCandidate(
            letterId = topObj.getString("letterId"),
            codepoint = topObj.getString("codepoint"),
            romanisation = topObj.getString("romanisation"),
            confidence = topObj.getDouble("confidence").toFloat(),
            periodAttribution = topObj.getString("periodAttribution"),
            morphologicalReasoning = topObj.getString("morphologicalReasoning")
        )

        val alternatives = mutableListOf<GlyphCandidate>()
        if (jsonObj.has("alternativeCandidates")) {
            val altArray = jsonObj.getJSONArray("alternativeCandidates")
            for (i in 0 until altArray.length()) {
                val obj = altArray.getJSONObject(i)
                alternatives.add(
                    GlyphCandidate(
                        letterId = obj.getString("letterId"),
                        codepoint = obj.getString("codepoint"),
                        romanisation = obj.getString("romanisation"),
                        confidence = obj.getDouble("confidence").toFloat(),
                        periodAttribution = obj.getString("periodAttribution"),
                        morphologicalReasoning = obj.getString("morphologicalReasoning")
                    )
                )
            }
        }

        thoughtSteps.add(
            AgentThoughtStep(
                stepNumber = 3,
                agentName = "IdentifyAgent",
                title = "Primary Consensus Formed",
                description = "Identified as '${topCandidate.codepoint}' (${topCandidate.letterId}) with ${(topCandidate.confidence * 100).toInt()}% confidence."
            )
        )

        return IdentifyAgentOutput(
            topCandidate = topCandidate,
            alternatives = alternatives,
            thoughtSteps = thoughtSteps
        )
    }
}
