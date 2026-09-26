package org.sellipi.companion.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// Written by scripts/build_knowledge_pack.py; column types and nullability must match exactly,
// or Room rejects the prepackaged database. Run scripts/check_room_schema.py after changing them.

@Entity(tableName = "knowledge_cards")
data class KnowledgeCardEntity(
    @PrimaryKey val id: String,
    val kind: String,
    @ColumnInfo(name = "title_en") val titleEn: String,
    @ColumnInfo(name = "title_si") val titleSi: String?,
    @ColumnInfo(name = "title_ta") val titleTa: String?,
    @ColumnInfo(name = "body_en") val bodyEn: String,
    @ColumnInfo(name = "body_si") val bodySi: String?,
    @ColumnInfo(name = "body_ta") val bodyTa: String?,
    val sources: String,
    @ColumnInfo(name = "confidence_note") val confidenceNote: String?,
    @ColumnInfo(name = "curation_status") val curationStatus: String
)

@Entity(tableName = "entity_aliases", primaryKeys = ["alias", "card_id"])
data class EntityAliasEntity(
    val alias: String,
    @ColumnInfo(name = "card_id") val cardId: String,
    val lang: String,
    val ambiguous: Boolean
)

@Entity(tableName = "card_links", primaryKeys = ["card_id", "target_type", "target_id"])
data class CardLinkEntity(
    @ColumnInfo(name = "card_id") val cardId: String,
    @ColumnInfo(name = "target_type") val targetType: String,
    @ColumnInfo(name = "target_id") val targetId: String
)
