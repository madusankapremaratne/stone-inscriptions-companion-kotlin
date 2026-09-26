package org.sellipi.companion.data.repository

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sellipi.companion.data.local.database.SellipiDatabase
import org.sellipi.companion.domain.knowledge.CardAlias
import org.sellipi.companion.domain.knowledge.CardLink
import org.sellipi.companion.domain.knowledge.CurationStatus
import org.sellipi.companion.domain.knowledge.KnowledgeCard
import org.sellipi.companion.domain.knowledge.KnowledgePack
import org.sellipi.companion.domain.knowledge.KnowledgeRepository
import org.sellipi.companion.domain.knowledge.LinkTarget

class KnowledgeRepositoryImpl(private val db: SellipiDatabase) : KnowledgeRepository {

    override suspend fun loadPack(): KnowledgePack = withContext(Dispatchers.IO) {
        val dao = db.knowledgeDao()
        KnowledgePack(
            cards = dao.getCards().mapNotNull { e ->
                // An unknown status is treated as unreviewed content and dropped, never shown.
                val status = runCatching { CurationStatus.valueOf(e.curationStatus.uppercase()) }
                    .onFailure { Log.w("Knowledge", "Skipping card ${e.id}: status '${e.curationStatus}'") }
                    .getOrNull() ?: return@mapNotNull null
                KnowledgeCard(
                    id = e.id, kind = e.kind,
                    titleEn = e.titleEn, titleSi = e.titleSi, titleTa = e.titleTa,
                    bodyEn = e.bodyEn, bodySi = e.bodySi, bodyTa = e.bodyTa,
                    sources = e.sources, confidenceNote = e.confidenceNote, status = status
                )
            },
            aliases = dao.getAliases().map { CardAlias(it.alias, it.cardId, it.lang, it.ambiguous) },
            links = dao.getLinks().mapNotNull { e ->
                val target = runCatching { LinkTarget.valueOf(e.targetType.uppercase()) }.getOrNull()
                    ?: return@mapNotNull null
                CardLink(e.cardId, target, e.targetId)
            }
        )
    }
}
