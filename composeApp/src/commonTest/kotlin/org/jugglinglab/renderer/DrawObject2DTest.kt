//
// DrawObject2DTest.kt
//
// Unit tests for DrawObject2D depth covering logic and coordinate management.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DrawObject2DTest {
    @Test
    fun `prop covering line returns correct depth relationship`() {
        val prop = DrawObject2D()
        prop.set3DCoordinates(DrawObject2D.Type.PROP, 1, listOf(JlVector(50.0, 50.0, 10.0)))
        prop.coords2D[0].set(JlVector(50.0, 50.0, 10.0)) // z = 10 (closer)
        prop.bbLeft = 40f
        prop.bbTop = 40f
        prop.bbRight = 60f
        prop.bbBottom = 60f

        val line = DrawObject2D()
        line.set3DCoordinates(
            DrawObject2D.Type.LINE,
            0,
            listOf(JlVector(0.0, 50.0, 50.0), JlVector(100.0, 50.0, 50.0))
        )
        line.coords2D[0].set(JlVector(0.0, 50.0, 50.0)) // z = 50 (further)
        line.coords2D[1].set(JlVector(100.0, 50.0, 50.0))
        line.computeBounds()

        assertEquals(1, prop.compareCovering(line))
        assertEquals(-1, line.compareCovering(prop))
        assertTrue(prop.isCovering(line))
        assertFalse(line.isCovering(prop))

        // Swap depths so line is closer (z = 5.0) than prop (z = 50.0)
        line.coords2D[0].set(JlVector(0.0, 50.0, 5.0))
        line.coords2D[1].set(JlVector(100.0, 50.0, 5.0))
        prop.coords2D[0].set(JlVector(50.0, 50.0, 50.0))

        assertEquals(-1, prop.compareCovering(line))
        assertEquals(1, line.compareCovering(prop))
        assertFalse(prop.isCovering(line))
        assertTrue(line.isCovering(prop))
    }

    @Test
    fun `poly covering poly evaluates depth when vertex is inside other polygon`() {
        // Poly A spans x: [0..100], y: [0..100], z flat at 10.0 (closer)
        val polyA = DrawObject2D()
        val ptsA = listOf(
            JlVector(0.0, 0.0, 10.0),
            JlVector(100.0, 0.0, 10.0),
            JlVector(100.0, 100.0, 10.0),
            JlVector(0.0, 100.0, 10.0)
        )
        polyA.set3DCoordinates(DrawObject2D.Type.POLY, 1, ptsA)
        for (i in 0..3) polyA.coords2D[i].set(ptsA[i])
        polyA.computeBounds()

        // Poly B has vertex (50, 50) inside Poly A, z flat at 20.0 (further)
        val polyB = DrawObject2D()
        val ptsB = listOf(
            JlVector(50.0, 50.0, 20.0),
            JlVector(150.0, 50.0, 20.0),
            JlVector(150.0, 150.0, 20.0),
            JlVector(50.0, 150.0, 20.0)
        )
        polyB.set3DCoordinates(DrawObject2D.Type.POLY, 2, ptsB)
        for (i in 0..3) polyB.coords2D[i].set(ptsB[i])
        polyB.computeBounds()

        assertEquals(1, polyA.compareCovering(polyB))
        assertEquals(-1, polyB.compareCovering(polyA))

        // Non-overlapping polygons (no vertex inside each other) return 0
        val polyC = DrawObject2D()
        val ptsC = listOf(
            JlVector(200.0, 200.0, 20.0),
            JlVector(250.0, 200.0, 20.0),
            JlVector(250.0, 250.0, 20.0),
            JlVector(200.0, 250.0, 20.0)
        )
        polyC.set3DCoordinates(DrawObject2D.Type.POLY, 3, ptsC)
        for (i in 0..3) polyC.coords2D[i].set(ptsC[i])
        polyC.computeBounds()

        assertEquals(0, polyA.compareCovering(polyC))
        assertEquals(0, polyC.compareCovering(polyA))
    }

    @Test
    fun `matrix transform correctly multiplies matrices in place`() {
        val m1 = JlMatrix.shiftMatrix(10.0, 20.0, 30.0)
        val m2 = JlMatrix.scaleMatrix(2.0, 3.0, 4.0)

        val mCombined = m1.clone
        mCombined.transform(m2)

        val v = JlVector(1.0, 1.0, 1.0)
        val result = v.transform(mCombined)

        // Shifting followed by scaling: x = 2*(1 + 10) = 22, y = 3*(1 + 20) = 63, z = 4*(1 + 30) = 124
        assertEquals(22.0, result.x, 0.0001)
        assertEquals(63.0, result.y, 0.0001)
        assertEquals(124.0, result.z, 0.0001)
    }
}
