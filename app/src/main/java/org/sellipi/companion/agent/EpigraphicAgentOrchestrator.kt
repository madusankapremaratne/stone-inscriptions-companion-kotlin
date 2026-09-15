package org.sellipi.companion.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.sellipi.companion.agent.model.AgentStage
import org.sellipi.companion.agent.model.AgentThoughtStep
import org.sellipi.companion.agent.model.AgentWorkflowState
import org.sellipi.companion.agent.model.GlyphCandidate

/**
 * Orchestrator coordinating the Multi-Agent Epigraphical Workflow:
 * IdentifyAgent -> CriticAgent -> (User Confirmation) -> LearningAgent
 */
class EpigraphicAgentOrchestrator(
    private val identifyAgent: EpigraphicIdentifyAgent,
    private val criticAgent: EpigraphicCriticAgent,
    private val learningAgent: EpigraphicLearningAgent
) {

    private val _state = MutableStateFlow(AgentWorkflowState())
    val state: StateFlow<AgentWorkflowState> = _state.asStateFlow()

    suspend fun runAgenticIdentification(
        strokeDescription: String,
        inscriptionId: String,
        inscriptionEra: String = "3rd c. BCE Early Brahmi",
        lineContext: String = "දෙවනපිය මහරඣහ...",
        siteContext: String = "Mihintale Cave Inscription"
    ) {
        val steps = mutableListOf<AgentThoughtStep>()

        try {
            // Stage 1: Feature Extraction
            _state.value = AgentWorkflowState(
                stage = AgentStage.FEATURE_EXTRACTION,
                thoughtSteps = listOf(
                    AgentThoughtStep(
                        stepNumber = 1,
                        agentName = "VisionPreProcessor",
                        title = "Preprocessing Eroded Rock Contour",
                        description = "Normalizing stroke vectors and optical contrast against stone granite grain."
                    )
                )
            )
            delay(200)

            // Stage 2: Identify Agent (SLM CoT Reasoning)
            _state.value = _state.value.copy(stage = AgentStage.IDENTIFICATION_REASONING)
            val identifyOutput = identifyAgent.identifyGlyph(strokeDescription, inscriptionEra, siteContext)
            steps.addAll(identifyOutput.thoughtSteps)

            _state.value = _state.value.copy(
                thoughtSteps = steps.toList(),
                topCandidate = identifyOutput.topCandidate,
                alternatives = identifyOutput.alternatives
            )
            delay(150)

            // Stage 3: Critic Agent (Historical & Grammatical Critique)
            _state.value = _state.value.copy(stage = AgentStage.CRITIC_VALIDATION)
            val criticOutput = criticAgent.validateCandidate(
                candidate = identifyOutput.topCandidate,
                transcriptionLineContext = lineContext,
                inscriptionDateRange = inscriptionEra
            )
            steps.addAll(criticOutput.thoughtSteps)

            val calibratedTopCandidate = identifyOutput.topCandidate.copy(
                confidence = criticOutput.calibratedConfidence
            )

            // Stage 4: Consensus Ready for User Approval
            _state.value = _state.value.copy(
                stage = AgentStage.CONSENSUS_READY,
                thoughtSteps = steps.toList(),
                topCandidate = calibratedTopCandidate,
                criticValidationPassed = criticOutput.isValidated,
                criticNotes = criticOutput.criticNotes
            )

        } catch (e: Exception) {
            _state.value = _state.value.copy(
                stage = AgentStage.FAILED,
                errorMessage = "Agentic pipeline error: ${e.message}"
            )
        }
    }

    suspend fun approveAndLearnCandidate(
        candidate: GlyphCandidate,
        inscriptionId: String,
        researcherNotes: String? = null
    ) {
        val currentSteps = _state.value.thoughtSteps.toMutableList()
        _state.value = _state.value.copy(stage = AgentStage.LEARNING_PERSISTENCE)

        val learningOutput = learningAgent.learnAndPersistGlyph(
            candidate = candidate,
            inscriptionId = inscriptionId,
            researcherNotes = researcherNotes
        )
        currentSteps.addAll(learningOutput.thoughtSteps)

        _state.value = _state.value.copy(
            stage = AgentStage.IDLE,
            thoughtSteps = currentSteps,
            isLearnedAndSaved = true
        )
    }

    fun reset() {
        _state.value = AgentWorkflowState()
    }
}
