package org.sellipi.companion.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import org.sellipi.companion.data.local.entity.CardLinkEntity
import org.sellipi.companion.data.local.entity.EntityAliasEntity
import org.sellipi.companion.data.local.entity.KnowledgeCardEntity

@Dao
interface KnowledgeDao {
    @Query("SELECT * FROM knowledge_cards")
    suspend fun getCards(): List<KnowledgeCardEntity>

    @Query("SELECT * FROM entity_aliases")
    suspend fun getAliases(): List<EntityAliasEntity>

    @Query("SELECT * FROM card_links")
    suspend fun getLinks(): List<CardLinkEntity>
}
