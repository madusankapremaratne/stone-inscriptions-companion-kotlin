package org.sellipi.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.domain.lessons.AlignmentCorrection
import org.sellipi.companion.domain.lessons.AlignmentEndReason
import org.sellipi.companion.domain.lessons.AlignmentMode
import org.sellipi.companion.domain.lessons.AlignmentOutcome
import org.sellipi.companion.domain.lessons.AlignmentSessionTracker
import org.sellipi.companion.domain.model.InscriptionQuad
import org.sellipi.companion.domain.model.QuadPoint

class AlignmentSessionTrackerTest {

    private var now = 1_000L
    private fun tracker(mode: AlignmentMode = AlignmentMode.OVERLAY) =
        AlignmentSessionTracker(mode, clock = { now }, localHour = { 14 })

    private val quad = InscriptionQuad(
        topLeft = QuadPoint(100f, 250f),
        topRight = QuadPoint(900f, 250f),
        bottomRight = QuadPoint(900f, 800f),
        bottomLeft = QuadPoint(100f, 800f)
    )

    @Test
    fun sessionWithoutGlyphHitIsUnconfirmed() {
        val t = tracker()
        t.onInitialQuad(quad)
        t.onGlyphTap(hit = false)
        now += 5_000

        val lessons = t.finish()

        assertEquals(1, lessons.size)
        val outcome = lessons.single() as AlignmentOutcome
        assertFalse(outcome.success)
        assertNull(outcome.timeToAlignMs)
        assertEquals(1, outcome.glyphTapMisses)
        assertEquals(5_000L, outcome.sessionDurationMs)
        assertEquals(14, outcome.localHour)
    }

    @Test
    fun timeToAlignIsMeasuredToFirstHit() {
        val t = tracker()
        now += 2_000
        t.onGlyphTap(hit = true)
        now += 3_000
        t.onGlyphTap(hit = true)

        val outcome = t.finish().single() as AlignmentOutcome

        assertTrue(outcome.success)
        assertEquals(2_000L, outcome.timeToAlignMs)
        assertEquals(2, outcome.glyphTapHits)
    }

    @Test
    fun correctionIsNormalisedToViewport() {
        val t = tracker()
        t.onViewportSize(1000f, 1000f)
        t.onInitialQuad(quad)
        t.onQuadChanged(quad.copy(topLeft = QuadPoint(150f, 300f)))
        t.onCornerDragFinished()

        val correction = t.finish().filterIsInstance<AlignmentCorrection>().single()

        assertTrue(correction.normalised)
        assertEquals(1, correction.cornerDragCount)
        assertEquals(listOf(0.1f, 0.25f, 0.9f, 0.25f, 0.9f, 0.8f, 0.1f, 0.8f), correction.initialQuad)
        assertEquals(0.15f, correction.finalQuad[0], 1e-6f)
        assertEquals(0.3f, correction.finalQuad[1], 1e-6f)
    }

    @Test
    fun correctionFallsBackToPixelsWithoutViewport() {
        val t = tracker()
        t.onInitialQuad(quad)
        t.onCornerDragFinished()

        val correction = t.finish().filterIsInstance<AlignmentCorrection>().single()

        assertFalse(correction.normalised)
        assertEquals(100f, correction.initialQuad[0], 0f)
    }

    @Test
    fun noCorrectionWithoutDrag() {
        val t = tracker()
        t.onInitialQuad(quad)
        assertTrue(t.finish().none { it is AlignmentCorrection })
    }

    @Test
    fun arFallbackIsRecorded() {
        val t = tracker(AlignmentMode.AR)
        t.onFallbackToOverlay()

        val outcome = t.finish().single() as AlignmentOutcome

        assertEquals(AlignmentMode.AR, outcome.mode)
        assertEquals(AlignmentEndReason.FALLBACK_TO_OVERLAY, outcome.endReason)
        assertFalse(outcome.success)
    }

    @Test
    fun finishIsIdempotent() {
        val t = tracker()
        assertEquals(1, t.finish().size)
        assertTrue(t.finish().isEmpty())
    }
}
