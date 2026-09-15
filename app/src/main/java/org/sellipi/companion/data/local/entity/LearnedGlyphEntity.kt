package org.sellipi.companion.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "learned_glyphs",
    foreignKeys = [
        ForeignKey(
            entity = LetterEntity::class,
            parentColumns = ["id"],
            childColumns = ["letter_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["period_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["letter_id"]),
        Index(value = ["period_id"])
    ]
)
data class LearnedGlyphEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "letter_id") val letterId: String,
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "inscription_id") val inscriptionId: String,
    @ColumnInfo(name = "image_crop_path") val imageCropPath: String?,
    @ColumnInfo(name = "vector_path") val vectorPath: String?,
    @ColumnInfo(name = "confidence") val confidence: Float,
    @ColumnInfo(name = "researcher_notes") val researcherNotes: String?,
    @ColumnInfo(name = "timestamp") val timestamp: Long = System.currentTimeMillis()
)
