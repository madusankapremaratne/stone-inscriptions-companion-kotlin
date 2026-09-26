package org.sellipi.companion.domain.knowledge

/**
 * Curated knowledge pack (docs/slm-lessons-architecture.md §5.1). Cards are the only source
 * an answer may draw from; Sinhala/Tamil are shown verbatim, never generated.
 */
enum class ContentLanguage { EN, SI, TA }

/** DRAFT cards are visible only in debuggable builds; release builds show VERIFIED only. */
enum class CurationStatus { DRAFT, VERIFIED }

enum class LinkTarget { INSCRIPTION, SITE }

data class KnowledgeCard(
    val id: String,
    val kind: String,
    val titleEn: String,
    val titleSi: String?,
    val titleTa: String?,
    val bodyEn: String,
    val bodySi: String?,
    val bodyTa: String?,
    val sources: String,
    val confidenceNote: String?,
    val status: CurationStatus
) {
    fun title(lang: ContentLanguage): String = when (lang) {
        ContentLanguage.EN -> titleEn
        ContentLanguage.SI -> titleSi
        ContentLanguage.TA -> titleTa
    }?.takeIf { it.isNotBlank() } ?: titleEn

    /** Null when no curated translation exists; callers fall back to [bodyEn] and say so. */
    fun body(lang: ContentLanguage): String? = when (lang) {
        ContentLanguage.EN -> bodyEn
        ContentLanguage.SI -> bodySi
        ContentLanguage.TA -> bodyTa
    }?.takeIf { it.isNotBlank() }
}

/**
 * A surface form that names a card. [ambiguous] marks forms shared by several entities
 * (e.g. "Tissa" was borne by many kings), which alone must never produce an answer.
 */
data class CardAlias(
    val alias: String,
    val cardId: String,
    val lang: String,
    val ambiguous: Boolean
)

data class CardLink(
    val cardId: String,
    val targetType: LinkTarget,
    val targetId: String
)

data class KnowledgePack(
    val cards: List<KnowledgeCard>,
    val aliases: List<CardAlias>,
    val links: List<CardLink>
)

/** Where the visitor is asking from; linked cards get a small boost (Tier 0). */
data class QueryContext(
    val inscriptionId: String? = null,
    val siteId: String? = null
)

data class ScoredCard(
    val card: KnowledgeCard,
    val score: Double,
    val matchedAlias: String?
)

sealed interface KnowledgeAnswer {
    val normalizedQuery: String

    data class Found(
        override val normalizedQuery: String,
        val cards: List<ScoredCard>
    ) : KnowledgeAnswer

    /** Abstain: nothing in the curated record answers this. Never guess. */
    data class NotInRecords(
        override val normalizedQuery: String,
        val topScore: Double
    ) : KnowledgeAnswer
}
