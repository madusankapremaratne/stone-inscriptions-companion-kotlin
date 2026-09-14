package org.sellipi.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.domain.model.InscriptionQuad
import org.sellipi.companion.domain.model.QuadPoint
import org.sellipi.companion.engine.homography.HomographyCalculator

class HomographyCalculatorTest {

    private val calculator = HomographyCalculator()

    @Test
    fun testIdentityHomography() {
        val quad = InscriptionQuad(
            topLeft = QuadPoint(0f, 0f),
            topRight = QuadPoint(100f, 0f),
            bottomRight = QuadPoint(100f, 100f),
            bottomLeft = QuadPoint(0f, 100f)
        )

        val matrix = calculator.computeHomographyMatrix(quad)
        assertNotNull(matrix)
        assertEquals(9, matrix.size)

        val mappedCenter = calculator.mapPoint(matrix, 0.5f, 0.5f)
        assertEquals(50f, mappedCenter.x, 0.01f)
        assertEquals(50f, mappedCenter.y, 0.01f)
    }

    @Test
    fun testInversePointProjectionAndTouchHit() {
        val quad = InscriptionQuad(
            topLeft = QuadPoint(100f, 100f),
            topRight = QuadPoint(500f, 100f),
            bottomRight = QuadPoint(500f, 500f),
            bottomLeft = QuadPoint(100f, 500f)
        )

        val matrix = calculator.computeHomographyMatrix(quad)
        val invMatrix = calculator.invertMatrix(matrix)
        assertNotNull(invMatrix)

        // Glyph bbox in normalized unit square: x=0.2, y=0.2, w=0.2, h=0.2
        // In screen coordinates, this corresponds to x in [180, 260], y in [180, 260]
        val isHitInside = calculator.isTouchInsideNormalizedBbox(
            invMatrix = invMatrix!!,
            touchX = 200f,
            touchY = 200f,
            bboxX = 0.2f,
            bboxY = 0.2f,
            bboxW = 0.2f,
            bboxH = 0.2f
        )
        assertTrue(isHitInside)
    }
}
