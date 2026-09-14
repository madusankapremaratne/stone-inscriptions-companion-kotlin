package org.sellipi.companion.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sites")
data class SiteEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name_si") val nameSi: String,
    @ColumnInfo(name = "name_ta") val nameTa: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "geofence_radius_m") val geofenceRadiusM: Float,
    @ColumnInfo(name = "permit_reference") val permitReference: String
)

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "period_number") val periodNumber: Int,
    @ColumnInfo(name = "label_si") val labelSi: String,
    @ColumnInfo(name = "label_ta") val labelTa: String,
    @ColumnInfo(name = "label_en") val labelEn: String,
    @ColumnInfo(name = "year_start") val yearStart: Int,
    @ColumnInfo(name = "year_end") val yearEnd: Int,
    @ColumnInfo(name = "chart_column_refs") val chartColumnRefs: String
)

@Entity(tableName = "letters")
data class LetterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "row_index") val rowIndex: Int,
    @ColumnInfo(name = "modern_sinhala_codepoint") val modernSinhalaCodepoint: String,
    @ColumnInfo(name = "romanisation") val romanisation: String,
    @ColumnInfo(name = "letter_name") val letterName: String,
    @ColumnInfo(name = "glyph_image_path") val glyphImagePath: String?
)

@Entity(
    tableName = "letter_forms",
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
        Index(value = ["letter_id", "period_id"])
    ]
)
data class LetterFormEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "letter_id") val letterId: String,
    @ColumnInfo(name = "period_id") val periodId: String,
    @ColumnInfo(name = "grid_row") val gridRow: Int,
    @ColumnInfo(name = "grid_col") val gridCol: Int,
    @ColumnInfo(name = "vector_path") val vectorPath: String?,
    @ColumnInfo(name = "image_asset_path") val imageAssetPath: String?,
    @ColumnInfo(name = "is_attested") val isAttested: Int,
    @ColumnInfo(name = "is_reconstructed") val isReconstructed: Int,
    @ColumnInfo(name = "source_inscription_ref") val sourceInscriptionRef: String?,
    @ColumnInfo(name = "provenance") val provenance: String
)

@Entity(
    tableName = "inscriptions",
    foreignKeys = [
        ForeignKey(
            entity = SiteEntity::class,
            parentColumns = ["id"],
            childColumns = ["site_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PeriodEntity::class,
            parentColumns = ["id"],
            childColumns = ["primary_period_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["site_id"]),
        Index(value = ["primary_period_id"])
    ]
)
data class InscriptionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "site_id") val siteId: String,
    @ColumnInfo(name = "name_si") val nameSi: String,
    @ColumnInfo(name = "name_ta") val nameTa: String,
    @ColumnInfo(name = "name_en") val nameEn: String,
    @ColumnInfo(name = "date_range_start") val dateRangeStart: Int,
    @ColumnInfo(name = "date_range_end") val dateRangeEnd: Int,
    @ColumnInfo(name = "dating_basis") val datingBasis: String,
    @ColumnInfo(name = "primary_period_id") val primaryPeriodId: String,
    @ColumnInfo(name = "reference_photo") val referencePhoto: String?,
    @ColumnInfo(name = "arcore_target_score") val arcoreTargetScore: Int,
    @ColumnInfo(name = "alignment_strategy") val alignmentStrategy: String,
    @ColumnInfo(name = "source_citation") val sourceCitation: String
)

@Entity(
    tableName = "transcription_lines",
    foreignKeys = [
        ForeignKey(
            entity = InscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inscription_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["inscription_id"])
    ]
)
data class TranscriptionLineEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "inscription_id") val inscriptionId: String,
    @ColumnInfo(name = "line_number") val lineNumber: Int,
    @ColumnInfo(name = "text_original") val textOriginal: String,
    @ColumnInfo(name = "text_modern_sinhala") val textModernSinhala: String,
    @ColumnInfo(name = "translation_si") val translationSi: String,
    @ColumnInfo(name = "translation_ta") val translationTa: String,
    @ColumnInfo(name = "translation_en") val translationEn: String
)

@Entity(
    tableName = "glyph_occurrences",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptionLineEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcription_line_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LetterEntity::class,
            parentColumns = ["id"],
            childColumns = ["letter_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transcription_line_id"]),
        Index(value = ["letter_id"])
    ]
)
data class GlyphOccurrenceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "transcription_line_id") val transcriptionLineId: String,
    @ColumnInfo(name = "position") val position: Int,
    @ColumnInfo(name = "letter_id") val letterId: String,
    @ColumnInfo(name = "bbox_x") val bboxX: Float,
    @ColumnInfo(name = "bbox_y") val bboxY: Float,
    @ColumnInfo(name = "bbox_w") val bboxW: Float,
    @ColumnInfo(name = "bbox_h") val bboxH: Float
)

@Entity(
    tableName = "capture_sessions",
    foreignKeys = [
        ForeignKey(
            entity = InscriptionEntity::class,
            parentColumns = ["id"],
            childColumns = ["inscription_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["inscription_id"])
    ]
)
data class CaptureSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "inscription_id") val inscriptionId: String,
    @ColumnInfo(name = "device_model") val deviceModel: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "gps_lat") val gpsLat: Double,
    @ColumnInfo(name = "gps_lon") val gpsLon: Double,
    @ColumnInfo(name = "gps_accuracy") val gpsAccuracy: Float,
    @ColumnInfo(name = "pose_matrix") val poseMatrix: String,
    @ColumnInfo(name = "scale_mm_per_px") val scaleMmPerPx: Float,
    @ColumnInfo(name = "image_paths") val imagePaths: String,
    @ColumnInfo(name = "permit_reference") val permitReference: String
)
