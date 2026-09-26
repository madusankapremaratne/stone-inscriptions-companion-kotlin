package org.sellipi.companion.domain.lessons

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lessons are structured observations of where the app fell short (see docs/slm-lessons-architecture.md §5).
 * Everything in this package is plain Kotlin so it can be unit tested without Android.
 */
enum class LessonType {
    ALIGNMENT_OUTCOME, ALIGNMENT_CORRECTION, IDENTIFICATION_RECOVERY, KNOWLEDGE_GAP, ALIAS_MISS,
    MODEL_ROUTING, GENERATION_OUTCOME
}

/** observed = automatic telemetry, proposed = awaiting curator, verified = curator-approved. */
enum class LessonTrust { OBSERVED, PROPOSED, VERIFIED }

enum class AlignmentMode { OVERLAY, AR }

enum class AlignmentEndReason { EXITED, FALLBACK_TO_OVERLAY }

enum class SelectionMethod { NEARBY_LIST, SITE_FILTER, SEARCH }

@Serializable
sealed interface LessonPayload

/**
 * One Overlay or AR viewing session.
 *
 * [success] means alignment was *confirmed*: a glyph tap landed on a bounding box (Overlay)
 * or ARCore acquired the surface (AR). [glyphTapMisses] counts taps that hit no glyph,
 * a proxy for letters the visitor could not reach because the overlay was misaligned.
 */
@Serializable
@SerialName("alignment_outcome")
data class AlignmentOutcome(
    val mode: AlignmentMode,
    val success: Boolean,
    val timeToAlignMs: Long?,
    val sessionDurationMs: Long,
    val glyphTapHits: Int,
    val glyphTapMisses: Int,
    val endReason: AlignmentEndReason,
    val localHour: Int
) : LessonPayload

/**
 * How the visitor corrected the 4-point quad. Quads are flattened TL, TR, BR, BL as x,y pairs,
 * normalised to the viewport when [normalised] is true (device-independent), raw pixels otherwise.
 */
@Serializable
@SerialName("alignment_correction")
data class AlignmentCorrection(
    val initialQuad: List<Float>,
    val finalQuad: List<Float>,
    val cornerDragCount: Int,
    val normalised: Boolean
) : LessonPayload

/**
 * The visitor identified the inscription manually (geo list, site filter, or ID/name search).
 * The query text itself is never stored, only its length.
 */
@Serializable
@SerialName("identification_recovery")
data class IdentificationRecovery(
    val selectionMethod: SelectionMethod,
    val rankInList: Int,
    val listSize: Int,
    val searchQueryLength: Int,
    val locationAvailable: Boolean,
    val msToSelect: Long
) : LessonPayload

/**
 * A visitor question the knowledge pack could not answer. [queryNorm] is sanitised
 * (normalised, no digits or emails, max 120 chars); it is the curator's to-do list.
 */
@Serializable
@SerialName("knowledge_gap")
data class KnowledgeGap(
    val queryNorm: String,
    val language: String,
    val topScore: Double
) : LessonPayload

/**
 * A missed question followed shortly by a rephrase that hit [hitCardId]: [missQueryNorm]
 * is a candidate alias for that card, pending curator review.
 */
@Serializable
@SerialName("alias_miss")
data class AliasMiss(
    val missQueryNorm: String,
    val hitCardId: String,
    val msBetween: Long
) : LessonPayload

/**
 * Deterministic retrieval missed but the on-device model routed the question to [cardIds].
 * The curator can add the wording as an alias so retrieval handles it next release:
 * the model's successes become deterministic knowledge.
 */
@Serializable
@SerialName("model_routing")
data class ModelRouting(
    val queryNorm: String,
    val cardIds: List<String>,
    val latencyMs: Long
) : LessonPayload

/**
 * One attempt by the on-device model to write an answer. [verdict] is an AnswerVerdict name,
 * or ERROR / TIMEOUT. Rejection rates by verdict are the hallucination-guard metric.
 */
@Serializable
@SerialName("generation_outcome")
data class GenerationOutcome(
    val verdict: String,
    val latencyMs: Long,
    val backend: String,
    val sourceCount: Int,
    val routedByModel: Boolean
) : LessonPayload

val LessonPayload.type: LessonType
    get() = when (this) {
        is AlignmentOutcome -> LessonType.ALIGNMENT_OUTCOME
        is AlignmentCorrection -> LessonType.ALIGNMENT_CORRECTION
        is IdentificationRecovery -> LessonType.IDENTIFICATION_RECOVERY
        is KnowledgeGap -> LessonType.KNOWLEDGE_GAP
        is AliasMiss -> LessonType.ALIAS_MISS
        is ModelRouting -> LessonType.MODEL_ROUTING
        is GenerationOutcome -> LessonType.GENERATION_OUTCOME
    }

data class Lesson(
    val id: String,
    val type: LessonType,
    val inscriptionId: String?,
    val packVersion: Int,
    val appVersion: String,
    val payloadSchemaVersion: Int,
    val payloadJson: String,
    val trust: LessonTrust,
    val createdAt: Long,
    val syncedAt: Long?
)
