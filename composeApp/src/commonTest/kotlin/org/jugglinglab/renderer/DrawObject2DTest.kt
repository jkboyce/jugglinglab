//
// DrawObject2DTest.kt
//
// Unit tests for DrawObject2D depth covering logic and coordinate management.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import kotlin.test.Test
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

        assertTrue(prop.isCovering(line))
        assertFalse(line.isCovering(prop))

        // Swap depths so line is closer (z = 5.0) than prop (z = 50.0)
        line.coords2D[0].set(JlVector(0.0, 50.0, 5.0))
        line.coords2D[1].set(JlVector(100.0, 50.0, 5.0))
        prop.coords2D[0].set(JlVector(50.0, 50.0, 50.0))

        assertFalse(prop.isCovering(line))
        assertTrue(line.isCovering(prop))
    }
}
