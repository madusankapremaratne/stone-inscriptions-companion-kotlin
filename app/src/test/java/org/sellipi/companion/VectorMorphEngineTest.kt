package org.sellipi.companion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sellipi.companion.engine.morph.MorphNode
import org.sellipi.companion.engine.morph.VectorMorphEngine

class VectorMorphEngineTest {

    private val morphEngine = VectorMorphEngine()

    @Test
    fun testParsePathToNodes() {
        val path = "M 10 20 L 30 40 C 50 60 70 80 90 100 Z"
        val nodes = morphEngine.parsePathToNodes(path, "අ")
        assertNotNull(nodes)
        assertTrue(nodes.isNotEmpty())
        assertEquals(10f, nodes.first().x, 0.01f)
        assertEquals(20f, nodes.first().y, 0.01f)
    }

    @Test
    fun testResampleAndInterpolateNodes() {
        val nodesA = listOf(MorphNode(0f, 0f), MorphNode(10f, 10f))
        val nodesB = listOf(MorphNode(10f, 0f), MorphNode(20f, 10f))

        val interpolated = morphEngine.interpolateNodes(nodesA, nodesB, 0.5f)
        assertNotNull(interpolated)
        assertEquals(32, interpolated.size)
        // Midpoint of start nodes (0,0) and (10,0) at t=0.5 should be (5,0)
        assertEquals(5f, interpolated.first().x, 0.01f)
        assertEquals(0f, interpolated.first().y, 0.01f)
    }
}
