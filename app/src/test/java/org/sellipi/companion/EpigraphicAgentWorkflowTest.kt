package org.sellipi.companion

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.agent.EpigraphicAgentOrchestrator
import org.sellipi.companion.agent.EpigraphicCriticAgent
import org.sellipi.companion.agent.EpigraphicIdentifyAgent
import org.sellipi.companion.agent.EpigraphicLearningAgent
import org.sellipi.companion.agent.model.AgentStage
import org.sellipi.companion.data.local.dao.LearnedGlyphDao
import org.sellipi.companion.data.local.dao.LetterFormDao
import org.sellipi.companion.data.local.entity.LearnedGlyphEntity
import org.sellipi.companion.data.local.entity.LetterFormEntity
import org.sellipi.companion.engine.slm.LocalEpigraphicSlmEngine

class EpigraphicAgentWorkflowTest {

    private val slmEngine = LocalEpigraphicSlmEngine()
    private val identifyAgent = EpigraphicIdentifyAgent(slmEngine)
    private val criticAgent = EpigraphicCriticAgent(slmEngine)

    private val fakeLearnedDao = object : LearnedGlyphDao {
        val storage = mutableListOf<LearnedGlyphEntity>()
        override suspend fun insertLearnedGlyph(glyph: LearnedGlyphEntity) {
            storage.add(glyph)
        }
        override fun getAllLearnedGlyphsFlow(): Flow<List<LearnedGlyphEntity>> = flowOf(storage)
        override suspend fun getLearnedGlyphsForLetter(letterId: String) = storage.filter { it.letterId == letterId }
        override suspend fun getLearnedCount(): Int = storage.size
    }

    private val fakeLetterFormDao = object : LetterFormDao {
        override fun getFormsForLetterFlow(letterId: String): Flow<List<LetterFormEntity>> = flowOf(emptyList())
        override suspend fun getForm(letterId: String, periodId: String): LetterFormEntity? = null
    }

    private val learningAgent = EpigraphicLearningAgent(fakeLearnedDao, fakeLetterFormDao)
    private val orchestrator = EpigraphicAgentOrchestrator(identifyAgent, criticAgent, learningAgent)

    @Test
    fun testCompleteAgenticIdentificationAndLearningWorkflow() = runTest {
        // 1. Run multi-agent identification on a cross-shaped glyph (Brahmi Ka)
        orchestrator.runAgenticIdentification(
            strokeDescription = "Perpendicular horizontal and vertical cross strokes with weathered edges",
            inscriptionId = "INSC_ANP_01",
            inscriptionEra = "3rd c. BCE Early Brahmi",
            lineContext = "දෙවනපිය මහරඣහ...",
            siteContext = "Vessagiriya Cave"
        )

        val stateAfterConsensus = orchestrator.state.value
        assertEquals(AgentStage.CONSENSUS_READY, stateAfterConsensus.stage)
        assertNotNull(stateAfterConsensus.topCandidate)
        assertEquals("ක", stateAfterConsensus.topCandidate!!.codepoint)
        assertTrue(stateAfterConsensus.criticValidationPassed)
        assertTrue(stateAfterConsensus.thoughtSteps.size >= 4)

        // 2. Approve and trigger Learning Agent to persist to database
        orchestrator.approveAndLearnCandidate(
            candidate = stateAfterConsensus.topCandidate!!,
            inscriptionId = "INSC_ANP_01",
            researcherNotes = "Verified in field with epigrapher signoff."
        )

        val stateAfterLearning = orchestrator.state.value
        assertTrue(stateAfterLearning.isLearnedAndSaved)
        assertEquals(1, fakeLearnedDao.getLearnedCount())
        assertEquals("L11", fakeLearnedDao.storage.first().letterId)
    }
}
