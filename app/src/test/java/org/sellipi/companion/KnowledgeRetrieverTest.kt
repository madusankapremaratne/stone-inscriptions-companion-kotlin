package org.sellipi.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.domain.knowledge.AskKnowledgeUseCase
import org.sellipi.companion.domain.knowledge.CardAlias
import org.sellipi.companion.domain.knowledge.CardLink
import org.sellipi.companion.domain.knowledge.ContentLanguage
import org.sellipi.companion.domain.knowledge.CurationStatus
import org.sellipi.companion.domain.knowledge.KnowledgeAnswer
import org.sellipi.companion.domain.knowledge.KnowledgeCard
import org.sellipi.companion.domain.knowledge.KnowledgePack
import org.sellipi.companion.domain.knowledge.KnowledgeRetriever
import org.sellipi.companion.domain.knowledge.LinkTarget
import org.sellipi.companion.domain.knowledge.QueryContext
import org.sellipi.companion.domain.knowledge.RephraseTracker
import org.sellipi.companion.domain.knowledge.TextNormalizer
import org.sellipi.companion.domain.lessons.AliasMiss
import org.sellipi.companion.domain.lessons.KnowledgeGap

class KnowledgeRetrieverTest {

    private fun card(id: String, title: String, body: String, status: CurationStatus = CurationStatus.VERIFIED) =
        KnowledgeCard(id, "person", title, null, null, body, null, null, "src", null, status)

    private val pack = KnowledgePack(
        cards = listOf(
            card("tissa", "Devanampiya Tissa", "A king of Anuradhapura in the 3rd century BCE."),
            card("mahinda", "Mahinda Thera", "The monk who brought Buddhism to the island."),
            card("draft", "Draft Card", "Unreviewed text about Sigiriya.", CurationStatus.DRAFT)
        ),
        aliases = listOf(
            CardAlias("Devanampiya Tissa", "tissa", "en", ambiguous = false),
            CardAlias("දේවානම්පියතිස්ස", "tissa", "si", ambiguous = false),
            CardAlias("Tissa", "tissa", "en", ambiguous = true),
            CardAlias("Mahinda Thera", "mahinda", "en", ambiguous = false),
            CardAlias("Mahinda", "mahinda", "en", ambiguous = true),
            CardAlias("Sigiriya", "draft", "en", ambiguous = false)
        ),
        links = listOf(CardLink("mahinda", LinkTarget.SITE, "SITE_MIHINTALE"))
    )

    private val retriever = KnowledgeRetriever(pack, includeDrafts = false)

    private fun topId(answer: KnowledgeAnswer) = (answer as? KnowledgeAnswer.Found)?.cards?.first()?.card?.id

    @Test
    fun romanisationVariantsMatch() {
        assertEquals("tissa", topId(retriever.search("Who is Dewanam Piyathissa")))
        assertEquals("tissa", topId(retriever.search("DEVĀNAMPIYA  tissa?")))
    }

    @Test
    fun sinhalaMatchesIgnoringSpacesAndJoiners() {
        assertEquals("tissa", topId(retriever.search("දේවානම් පියතිස්ස රජු කවුද?")))
        assertEquals("tissa", topId(retriever.search("දේවානම්‍පියතිස්ස")))
    }

    @Test
    fun ambiguousNameAloneAbstains() {
        assertTrue(retriever.search("Who was Mahinda?") is KnowledgeAnswer.NotInRecords)
        assertTrue(retriever.search("Who was Mahinda IV?") is KnowledgeAnswer.NotInRecords)
        assertTrue(retriever.search("Tissa") is KnowledgeAnswer.NotInRecords)
    }

    @Test
    fun unrelatedQuestionAbstains() {
        val answer = retriever.search("What is the entrance fee?")
        assertTrue(answer is KnowledgeAnswer.NotInRecords)
    }

    @Test
    fun draftsHiddenUnlessIncluded() {
        assertTrue(retriever.search("Who built Sigiriya?") is KnowledgeAnswer.NotInRecords)
        val debug = KnowledgeRetriever(pack, includeDrafts = true)
        assertEquals("draft", topId(debug.search("Who built Sigiriya?")))
    }

    @Test
    fun contextBoostAppliesOnlyWhenSomethingMatched() {
        val atMihintale = QueryContext(siteId = "SITE_MIHINTALE")
        assertTrue(retriever.search("What is the entrance fee?", atMihintale) is KnowledgeAnswer.NotInRecords)

        val plain = retriever.search("Mahinda Thera") as KnowledgeAnswer.Found
        val boosted = retriever.search("Mahinda Thera", atMihintale) as KnowledgeAnswer.Found
        assertEquals(KnowledgeRetriever.CONTEXT_BOOST, boosted.cards.first().score - plain.cards.first().score, 1e-9)
    }

    @Test
    fun blankQueryAbstains() {
        assertTrue(retriever.search("  ?! ") is KnowledgeAnswer.NotInRecords)
    }

    @Test
    fun missThenRephraseRecordsAliasMiss() {
        var now = 0L
        val useCase = AskKnowledgeUseCase(retriever, clock = { now })

        val miss = useCase.ask("Who is Piyathissa king 42", ContentLanguage.EN, QueryContext())
        val gap = miss.lessons.single() as KnowledgeGap
        assertEquals("who is piyathissa king", gap.queryNorm)

        now += 10_000
        val hit = useCase.ask("Devanampiya Tissa", ContentLanguage.EN, QueryContext())
        val aliasMiss = hit.lessons.single() as AliasMiss
        assertEquals("tissa", aliasMiss.hitCardId)
        assertEquals(10_000L, aliasMiss.msBetween)

        // Consumed: a second hit does not repeat it.
        assertTrue(useCase.ask("Devanampiya Tissa", ContentLanguage.EN, QueryContext()).lessons.isEmpty())
    }

    @Test
    fun staleMissIsNotAnAlias() {
        var now = 0L
        val tracker = RephraseTracker(clock = { now })
        tracker.onMiss("something")
        now += RephraseTracker.DEFAULT_WINDOW_MS + 1
        assertNull(tracker.onHit("tissa"))
    }

    @Test
    fun sanitizeDropsEmailsAndDigits() {
        assertEquals(
            "call me at about mihintale",
            TextNormalizer.sanitizeForStorage("Call me at 0771234567, a.b@example.com about Mihintale")
        )
    }
}
