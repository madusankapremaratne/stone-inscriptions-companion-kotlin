package org.sellipi.companion.domain.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.sellipi.companion.domain.knowledge.AskKnowledgeUseCase
import org.sellipi.companion.domain.knowledge.ContentLanguage
import org.sellipi.companion.domain.knowledge.KnowledgeAnswer
import org.sellipi.companion.domain.knowledge.KnowledgePack
import org.sellipi.companion.domain.knowledge.KnowledgeRetriever
import org.sellipi.companion.domain.knowledge.QueryContext
import org.sellipi.companion.domain.lessons.GenerationOutcome

/** Same format as data/knowledge/eval/q_dev.json. */
@Serializable
data class EvalQuestion(
    val q: String,
    val lang: String,
    val expect: List<String>,
    val inscriptionId: String? = null,
    val siteId: String? = null,
    val note: String? = null
)

@Serializable
data class EvalQuestionSet(val description: String = "", val questions: List<EvalQuestion>)

@Serializable
data class EvalRow(
    val question: String,
    val lang: String,
    val expect: List<String>,
    /** GENERATED, FOUND, ROUTED (model-picked cards shown verbatim) or ABSTAIN. */
    val outcome: String,
    val cardIds: List<String>,
    val answerText: String? = null,
    val verdict: String? = null,
    val latencyMs: Long,
    val correct: Boolean,
    val wrong: Boolean
)

@Serializable
data class EvalReport(
    val backend: String,
    val modelEnabled: Boolean,
    val questions: Int,
    val answerable: Int,
    val groundedAnswerRate: Double,
    val abstentionPrecision: Double,
    val wrongAnswers: Int,
    /** Share of answered English questions where the model's text passed the verifier. */
    val generatedShare: Double,
    /** Verifier verdict counts across all generation attempts (hallucination-guard metric). */
    val verdictCounts: Map<String, Int>,
    val medianLatencyMs: Long,
    val rows: List<EvalRow>
)

object EvalScorer {

    private val CITATION = Regex("""\[(\d+)]""")

    fun row(question: EvalQuestion, answer: KnowledgeAnswer, verdicts: List<String>, latencyMs: Long): EvalRow {
        val (outcome, ids, text) = when (answer) {
            is KnowledgeAnswer.Generated -> {
                val cited = CITATION.findAll(answer.text).map { it.groupValues[1].toInt() }.distinct()
                    .mapNotNull { answer.sources.getOrNull(it - 1)?.card?.id }.toList()
                Triple("GENERATED", cited, answer.text)
            }
            is KnowledgeAnswer.Found ->
                Triple(if (answer.routedByModel) "ROUTED" else "FOUND", listOf(answer.cards.first().card.id), null)
            is KnowledgeAnswer.NotInRecords -> Triple("ABSTAIN", emptyList(), null)
        }
        val answerable = question.expect.isNotEmpty()
        val correct = if (answerable) ids.isNotEmpty() && ids.all { it in question.expect } else ids.isEmpty()
        val wrong = ids.isNotEmpty() && !correct
        return EvalRow(question.q, question.lang, question.expect, outcome, ids, text, verdicts.lastOrNull(), latencyMs, correct, wrong)
    }

    fun report(rows: List<EvalRow>, backend: String, modelEnabled: Boolean, verdictCounts: Map<String, Int>): EvalReport {
        val answerable = rows.filter { it.expect.isNotEmpty() }
        val abstains = rows.filter { it.outcome == "ABSTAIN" }
        val englishAnswered = rows.filter { it.lang == "EN" && it.outcome != "ABSTAIN" }
        val latencies = rows.map { it.latencyMs }.sorted()
        return EvalReport(
            backend = backend,
            modelEnabled = modelEnabled,
            questions = rows.size,
            answerable = answerable.size,
            groundedAnswerRate = ratio(answerable.count { it.correct }, answerable.size),
            abstentionPrecision = ratio(abstains.count { it.expect.isEmpty() }, abstains.size, empty = 1.0),
            wrongAnswers = rows.count { it.wrong },
            generatedShare = ratio(englishAnswered.count { it.outcome == "GENERATED" }, englishAnswered.size),
            verdictCounts = verdictCounts,
            medianLatencyMs = if (latencies.isEmpty()) 0 else latencies[latencies.size / 2],
            rows = rows
        )
    }

    private fun ratio(n: Int, d: Int, empty: Double = 0.0) = if (d == 0) empty else n.toDouble() / d
}

/**
 * Runs a question set through the full cascade (retrieval, routing, generation, verification)
 * exactly as the Ask screen does. A fresh use case per question keeps rephrase detection from
 * leaking between questions. Lessons are not recorded.
 */
class KnowledgeEvaluator(
    private val pack: KnowledgePack,
    private val includeDrafts: Boolean,
    private val llm: LlmEngine?,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun run(questions: List<EvalQuestion>, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): EvalReport {
        val retriever = KnowledgeRetriever(pack, includeDrafts)
        val verdictCounts = mutableMapOf<String, Int>()
        val rows = questions.mapIndexed { i, question ->
            val useCase = AskKnowledgeUseCase(retriever, llm = { llm }, clock = clock)
            val language = runCatching { ContentLanguage.valueOf(question.lang) }.getOrDefault(ContentLanguage.EN)
            val started = clock()
            val result = useCase.ask(question.q, language, QueryContext(question.inscriptionId, question.siteId))
            val latency = clock() - started
            val verdicts = result.lessons.filterIsInstance<GenerationOutcome>().map { it.verdict }
            verdicts.forEach { verdictCounts[it] = (verdictCounts[it] ?: 0) + 1 }
            onProgress(i + 1, questions.size)
            EvalScorer.row(question, result.answer, verdicts, latency)
        }
        return EvalScorer.report(rows, llm?.backendName ?: "NONE", llm != null, verdictCounts)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }
    }
}
