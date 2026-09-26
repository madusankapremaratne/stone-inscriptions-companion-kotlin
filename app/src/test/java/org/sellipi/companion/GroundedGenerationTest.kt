package org.sellipi.companion

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.domain.knowledge.AskKnowledgeUseCase
import org.sellipi.companion.domain.knowledge.CardAlias
import org.sellipi.companion.domain.knowledge.ContentLanguage
import org.sellipi.companion.domain.knowledge.CurationStatus
import org.sellipi.companion.domain.knowledge.KnowledgeAnswer
import org.sellipi.companion.domain.knowledge.KnowledgeCard
import org.sellipi.companion.domain.knowledge.KnowledgePack
import org.sellipi.companion.domain.knowledge.KnowledgeRetriever
import org.sellipi.companion.domain.knowledge.QueryContext
import org.sellipi.companion.domain.lessons.GenerationOutcome
import org.sellipi.companion.domain.lessons.KnowledgeGap
import org.sellipi.companion.domain.lessons.ModelRouting
import org.sellipi.companion.domain.llm.AnswerVerdict
import org.sellipi.companion.domain.llm.AnswerVerifier
import org.sellipi.companion.domain.llm.EvalQuestion
import org.sellipi.companion.domain.llm.GroundedPrompts
import org.sellipi.companion.domain.llm.KnowledgeEvaluator
import org.sellipi.companion.domain.llm.LlmEngine

class GroundedGenerationTest {

    private fun card(id: String, title: String, body: String) =
        KnowledgeCard(id, id.substringBefore('.'), title, null, null, body, null, null, "src", null, CurationStatus.VERIFIED)

    private val mahinda = card(
        "person.mahinda_thera", "Mahinda Thera",
        "Mahinda is remembered as the monk who brought Buddhism to Sri Lanka in the 3rd century BCE. He met King Devanampiya Tissa at Mihintale."
    )
    private val mihintale = card(
        "place.mihintale", "Mihintale",
        "Mihintale is a hill complex east of Anuradhapura where Buddhism was introduced to Sri Lanka."
    )
    private val pack = KnowledgePack(
        cards = listOf(mahinda, mihintale),
        aliases = listOf(
            CardAlias("Mahinda Thera", mahinda.id, "en", false),
            CardAlias("Mihintale", mihintale.id, "en", false)
        ),
        links = emptyList()
    )
    private val retriever = KnowledgeRetriever(pack, includeDrafts = false)

    /** Answers router prompts and answer prompts with scripted replies; records the prompts. */
    private class FakeLlm(
        private val routerReply: String = "NONE",
        private val answerReply: String = "ABSTAIN",
        private val delayMs: Long = 0,
        private val fail: Boolean = false
    ) : LlmEngine {
        val prompts = mutableListOf<String>()
        override val backendName = "FAKE"
        override suspend fun generate(systemInstruction: String, prompt: String): String {
            prompts += prompt
            if (delayMs > 0) delay(delayMs)
            if (fail) error("native failure")
            return if (systemInstruction == GroundedPrompts.ROUTER_SYSTEM) routerReply else answerReply
        }
    }

    private suspend fun ask(llm: LlmEngine?, q: String, lang: ContentLanguage = ContentLanguage.EN) =
        AskKnowledgeUseCase(retriever, llm = { llm }, clock = { 0L }).ask(q, lang, QueryContext())

    // --- Router parsing -----------------------------------------------------------------------

    @Test
    fun routerKeepsOnlyKnownIdsInOrder() {
        val valid = setOf(mahinda.id, mihintale.id)
        assertEquals(listOf(mihintale.id, mahinda.id),
            GroundedPrompts.parseRouterReply("IDs: place.mihintale, person.mahinda_thera, person.invented", valid))
        assertEquals(emptyList<String>(), GroundedPrompts.parseRouterReply("NONE", valid))
        assertEquals(emptyList<String>(), GroundedPrompts.parseRouterReply("person.devanampiya_tissa", valid))
    }

    @Test
    fun routerPromptStaysWithinBudget() {
        val many = (1..500).map { card("term.t$it", "Term $it", "A long description of term number $it. More text.") }
        val prompt = GroundedPrompts.routerPrompt("q", many)
        assertTrue(prompt.length < GroundedPrompts.MAX_CATALOGUE_CHARS + 100)
    }

    // --- Verifier -----------------------------------------------------------------------------

    @Test
    fun verifierAcceptsCitedGroundedAnswer() {
        val check = AnswerVerifier.verify(
            "Mahinda is remembered as the monk who brought Buddhism to Sri Lanka [1].",
            listOf(mahinda), "Who brought Buddhism to Sri Lanka?"
        )
        assertEquals(AnswerVerdict.VALID, check.verdict)
        assertEquals(listOf(1), check.citedSources)
    }

    @Test
    fun verifierRejectsCommonFailures() {
        val q = "Who was Mahinda?"
        assertEquals(AnswerVerdict.NO_CITATION, AnswerVerifier.verify("Mahinda was a monk.", listOf(mahinda), q).verdict)
        assertEquals(AnswerVerdict.INVALID_CITATION, AnswerVerifier.verify("Mahinda was a monk [3].", listOf(mahinda), q).verdict)
        assertEquals(AnswerVerdict.UNCITED_SENTENCE,
            AnswerVerifier.verify("Mahinda was a monk [1]. He met the king at Mihintale.", listOf(mahinda), q).verdict)
        assertEquals(AnswerVerdict.ABSTAINED, AnswerVerifier.verify("ABSTAIN", listOf(mahinda), q).verdict)
        assertEquals(AnswerVerdict.EMPTY, AnswerVerifier.verify("  ", listOf(mahinda), q).verdict)
    }

    @Test
    fun verifierRejectsInventedNumbersAndFacts() {
        val q = "Who was Mahinda?"
        val invented = AnswerVerifier.verify("Mahinda arrived in 247 BCE [1].", listOf(mahinda), q)
        assertEquals(AnswerVerdict.UNSUPPORTED_CONTENT, invented.verdict)
        assertTrue("247" in invented.unsupportedTerms)

        val fabricated = AnswerVerifier.verify(
            "Mahinda founded seventeen monasteries across Kandy using golden elephants [1].", listOf(mahinda), q
        )
        assertEquals(AnswerVerdict.UNSUPPORTED_CONTENT, fabricated.verdict)
    }

    // --- Cascade ------------------------------------------------------------------------------

    @Test
    fun withoutModelBehavesLikePhaseOne() = runTest {
        val result = ask(null, "Who was Mahinda Thera?")
        assertTrue(result.answer is KnowledgeAnswer.Found)
        assertTrue(result.lessons.none { it is GenerationOutcome })
    }

    @Test
    fun validGenerationIsShownWithSources() = runTest {
        val llm = FakeLlm(answerReply = "Mahinda is remembered as the monk who brought Buddhism to Sri Lanka [1].")
        val result = ask(llm, "Who was Mahinda Thera?")
        val generated = result.answer as KnowledgeAnswer.Generated
        assertFalse(generated.routedByModel)
        assertEquals(mahinda.id, generated.sources.single().card.id)
        assertEquals("VALID", result.lessons.filterIsInstance<GenerationOutcome>().single().verdict)
        assertEquals(1, llm.prompts.size) // retrieval hit: no routing call
    }

    @Test
    fun rejectedGenerationFallsBackToVerbatimCards() = runTest {
        val result = ask(FakeLlm(answerReply = "Mahinda was born in 280 BCE [1]."), "Who was Mahinda Thera?")
        assertTrue(result.answer is KnowledgeAnswer.Found)
        assertEquals("UNSUPPORTED_CONTENT", result.lessons.filterIsInstance<GenerationOutcome>().single().verdict)
    }

    @Test
    fun routerRescuesNameFreeQuestion() = runTest {
        val llm = FakeLlm(
            routerReply = "person.mahinda_thera",
            answerReply = "Mahinda is remembered as the monk who brought Buddhism to Sri Lanka [1]."
        )
        val result = ask(llm, "Who brought Buddhism here?")
        val generated = result.answer as KnowledgeAnswer.Generated
        assertTrue(generated.routedByModel)
        assertEquals(listOf(mahinda.id), result.lessons.filterIsInstance<ModelRouting>().single().cardIds)
        assertTrue(result.lessons.none { it is KnowledgeGap })
    }

    @Test
    fun routedCardsTheModelRejectsBecomeAMiss() = runTest {
        val result = ask(FakeLlm(routerReply = "place.mihintale", answerReply = "ABSTAIN"), "What is the entrance fee?")
        assertTrue(result.answer is KnowledgeAnswer.NotInRecords)
        assertTrue(result.lessons.any { it is KnowledgeGap })
    }

    @Test
    fun routerFindingNothingAbstains() = runTest {
        val result = ask(FakeLlm(routerReply = "NONE"), "Where can I buy food?")
        assertTrue(result.answer is KnowledgeAnswer.NotInRecords)
        assertTrue(result.lessons.any { it is KnowledgeGap })
        assertTrue(result.lessons.none { it is ModelRouting })
    }

    @Test
    fun sinhalaNeverGeneratesButCanRoute() = runTest {
        val llm = FakeLlm(routerReply = "person.mahinda_thera", answerReply = "should not be used [1].")
        val result = ask(llm, "බුදු දහම ගෙනා තැනැත්තා", ContentLanguage.SI)
        val found = result.answer as KnowledgeAnswer.Found
        assertTrue(found.routedByModel)
        assertEquals(1, llm.prompts.size) // router only
    }

    @Test
    fun modelFailureOrTimeoutNeverBreaksAnswering() = runTest {
        val failed = ask(FakeLlm(fail = true), "Who was Mahinda Thera?")
        assertTrue(failed.answer is KnowledgeAnswer.Found)
        assertEquals("ERROR", failed.lessons.filterIsInstance<GenerationOutcome>().single().verdict)

        val slow = ask(FakeLlm(delayMs = AskKnowledgeUseCase.DEFAULT_TIMEOUT_MS + 1), "Who was Mahinda Thera?")
        assertTrue(slow.answer is KnowledgeAnswer.Found)
        assertEquals("TIMEOUT", slow.lessons.filterIsInstance<GenerationOutcome>().single().verdict)
    }

    // --- Evaluation ---------------------------------------------------------------------------

    @Test
    fun evaluatorScoresWrongAnswersStrictly() = runTest {
        val llm = FakeLlm(routerReply = "place.mihintale", answerReply = "Mihintale is a hill complex east of Anuradhapura [1].")
        val report = KnowledgeEvaluator(pack, includeDrafts = false, llm = llm, clock = { 0L }).run(
            listOf(
                EvalQuestion("Who was Mahinda Thera?", "EN", listOf(mahinda.id)),
                EvalQuestion("Where can I buy food?", "EN", emptyList())
            )
        )
        // Q1: retrieval hit, but the fake answer cites Mihintale's text against Mahinda's card:
        // the verifier rejects it, so the card is shown verbatim and scored correct.
        // Q2: unanswerable, but the router picks Mihintale and the answer passes: a wrong answer.
        assertEquals(1.0, report.groundedAnswerRate, 1e-9)
        assertEquals(1, report.wrongAnswers)
        assertEquals("GENERATED", report.rows[1].outcome)
    }
}
