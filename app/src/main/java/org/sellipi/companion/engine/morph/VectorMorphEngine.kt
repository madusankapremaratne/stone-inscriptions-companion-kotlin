package org.sellipi.companion.engine.morph

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import kotlin.math.max

data class MorphNode(val x: Float, val y: Float)

/**
 * 2D Vector Path Morphing Engine.
 * Normalizes vector path nodes between historical periods and interpolates smoothly
 * as the user scrubs through the historical timeline.
 */
class VectorMorphEngine {

    /**
     * Parses standard SVG path commands or fallback coordinate lists into a sequence of MorphNodes.
     */
    fun parsePathToNodes(pathData: String?, fallbackChar: String): List<MorphNode> {
        if (pathData.isNullOrBlank()) {
            return generateProceduralLetterNodes(fallbackChar)
        }

        val nodes = mutableListOf<MorphNode>()
        val tokens = pathData.trim().split(Regex("[ ,]+")).filter { it.isNotBlank() }
        var i = 0
        var currentX = 0f
        var currentY = 0f

        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.equals("M", ignoreCase = true) || token.equals("L", ignoreCase = true) -> {
                    if (i + 2 < tokens.size) {
                        currentX = tokens[i + 1].toFloatOrNull() ?: currentX
                        currentY = tokens[i + 2].toFloatOrNull() ?: currentY
                        nodes.add(MorphNode(currentX, currentY))
                        i += 3
                    } else i++
                }
                token.equals("C", ignoreCase = true) -> {
                    if (i + 6 < tokens.size) {
                        // Intermediate control points
                        val cp1x = tokens[i + 1].toFloatOrNull() ?: currentX
                        val cp1y = tokens[i + 2].toFloatOrNull() ?: currentY
                        val cp2x = tokens[i + 3].toFloatOrNull() ?: currentX
                        val cp2y = tokens[i + 4].toFloatOrNull() ?: currentY
                        currentX = tokens[i + 5].toFloatOrNull() ?: currentX
                        currentY = tokens[i + 6].toFloatOrNull() ?: currentY
                        nodes.add(MorphNode(cp1x, cp1y))
                        nodes.add(MorphNode(cp2x, cp2y))
                        nodes.add(MorphNode(currentX, currentY))
                        i += 7
                    } else i++
                }
                token.equals("Z", ignoreCase = true) -> {
                    if (nodes.isNotEmpty()) {
                        nodes.add(nodes.first())
                    }
                    i++
                }
                else -> {
                    val x = token.toFloatOrNull()
                    val y = if (i + 1 < tokens.size) tokens[i + 1].toFloatOrNull() else null
                    if (x != null && y != null) {
                        nodes.add(MorphNode(x, y))
                        i += 2
                    } else {
                        i++
                    }
                }
            }
        }

        return if (nodes.isNotEmpty()) nodes else generateProceduralLetterNodes(fallbackChar)
    }

    /**
     * Resamples a list of nodes to a fixed target count for 1-to-1 linear interpolation.
     */
    fun resampleNodes(nodes: List<MorphNode>, targetCount: Int): List<MorphNode> {
        if (nodes.isEmpty()) return List(targetCount) { MorphNode(0.5f, 0.5f) }
        if (nodes.size == targetCount) return nodes

        val result = mutableListOf<MorphNode>()
        val step = (nodes.size - 1).toFloat() / (targetCount - 1).coerceAtLeast(1)

        for (i in 0 until targetCount) {
            val exactIndex = i * step
            val lowIndex = exactIndex.toInt().coerceIn(0, nodes.size - 1)
            val highIndex = (lowIndex + 1).coerceIn(0, nodes.size - 1)
            val fraction = exactIndex - lowIndex

            val p1 = nodes[lowIndex]
            val p2 = nodes[highIndex]

            val interpolatedX = p1.x + fraction * (p2.x - p1.x)
            val interpolatedY = p1.y + fraction * (p2.y - p1.y)
            result.add(MorphNode(interpolatedX, interpolatedY))
        }

        return result
    }

    /**
     * Interpolates between two node lists at progress t in [0, 1].
     */
    fun interpolateNodes(nodesA: List<MorphNode>, nodesB: List<MorphNode>, fraction: Float): List<MorphNode> {
        val count = max(nodesA.size, nodesB.size).coerceAtLeast(32)
        val resampledA = resampleNodes(nodesA, count)
        val resampledB = resampleNodes(nodesB, count)

        val t = fraction.coerceIn(0f, 1f)
        return resampledA.zip(resampledB) { pA, pB ->
            MorphNode(
                x = pA.x + t * (pB.x - pA.x),
                y = pA.y + t * (pB.y - pA.y)
            )
        }
    }

    /**
     * Builds an Android Compose Path from a normalized node list within a given viewport [width, height].
     */
    fun buildComposePath(nodes: List<MorphNode>, width: Float, height: Float, padding: Float = 20f): Path {
        val path = Path()
        if (nodes.isEmpty()) return path

        val drawWidth = width - (padding * 2)
        val drawHeight = height - (padding * 2)

        val start = nodes.first()
        path.moveTo(padding + start.x * drawWidth, padding + start.y * drawHeight)

        for (i in 1 until nodes.size) {
            val node = nodes[i]
            path.lineTo(padding + node.x * drawWidth, padding + node.y * drawHeight)
        }

        return path
    }

    /**
     * Fallback procedural Brahmi letter skeleton if SVG is missing.
     */
    private fun generateProceduralLetterNodes(codepoint: String): List<MorphNode> {
        return when (codepoint) {
            "අ" -> listOf(
                MorphNode(0.2f, 0.2f), MorphNode(0.5f, 0.2f), MorphNode(0.5f, 0.8f),
                MorphNode(0.2f, 0.5f), MorphNode(0.8f, 0.5f)
            )
            "ක" -> listOf(
                // Cross '+' shape of Brahmi Ka
                MorphNode(0.5f, 0.1f), MorphNode(0.5f, 0.9f),
                MorphNode(0.5f, 0.5f), MorphNode(0.1f, 0.5f), MorphNode(0.9f, 0.5f)
            )
            "ග" -> listOf(
                // Inverted 'V' shape of Brahmi Ga
                MorphNode(0.2f, 0.9f), MorphNode(0.5f, 0.1f), MorphNode(0.8f, 0.9f)
            )
            "ත" -> listOf(
                // Semicircle with tail of Brahmi Ta
                MorphNode(0.2f, 0.3f), MorphNode(0.5f, 0.1f), MorphNode(0.8f, 0.3f),
                MorphNode(0.5f, 0.6f), MorphNode(0.5f, 0.9f)
            )
            "ද" -> listOf(
                // 'D' arc of Brahmi Da
                MorphNode(0.3f, 0.1f), MorphNode(0.3f, 0.9f),
                MorphNode(0.7f, 0.7f), MorphNode(0.7f, 0.3f), MorphNode(0.3f, 0.1f)
            )
            "ම" -> listOf(
                // Circle atop semicircle of Brahmi Ma
                MorphNode(0.5f, 0.1f), MorphNode(0.7f, 0.3f), MorphNode(0.5f, 0.5f),
                MorphNode(0.3f, 0.3f), MorphNode(0.5f, 0.1f), MorphNode(0.5f, 0.9f)
            )
            "ර" -> listOf(
                // Vertical line / squiggle of Brahmi Ra
                MorphNode(0.5f, 0.1f), MorphNode(0.5f, 0.9f)
            )
            else -> listOf(
                MorphNode(0.2f, 0.2f), MorphNode(0.8f, 0.2f),
                MorphNode(0.8f, 0.8f), MorphNode(0.2f, 0.8f), MorphNode(0.2f, 0.2f)
            )
        }
    }
}
