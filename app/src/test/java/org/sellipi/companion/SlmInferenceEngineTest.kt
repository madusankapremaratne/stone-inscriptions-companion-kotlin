package org.sellipi.companion

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.engine.slm.LocalEpigraphicSlmEngine
import org.sellipi.companion.engine.slm.PalaeographicPromptTemplates
import org.sellipi.companion.engine.slm.SlmInferenceRequest

class SlmInferenceEngineTest {

    private val engine = LocalEpigraphicSlmEngine()

    @Test
    fun testIdentifyPromptGenerationForKayanna() = runTest {
        val request = SlmInferenceRequest(
            systemPrompt = PalaeographicPromptTemplates.IDENTIFY_AGENT_SYSTEM_PROMPT,
            userPrompt = "Contour shows vertical and horizontal cross strokes (+ shape) on stone.",
            temperature = 0.1f
        )

        val response = engine.generateEpigraphicReasoning(request)
        assertNotNull(response)
        assertNotNull(response.structuredJson)
        assertTrue(response.structuredJson!!.contains("L11") || response.structuredJson!!.contains("ක"))
        assertTrue(response.latencyMs >= 0)
    }

    @Test
    fun testCriticValidationResponse() = runTest {
        val request = SlmInferenceRequest(
            systemPrompt = PalaeographicPromptTemplates.CRITIC_AGENT_SYSTEM_PROMPT,
            userPrompt = "Candidate: 'ක'. Line: 'දෙවනපිය මහරඣහ'. Era: '3rd c. BCE'.",
            temperature = 0.1f
        )

        val response = engine.generateEpigraphicReasoning(request)
        assertNotNull(response)
        assertNotNull(response.structuredJson)
        assertTrue(response.structuredJson!!.contains("isValidated"))
    }
}
