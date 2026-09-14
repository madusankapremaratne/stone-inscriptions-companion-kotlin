package org.sellipi.companion.domain.model

data class Site(
    val id: String,
    val nameSi: String,
    val nameTa: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double,
    val geofenceRadiusM: Float,
    val permitReference: String,
    val distanceFromUserM: Float? = null
)

data class Inscription(
    val id: String,
    val siteId: String,
    val nameSi: String,
    val nameTa: String,
    val nameEn: String,
    val dateRangeStart: Int,
    val dateRangeEnd: Int,
    val datingBasis: String,
    val primaryPeriodId: String,
    val referencePhoto: String?,
    val arcoreTargetScore: Int,
    val alignmentStrategy: String,
    val sourceCitation: String
)

data class Period(
    val id: String,
    val periodNumber: Int,
    val labelSi: String,
    val labelTa: String,
    val labelEn: String,
    val yearStart: Int,
    val yearEnd: Int,
    val chartColumnRefs: String
)

data class Letter(
    val id: String,
    val rowIndex: Int,
    val modernSinhalaCodepoint: String,
    val romanisation: String,
    val letterName: String,
    val glyphImagePath: String?
)

data class LetterForm(
    val id: String,
    val letterId: String,
    val periodId: String,
    val gridRow: Int,
    val gridCol: Int,
    val vectorPath: String?,
    val imageAssetPath: String?,
    val isAttested: Boolean,
    val isReconstructed: Boolean,
    val sourceInscriptionRef: String?,
    val provenance: String,
    val period: Period? = null
)

data class TranscriptionLine(
    val id: String,
    val inscriptionId: String,
    val lineNumber: Int,
    val textOriginal: String,
    val textModernSinhala: String,
    val translationSi: String,
    val translationTa: String,
    val translationEn: String,
    val glyphs: List<GlyphOccurrence> = emptyList()
)

data class GlyphOccurrence(
    val id: String,
    val transcriptionLineId: String,
    val position: Int,
    val letterId: String,
    val bboxX: Float,
    val bboxY: Float,
    val bboxW: Float,
    val bboxH: Float,
    val letter: Letter? = null
)

data class CaptureSession(
    val id: String,
    val inscriptionId: String,
    val deviceModel: String,
    val timestamp: Long,
    val gpsLat: Double,
    val gpsLon: Double,
    val gpsAccuracy: Float,
    val poseMatrix: String,
    val scaleMmPerPx: Float,
    val imagePaths: List<String>,
    val permitReference: String
)

data class QuadPoint(val x: Float, val y: Float)

data class InscriptionQuad(
    val topLeft: QuadPoint,
    val topRight: QuadPoint,
    val bottomRight: QuadPoint,
    val bottomLeft: QuadPoint
)
