package org.sellipi.companion.domain.llm

import org.sellipi.companion.domain.knowledge.KnowledgeCard
import org.sellipi.companion.domain.knowledge.TextNormalizer

/**
 * An on-device generative model. Implementations must be safe to call from any coroutine
 * and may throw; callers treat any failure as "no model answer".
 */
interface LlmEngine {
    /** Short label for telemetry, e.g. "CPU" or "GPU". */
    val backendName: String

    suspend fun generate(systemInstruction: String, prompt: String): String
}

/**
 * Prompts and output checks for the two jobs the model does (docs/slm-lessons-architecture.md §8.3):
 *
 * 1. Router: when deterministic retrieval finds nothing, pick catalogue entries by meaning.
 *    Output is accepted only as IDs that exist in the catalogue.
 * 2. Answer: write a short English answer from numbered sources, citing every sentence.
 *    The reply is rejected unless [verifyAnswer] passes; the caller then falls back to
 *    showing the cards verbatim.
 */
object GroundedPrompts {

    const val ABSTAIN = "ABSTAIN"
    const val NONE = "NONE"
    const val MAX_ROUTED_CARDS = 2

    /** Keeps the catalogue prompt well inside the model's 4,096-token context. */
    const val MAX_CATALOGUE_CHARS = 9_000
    const val MAX_ANSWER_CHARS = 700

    val ROUTER_SYSTEM = """
        You match a visitor's question to entries in a catalogue about ancient Sri Lankan inscriptions.
        Reply with the IDs of at most two entries that answer the question, separated by commas.
        Reply with IDs only, copied exactly from the catalogue.
        If no entry answers the question, reply $NONE.
    """.trimIndent()

    val ANSWER_SYSTEM = """
        You answer a visitor's question using ONLY the numbered sources provided.
        Write at most three short sentences in plain English.
        End every sentence with the number of the source it comes from in square brackets, like [1].
        Never add names, dates, numbers or facts that are not in the sources.
        If the sources do not answer the question, reply exactly $ABSTAIN.
    """.trimIndent()

    fun routerPrompt(question: String, cards: List<KnowledgeCard>): String {
        val sb = StringBuilder("Catalogue:\n")
        for (card in cards) {
            val line = "- ${card.id}: ${card.titleEn}. ${firstSentence(card.bodyEn)}\n"
            if (sb.length + line.length > MAX_CATALOGUE_CHARS) break
            sb.append(line)
        }
        sb.append("\nQuestion: ").append(question.trim()).append("\nIDs:")
        return sb.toString()
    }

    fun parseRouterReply(reply: String, validIds: Set<String>): List<String> =
        ID_PATTERN.findAll(reply.lowercase())
            .map { it.value }
            .filter { it in validIds }
            .distinct()
            .take(MAX_ROUTED_CARDS)
            .toList()

    fun answerPrompt(question: String, cards: List<KnowledgeCard>): String {
        val sb = StringBuilder("Sources:\n")
        cards.forEachIndexed { i, card ->
            sb.append('[').append(i + 1).append("] ").append(card.titleEn).append(": ").append(card.bodyEn.trim()).append("\n")
        }
        sb.append("\nQuestion: ").append(question.trim()).append("\nAnswer:")
        return sb.toString()
    }

    private val ID_PATTERN = Regex("""[a-z]+\.[a-z0-9_]+""")

    private fun firstSentence(text: String): String {
        val trimmed = text.trim()
        val end = Regex("""[.!?](\s|$)""").find(trimmed)?.range?.first
        return if (end == null) trimmed.take(160) else trimmed.substring(0, end).take(160)
    }
}

/** Why a generated answer was accepted or rejected. Stored in lessons; names are stable. */
enum class AnswerVerdict {
    VALID,
    ABSTAINED,
    EMPTY,
    NO_CITATION,
    INVALID_CITATION,
    UNCITED_SENTENCE,
    UNSUPPORTED_CONTENT
}

data class AnswerCheck(
    val verdict: AnswerVerdict,
    /** Cleaned answer text with [n] markers; only meaningful when VALID. */
    val text: String,
    /** 1-based source numbers cited, in first-cited order. */
    val citedSources: List<Int>,
    /** Terms in the answer not found in any cited source (for diagnostics). */
    val unsupportedTerms: List<String> = emptyList()
)

/**
 * Rejects answers that are not traceably grounded. A deliberately strict, lexical check:
 * it cannot prove an answer is faithful, but it catches the common failure modes of a small
 * model (missing/invented citations, invented numbers, names and terms).
 */
object AnswerVerifier {

    /** Share of an answer sentence's content words that may be absent from its cited sources. */
    const val MAX_UNSUPPORTED_RATIO = 0.34

    private val CITATION = Regex("""\[(\d+)]""")
    private val NUMBER = Regex("""\d+""")
    private val SENTENCE_SPLIT = Regex("""(?<=[.!?])\s+""")

    private val FUNCTION_WORDS = setOf(
        "also", "after", "before", "because", "been", "being", "from", "have", "into", "known",
        "many", "more", "most", "only", "other", "over", "such", "than", "that", "their", "them",
        "then", "there", "these", "they", "this", "those", "under", "used", "very", "well", "were",
        "what", "when", "where", "which", "while", "with", "would", "about", "said", "says"
    )

    fun verify(reply: String, sources: List<KnowledgeCard>, question: String): AnswerCheck {
        val text = reply.trim().removeSurrounding("\"").trim().take(GroundedPrompts.MAX_ANSWER_CHARS)
        if (text.isEmpty()) return AnswerCheck(AnswerVerdict.EMPTY, "", emptyList())
        if (text.uppercase().startsWith(GroundedPrompts.ABSTAIN)) return AnswerCheck(AnswerVerdict.ABSTAINED, "", emptyList())

        val cited = CITATION.findAll(text).map { it.groupValues[1].toInt() }.toList()
        if (cited.isEmpty()) return AnswerCheck(AnswerVerdict.NO_CITATION, text, emptyList())
        if (cited.any { it < 1 || it > sources.size }) {
            return AnswerCheck(AnswerVerdict.INVALID_CITATION, text, cited.distinct())
        }

        val questionTerms = contentTerms(question)
        val unsupported = mutableListOf<String>()
        for (sentence in text.split(SENTENCE_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }) {
            val sentenceCites = CITATION.findAll(sentence).map { it.groupValues[1].toInt() }.toSet()
            if (sentenceCites.isEmpty()) {
                return AnswerCheck(AnswerVerdict.UNCITED_SENTENCE, text, cited.distinct())
            }
            val sourceText = sentenceCites.joinToString(" ") { sourceText(sources[it - 1]) }
            val sourceTerms = contentTerms(sourceText) + questionTerms
            val prose = CITATION.replace(sentence, " ")

            // Any number must appear in the cited sources (dates, reign years, counts).
            NUMBER.findAll(prose).map { it.value }.filter { it !in sourceText }.forEach { unsupported += it }

            val terms = contentTerms(prose)
            val missing = terms.filter { it !in sourceTerms }
            unsupported += missing
            if (terms.isNotEmpty() && missing.size.toDouble() / terms.size > MAX_UNSUPPORTED_RATIO) {
                return AnswerCheck(AnswerVerdict.UNSUPPORTED_CONTENT, text, cited.distinct(), unsupported.distinct())
            }
        }
        if (unsupported.any { it.all(Char::isDigit) }) {
            return AnswerCheck(AnswerVerdict.UNSUPPORTED_CONTENT, text, cited.distinct(), unsupported.distinct())
        }
        return AnswerCheck(AnswerVerdict.VALID, text, cited.distinct(), unsupported.distinct())
    }

    private fun sourceText(card: KnowledgeCard) =
        listOfNotNull(card.titleEn, card.bodyEn, card.confidenceNote).joinToString(" ")

    /** Lower-cased words of 4+ letters minus function words, crudely de-pluralised. */
    private fun contentTerms(text: String): Set<String> =
        TextNormalizer.normalize(text).split(' ')
            .filter { it.length >= 4 && it.none(Char::isDigit) && it !in FUNCTION_WORDS }
            .map { stem(it) }
            .toSet()

    // Applied to both answer and source words, so it only needs to be consistent, not correct.
    private fun stem(word: String): String = when {
        word.endsWith("ies") && word.length > 5 -> word.dropLast(3) + "y"
        word.endsWith("s") && !word.endsWith("ss") && word.length > 4 -> word.dropLast(1)
        else -> word
    }
}
