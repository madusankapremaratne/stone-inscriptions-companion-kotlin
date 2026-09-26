package org.sellipi.companion.domain.knowledge

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.sellipi.companion.domain.lessons.AliasMiss
import org.sellipi.companion.domain.lessons.GenerationOutcome
import org.sellipi.companion.domain.lessons.KnowledgeGap
import org.sellipi.companion.domain.lessons.LessonPayload
import org.sellipi.companion.domain.lessons.ModelRouting
import org.sellipi.companion.domain.llm.AnswerVerdict
import org.sellipi.companion.domain.llm.AnswerVerifier
import org.sellipi.companion.domain.llm.GroundedPrompts
import org.sellipi.companion.domain.llm.LlmEngine

interface KnowledgeRepository {
    suspend fun loadPack(): KnowledgePack
}

/**
 * Answers a visitor question from the curated pack, or abstains, and returns the lessons the
 * attempt produced. Holds per-session state for rephrase detection, so use one instance per
 * Ask screen.
 *
 * Cascade (docs §3, §8.3):
 * 1. Deterministic retrieval.
 * 2. If it misses and a model is available: the model routes the question to catalogue IDs.
 * 3. For English, the model writes a cited answer from the cards; it is shown only if
 *    [AnswerVerifier] accepts it, otherwise the cards are shown verbatim.
 * 4. Otherwise abstain.
 *
 * [llm] is read per question, so a model downloaded or disabled mid-session takes effect.
 */
class AskKnowledgeUseCase(
    private val retriever: KnowledgeRetriever,
    private val llm: () -> LlmEngine? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    private val rephrases = RephraseTracker(clock)

    data class Result(val answer: KnowledgeAnswer, val lessons: List<LessonPayload>)

    suspend fun ask(query: String, language: ContentLanguage, context: QueryContext): Result {
        val normalized = TextNormalizer.normalize(query)
        if (normalized.isBlank()) {
            return Result(KnowledgeAnswer.NotInRecords("", 0.0), emptyList())
        }
        val lessons = mutableListOf<LessonPayload>()
        val engine = llm()
        val retrieved = retriever.search(query, context)

        var cards: List<ScoredCard> = (retrieved as? KnowledgeAnswer.Found)?.cards.orEmpty()
        var routed = false
        if (cards.isEmpty() && engine != null) {
            cards = route(engine, query, lessons)
            routed = cards.isNotEmpty()
        }

        val topScore = (retrieved as? KnowledgeAnswer.NotInRecords)?.topScore ?: 0.0
        if (cards.isEmpty()) return miss(query, normalized, language, topScore, lessons)

        // Generation is English-only: Sinhala/Tamil show curated text verbatim (docs §3).
        val answer = if (engine != null && language == ContentLanguage.EN) {
            generate(engine, query, normalized, cards, routed, lessons)
        } else {
            null
        } ?: KnowledgeAnswer.Found(normalized, cards, routed)

        if (answer is KnowledgeAnswer.NotInRecords) return miss(query, normalized, language, topScore, lessons)
        rephrases.onHit(cards.first().card.id)?.let { lessons += it }
        return Result(answer, lessons)
    }

    private fun miss(
        query: String,
        normalized: String,
        language: ContentLanguage,
        topScore: Double,
        lessons: MutableList<LessonPayload>
    ): Result {
        rephrases.onMiss(query)
        lessons += KnowledgeGap(TextNormalizer.sanitizeForStorage(query), language.name, topScore)
        return Result(KnowledgeAnswer.NotInRecords(normalized, topScore), lessons)
    }

    private suspend fun route(engine: LlmEngine, query: String, lessons: MutableList<LessonPayload>): List<ScoredCard> {
        val catalogue = retriever.visibleCards
        if (catalogue.isEmpty()) return emptyList()
        val started = clock()
        val reply = runModel { engine.generate(GroundedPrompts.ROUTER_SYSTEM, GroundedPrompts.routerPrompt(query, catalogue)) }
            ?: return emptyList()
        val ids = GroundedPrompts.parseRouterReply(reply, catalogue.map { it.id }.toSet())
        if (ids.isEmpty()) return emptyList()
        lessons += ModelRouting(TextNormalizer.sanitizeForStorage(query), ids, clock() - started)
        val byId = catalogue.associateBy { it.id }
        return ids.mapNotNull { byId[it] }.map { ScoredCard(it, score = 0.0, matchedAlias = null) }
    }

    private suspend fun generate(
        engine: LlmEngine,
        query: String,
        normalized: String,
        cards: List<ScoredCard>,
        routed: Boolean,
        lessons: MutableList<LessonPayload>
    ): KnowledgeAnswer? {
        val sources = cards.map { it.card }
        val started = clock()
        var failure: String? = null
        val reply = runModel(onFailure = { failure = it }) {
            engine.generate(GroundedPrompts.ANSWER_SYSTEM, GroundedPrompts.answerPrompt(query, sources))
        }
        val latency = clock() - started
        if (reply == null) {
            lessons += GenerationOutcome(failure ?: "ERROR", latency, engine.backendName, sources.size, routed)
            return null
        }
        val check = AnswerVerifier.verify(reply, sources, query)
        lessons += GenerationOutcome(check.verdict.name, latency, engine.backendName, sources.size, routed)
        return when (check.verdict) {
            AnswerVerdict.VALID -> KnowledgeAnswer.Generated(normalized, check.text, cards, routed)
            // The model read the routed cards and said they don't answer: trust the abstention.
            AnswerVerdict.ABSTAINED -> if (routed) KnowledgeAnswer.NotInRecords(normalized, 0.0) else null
            else -> null
        }
    }

    /** Runs a model call with a timeout; returns null on any failure (the app must never crash on it). */
    private suspend fun runModel(onFailure: (String) -> Unit = {}, call: suspend () -> String): String? =
        try {
            withTimeout(timeoutMs) { call() }
        } catch (e: TimeoutCancellationException) {
            onFailure("TIMEOUT"); null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure("ERROR"); null
        }

    companion object {
        /** Generous for a 4 GB phone on CPU; the evaluation screen reports real latencies. */
        const val DEFAULT_TIMEOUT_MS = 90_000L
    }
}

/**
 * A miss followed within [windowMs] by a hit suggests the missed wording names the hit card.
 * Only the most recent miss is kept, and it is consumed by the first hit.
 */
class RephraseTracker(
    private val clock: () -> Long,
    private val windowMs: Long = DEFAULT_WINDOW_MS
) {
    private var lastMissQuery: String? = null
    private var lastMissAt = 0L

    fun onMiss(query: String) {
        lastMissQuery = query
        lastMissAt = clock()
    }

    fun onHit(cardId: String): AliasMiss? {
        val missed = lastMissQuery ?: return null
        lastMissQuery = null
        val elapsed = clock() - lastMissAt
        if (elapsed > windowMs) return null
        val sanitized = TextNormalizer.sanitizeForStorage(missed)
        if (sanitized.isBlank()) return null
        return AliasMiss(missQueryNorm = sanitized, hitCardId = cardId, msBetween = elapsed)
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 60_000L
    }
}
