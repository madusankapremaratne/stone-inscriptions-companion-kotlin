package org.sellipi.companion.domain.lessons

import org.sellipi.companion.domain.model.InscriptionQuad

/**
 * Accumulates signals during one Overlay/AR session and turns them into lessons on [finish].
 * Not thread-safe; call from the main thread (ViewModel event handlers).
 */
class AlignmentSessionTracker(
    private val mode: AlignmentMode,
    private val clock: () -> Long,
    private val localHour: () -> Int
) {
    private val startedAt = clock()
    private var alignedAt: Long? = null
    private var hits = 0
    private var misses = 0
    private var dragCount = 0
    private var initialQuad: InscriptionQuad? = null
    private var finalQuad: InscriptionQuad? = null
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var endReason = AlignmentEndReason.EXITED
    private var finished = false

    fun onInitialQuad(quad: InscriptionQuad) {
        if (initialQuad == null) initialQuad = quad
        finalQuad = quad
    }

    fun onViewportSize(width: Float, height: Float) {
        viewportWidth = width
        viewportHeight = height
    }

    fun onQuadChanged(quad: InscriptionQuad) {
        finalQuad = quad
    }

    fun onCornerDragFinished() {
        dragCount++
    }

    fun onGlyphTap(hit: Boolean) {
        if (hit) {
            hits++
            markAligned()
        } else {
            misses++
        }
    }

    fun onTrackingAcquired() = markAligned()

    fun onFallbackToOverlay() {
        endReason = AlignmentEndReason.FALLBACK_TO_OVERLAY
    }

    /** Returns the session's lessons exactly once; later calls return an empty list. */
    fun finish(): List<LessonPayload> {
        if (finished) return emptyList()
        finished = true

        val outcome = AlignmentOutcome(
            mode = mode,
            success = alignedAt != null,
            timeToAlignMs = alignedAt?.let { it - startedAt },
            sessionDurationMs = clock() - startedAt,
            glyphTapHits = hits,
            glyphTapMisses = misses,
            endReason = endReason,
            localHour = localHour()
        )

        val start = initialQuad
        val end = finalQuad
        val correction = if (dragCount > 0 && start != null && end != null) {
            val normalise = viewportWidth > 0f && viewportHeight > 0f
            AlignmentCorrection(
                initialQuad = flatten(start, normalise),
                finalQuad = flatten(end, normalise),
                cornerDragCount = dragCount,
                normalised = normalise
            )
        } else {
            null
        }

        return listOfNotNull(outcome, correction)
    }

    private fun markAligned() {
        if (alignedAt == null) alignedAt = clock()
    }

    private fun flatten(quad: InscriptionQuad, normalise: Boolean): List<Float> {
        val sx = if (normalise) viewportWidth else 1f
        val sy = if (normalise) viewportHeight else 1f
        return listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
            .flatMap { listOf(it.x / sx, it.y / sy) }
    }
}
