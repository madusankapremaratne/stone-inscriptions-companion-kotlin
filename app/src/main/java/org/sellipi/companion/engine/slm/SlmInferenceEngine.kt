package org.sellipi.companion.engine.slm

/**
 * Request payload for On-Device Small Language Model (SLM) epigraphic inference.
 */
data class SlmInferenceRequest(
    val systemPrompt: String,
    val userPrompt: String,
    val temperature: Float = 0.2f,
    val maxTokens: Int = 1024,
    val fewShotExamples: List<SlmFewShotExample> = emptyList()
)

data class SlmFewShotExample(
    val inputDescription: String,
    val reasoningChain: String,
    val outputJson: String
)

/**
 * Result returned from the on-device SLM with token metrics and raw text.
 */
data class SlmInferenceResponse(
    val rawText: String,
    val reasoningChain: String?,
    val structuredJson: String?,
    val latencyMs: Long,
    val tokenCount: Int
)

/**
 * Abstraction for On-Device Small Language Models (e.g. MediaPipe LLM Inference / LiteRT / Edge SLM).
 */
interface SlmInferenceEngine {
    val modelName: String
    val isModelLoaded: Boolean

    suspend fun generateEpigraphicReasoning(request: SlmInferenceRequest): SlmInferenceResponse
}
