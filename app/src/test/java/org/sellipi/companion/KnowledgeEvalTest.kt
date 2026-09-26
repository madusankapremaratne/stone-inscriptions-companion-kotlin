package org.sellipi.companion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.domain.knowledge.CardAlias
import org.sellipi.companion.domain.knowledge.CardLink
import org.sellipi.companion.domain.knowledge.CurationStatus
import org.sellipi.companion.domain.knowledge.KnowledgeAnswer
import org.sellipi.companion.domain.knowledge.KnowledgeCard
import org.sellipi.companion.domain.knowledge.KnowledgePack
import org.sellipi.companion.domain.knowledge.KnowledgeRetriever
import org.sellipi.companion.domain.knowledge.LinkTarget
import org.sellipi.companion.domain.knowledge.QueryContext
import java.io.File

/**
 * Runs the shipped retriever over the real card sources (data/knowledge/cards) and the
 * Q-dev set, and prints the per-question report. Mirrors scripts/build_knowledge_pack.py's
 * card mapping, which is a straight 1:1 copy of fields.
 *
 * Gate: zero wrong answers (a wrong answer is this system's hallucination), and a minimum
 * answer rate on answerable questions.
 */
class KnowledgeEvalTest {

    @Serializable
    private data class Localized(val en: String? = null, val si: String? = null, val ta: String? = null)

    @Serializable
    private data class AliasSource(val text: String, val lang: String, val ambiguous: Boolean = false)

    @Serializable
    private data class LinkSource(val sites: List<String> = emptyList(), val inscriptions: List<String> = emptyList())

    @Serializable
    private data class CardSource(
        val id: String,
        val kind: String,
        val status: String,
        val title: Localized,
        val body: Localized,
        val aliases: List<AliasSource>,
        val links: LinkSource = LinkSource(),
        val sources: List<String>,
        @SerialName("confidence_note") val confidenceNote: String? = null
    )

    @Serializable
    private data class Question(
        val q: String,
        val lang: String,
        val expect: List<String>,
        val inscriptionId: String? = null,
        val siteId: String? = null,
        val note: String? = null
    )

    @Serializable
    private data class QuestionSet(val description: String = "", val questions: List<Question>)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun qDevRetrievalMeetsGate() {
        val root = knowledgeRoot()
        val pack = loadPack(File(root, "cards"))
        val questions = json.decodeFromString<QuestionSet>(File(root, "eval/q_dev.json").readText()).questions
        val retriever = KnowledgeRetriever(pack, includeDrafts = true)

        var answerable = 0
        var answeredCorrectly = 0
        var abstains = 0
        var correctAbstains = 0
        val wrong = mutableListOf<String>()
        val report = StringBuilder("\nQ-dev retrieval report (threshold ${KnowledgeRetriever.DEFAULT_THRESHOLD})\n")

        for (question in questions) {
            val answer = retriever.search(question.q, QueryContext(question.inscriptionId, question.siteId))
            val got = (answer as? KnowledgeAnswer.Found)?.cards?.first()?.card?.id
            val score = when (answer) {
                is KnowledgeAnswer.Found -> answer.cards.first().score
                is KnowledgeAnswer.NotInRecords -> answer.topScore
            }
            val ok = if (question.expect.isEmpty()) got == null else got in question.expect
            if (question.expect.isNotEmpty()) answerable++
            if (question.expect.isNotEmpty() && ok) answeredCorrectly++
            if (got == null) {
                abstains++
                if (question.expect.isEmpty()) correctAbstains++
            } else if (!ok) {
                wrong += "${question.q} -> $got"
            }
            report.append(String.format("  %-4s %-5s %.2f  %-48s -> %s%n", if (ok) "ok" else "FAIL", question.lang, score, question.q, got ?: "ABSTAIN"))
        }

        val answerRate = answeredCorrectly.toDouble() / answerable
        val abstentionPrecision = if (abstains == 0) 1.0 else correctAbstains.toDouble() / abstains
        report.append(String.format("grounded answer rate %.2f (%d/%d), abstention precision %.2f (%d/%d), wrong answers %d%n",
            answerRate, answeredCorrectly, answerable, abstentionPrecision, correctAbstains, abstains, wrong.size))
        println(report)

        assertEquals("Wrong answers are hallucinations: $wrong", 0, wrong.size)
        assertTrue("Grounded answer rate $answerRate below gate", answerRate >= MIN_ANSWER_RATE)
    }

    private fun loadPack(dir: File): KnowledgePack {
        val sources = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
            .map { json.decodeFromString<CardSource>(it.readText()) }
        return KnowledgePack(
            cards = sources.map { s ->
                KnowledgeCard(
                    id = s.id, kind = s.kind,
                    titleEn = s.title.en!!, titleSi = s.title.si, titleTa = s.title.ta,
                    bodyEn = s.body.en!!, bodySi = s.body.si, bodyTa = s.body.ta,
                    sources = s.sources.joinToString("\n"), confidenceNote = s.confidenceNote,
                    status = CurationStatus.valueOf(s.status.uppercase())
                )
            },
            aliases = sources.flatMap { s -> s.aliases.map { CardAlias(it.text, s.id, it.lang, it.ambiguous) } },
            links = sources.flatMap { s ->
                s.links.sites.map { CardLink(s.id, LinkTarget.SITE, it) } +
                    s.links.inscriptions.map { CardLink(s.id, LinkTarget.INSCRIPTION, it) }
            }
        )
    }

    private fun knowledgeRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, "data/knowledge")
            if (File(candidate, "cards").isDirectory) return candidate
            dir = dir.parentFile
        }
        error("data/knowledge not found above ${System.getProperty("user.dir")}")
    }

    companion object {
        const val MIN_ANSWER_RATE = 0.85
    }
}
