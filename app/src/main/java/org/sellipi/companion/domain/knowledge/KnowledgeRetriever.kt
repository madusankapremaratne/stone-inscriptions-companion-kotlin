package org.sellipi.companion.domain.knowledge

/**
 * In-memory retrieval over the knowledge pack. A pack of a few hundred cards scores in
 * microseconds, and a pure-Kotlin retriever lets the same code run in the evaluation tests
 * (FTS5 is not reliably available in Android's framework SQLite, and FTS4's tokenizers are
 * untested on Sinhala/Tamil combining vowel signs).
 *
 * Scoring (all tunable against Q-dev, docs §7):
 * - alias match: 1.0, or [AMBIGUOUS_ALIAS_SCORE] for ambiguous aliases
 * - keyword overlap with title/body: up to [MAX_KEYWORD_SCORE]
 * - context boost for cards linked to the current inscription/site, only when something matched
 */
class KnowledgeRetriever(
    pack: KnowledgePack,
    includeDrafts: Boolean,
    private val threshold: Double = DEFAULT_THRESHOLD,
    private val maxResults: Int = DEFAULT_MAX_RESULTS
) {
    private class IndexedAlias(val key: String, val alias: CardAlias)
    private class IndexedCard(val card: KnowledgeCard, val titleTokens: Set<String>, val bodyTokens: Set<String>)

    private val cards: Map<String, IndexedCard>
    private val aliases: List<IndexedAlias>
    private val linksByCard: Map<String, List<CardLink>>

    init {
        val visible = pack.cards.filter { includeDrafts || it.status == CurationStatus.VERIFIED }
        cards = visible.associate { card ->
            val titles = listOfNotNull(card.titleEn, card.titleSi, card.titleTa).joinToString(" ")
            val bodies = listOfNotNull(card.bodyEn, card.bodySi, card.bodyTa).joinToString(" ")
            card.id to IndexedCard(card, TextNormalizer.tokens(titles).toSet(), TextNormalizer.tokens(bodies).toSet())
        }
        aliases = pack.aliases
            .filter { it.cardId in cards }
            .map { IndexedAlias(TextNormalizer.looseKey(it.alias), it) }
            .filter { it.key.length >= MIN_ALIAS_KEY_LENGTH }
            // Longest first so a full name wins over a fragment it contains.
            .sortedByDescending { it.key.length }
        linksByCard = pack.links.groupBy { it.cardId }
    }

    fun search(query: String, context: QueryContext = QueryContext()): KnowledgeAnswer {
        val normalized = TextNormalizer.normalize(query)
        if (normalized.isBlank() || cards.isEmpty()) return KnowledgeAnswer.NotInRecords(normalized, 0.0)

        val queryKey = TextNormalizer.looseKey(query)
        val queryTokens = TextNormalizer.tokens(query).toSet()

        val aliasHits = mutableMapOf<String, Pair<Double, String>>()
        val coveredTokens = mutableSetOf<String>()
        for (indexed in aliases) {
            if (!queryKey.contains(indexed.key)) continue
            coveredTokens += TextNormalizer.tokens(indexed.alias.alias)
            val score = if (indexed.alias.ambiguous) AMBIGUOUS_ALIAS_SCORE else 1.0
            val current = aliasHits[indexed.alias.cardId]
            if (current == null || score > current.first) aliasHits[indexed.alias.cardId] = score to indexed.alias.alias
        }
        // Words already counted as a name must not count again as keywords; otherwise an
        // ambiguous name ("Mahinda") plus its own keyword match would pass the threshold.
        val keywordTokens = queryTokens - coveredTokens

        val scored = cards.values.mapNotNull { indexed ->
            val aliasHit = aliasHits[indexed.card.id]
            val keyword = keywordScore(keywordTokens, queryTokens.size, indexed)
            var score = (aliasHit?.first ?: 0.0) + keyword
            if (score <= 0.0) return@mapNotNull null
            if (isLinked(indexed.card.id, context)) score += CONTEXT_BOOST
            ScoredCard(indexed.card, score, aliasHit?.second)
        }.sortedByDescending { it.score }

        val accepted = scored.filter { it.score >= threshold }.take(maxResults)
        return if (accepted.isEmpty()) {
            KnowledgeAnswer.NotInRecords(normalized, scored.firstOrNull()?.score ?: 0.0)
        } else {
            KnowledgeAnswer.Found(normalized, accepted)
        }
    }

    private fun keywordScore(tokens: Set<String>, queryTokenCount: Int, card: IndexedCard): Double {
        if (tokens.isEmpty() || queryTokenCount == 0) return 0.0
        val weighted = tokens.sumOf { token ->
            when (token) {
                in card.titleTokens -> 1.0
                in card.bodyTokens -> BODY_TOKEN_WEIGHT
                else -> 0.0
            }
        }
        return (weighted / queryTokenCount) * MAX_KEYWORD_SCORE
    }

    private fun isLinked(cardId: String, context: QueryContext): Boolean =
        linksByCard[cardId].orEmpty().any { link ->
            (link.targetType == LinkTarget.INSCRIPTION && link.targetId == context.inscriptionId) ||
                (link.targetType == LinkTarget.SITE && link.targetId == context.siteId)
        }

    companion object {
        const val DEFAULT_THRESHOLD = 0.5
        const val DEFAULT_MAX_RESULTS = 2
        const val AMBIGUOUS_ALIAS_SCORE = 0.3
        const val MAX_KEYWORD_SCORE = 0.6
        const val BODY_TOKEN_WEIGHT = 0.5
        const val CONTEXT_BOOST = 0.15
        const val MIN_ALIAS_KEY_LENGTH = 3
    }
}
