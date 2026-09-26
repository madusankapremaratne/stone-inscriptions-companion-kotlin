package org.sellipi.companion.domain.knowledge

import org.sellipi.companion.domain.lessons.AliasMiss
import org.sellipi.companion.domain.lessons.KnowledgeGap
import org.sellipi.companion.domain.lessons.LessonPayload

interface KnowledgeRepository {
    suspend fun loadPack(): KnowledgePack
}

/**
 * Answers a visitor question from the curated pack, or abstains, and returns the lessons the
 * attempt produced. Holds per-session state for rephrase detection, so use one instance per
 * Ask screen.
 */
class AskKnowledgeUseCase(
    private val retriever: KnowledgeRetriever,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val rephrases = RephraseTracker(clock)

    data class Result(val answer: KnowledgeAnswer, val lessons: List<LessonPayload>)

    fun ask(query: String, language: ContentLanguage, context: QueryContext): Result {
        if (TextNormalizer.normalize(query).isBlank()) {
            return Result(KnowledgeAnswer.NotInRecords("", 0.0), emptyList())
        }
        val answer = retriever.search(query, context)
        val lessons = when (answer) {
            is KnowledgeAnswer.NotInRecords -> {
                rephrases.onMiss(query)
                listOf(
                    KnowledgeGap(
                        queryNorm = TextNormalizer.sanitizeForStorage(query),
                        language = language.name,
                        topScore = answer.topScore
                    )
                )
            }
            is KnowledgeAnswer.Found -> listOfNotNull(rephrases.onHit(answer.cards.first().card.id))
        }
        return Result(answer, lessons)
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
