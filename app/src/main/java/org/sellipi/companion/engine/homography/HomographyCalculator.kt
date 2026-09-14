package org.sellipi.companion.engine.homography

import org.sellipi.companion.domain.model.InscriptionQuad
import org.sellipi.companion.domain.model.QuadPoint

/**
 * Direct Linear Transformation (DLT) Homography Calculator.
 * Maps 2D normalized planar coordinates [0, 1] to a 4-point quadrilateral on the camera viewport,
 * and performs inverse projection for hit-testing individual glyph bounding boxes on touch.
 */
class HomographyCalculator {

    /**
     * Calculates the 3x3 projective transformation matrix H that maps the unit square
     * (0,0), (1,0), (1,1), (0,1) to the four corner QuadPoints (TL, TR, BR, BL).
     */
    fun computeHomographyMatrix(quad: InscriptionQuad): FloatArray {
        val x0 = quad.topLeft.x
        val y0 = quad.topLeft.y
        val x1 = quad.topRight.x
        val y1 = quad.topRight.y
        val x2 = quad.bottomRight.x
        val y2 = quad.bottomRight.y
        val x3 = quad.bottomLeft.x
        val y3 = quad.bottomLeft.y

        val dx1 = x1 - x2
        val dx2 = x3 - x2
        val sx = x0 - x1 + x2 - x3
        val dy1 = y1 - y2
        val dy2 = y3 - y2
        val sy = y0 - y1 + y2 - y3

        if (sx == 0f && sy == 0f) {
            // Affine transform
            return floatArrayOf(
                x1 - x0, x2 - x1, x0,
                y1 - y0, y2 - y1, y0,
                0f, 0f, 1f
            )
        }

        val det = dx1 * dy2 - dx2 * dy1
        val g = if (det != 0f) (sx * dy2 - sy * dx2) / det else 0f
        val h = if (det != 0f) (dx1 * sy - dy1 * sx) / det else 0f

        val a = x1 - x0 + g * x1
        val b = x3 - x0 + h * x3
        val c = x0
        val d = y1 - y0 + g * y1
        val e = y3 - y0 + h * y3
        val f = y0

        return floatArrayOf(
            a, b, c,
            d, e, f,
            g, h, 1f
        )
    }

    /**
     * Warps a normalized point (u, v) in [0, 1] to screen coordinate (x, y) using matrix H.
     */
    fun mapPoint(matrix: FloatArray, u: Float, v: Float): QuadPoint {
        val x = matrix[0] * u + matrix[1] * v + matrix[2]
        val y = matrix[3] * u + matrix[4] * v + matrix[5]
        val w = matrix[6] * u + matrix[7] * v + matrix[8]
        val safeW = if (w != 0f) w else 1f
        return QuadPoint(x / safeW, y / safeW)
    }

    /**
     * Computes the inverse of a 3x3 matrix.
     */
    fun invertMatrix(m: FloatArray): FloatArray? {
        val det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
                  m[1] * (m[3] * m[8] - m[5] * m[6]) +
                  m[2] * (m[3] * m[7] - m[4] * m[6])

        if (det == 0f) return null
        val invDet = 1f / det

        return floatArrayOf(
            (m[4] * m[8] - m[5] * m[7]) * invDet,
            (m[2] * m[7] - m[1] * m[8]) * invDet,
            (m[1] * m[5] - m[2] * m[4]) * invDet,
            (m[5] * m[6] - m[3] * m[8]) * invDet,
            (m[0] * m[8] - m[2] * m[6]) * invDet,
            (m[2] * m[3] - m[0] * m[5]) * invDet,
            (m[3] * m[7] - m[4] * m[6]) * invDet,
            (m[1] * m[6] - m[0] * m[7]) * invDet,
            (m[0] * m[4] - m[1] * m[3]) * invDet
        )
    }

    /**
     * Checks if a screen touch coordinate (touchX, touchY) hits a normalized bounding box
     * [bboxX, bboxY, bboxW, bboxH] using the inverse homography matrix.
     */
    fun isTouchInsideNormalizedBbox(
        invMatrix: FloatArray,
        touchX: Float,
        touchY: Float,
        bboxX: Float,
        bboxY: Float,
        bboxW: Float,
        bboxH: Float
    ): Boolean {
        val normalized = mapPoint(invMatrix, touchX, touchY)
        return normalized.x in bboxX..(bboxX + bboxW) &&
               normalized.y in bboxY..(bboxY + bboxH)
    }
}
