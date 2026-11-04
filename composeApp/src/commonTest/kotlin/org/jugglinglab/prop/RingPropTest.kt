//
// RingPropTest.kt
//
// Unit tests for RingProp.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.prop

import org.jugglinglab.util.toRadians
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RingPropTest {
    @Test
    fun testRingPropRenderingAboveAndBelow() {
        val ring = RingProp()
        val zoom = 2.0

        val camAbove = doubleArrayOf(20.0.toRadians(), 60.0.toRadians())
        val camLevel = doubleArrayOf(20.0.toRadians(), 90.0.toRadians())
        val camBelow = doubleArrayOf(20.0.toRadians(), 120.0.toRadians())

        val imageAbove = ring.getProp2DImage(zoom, camAbove)
        assertNotNull(imageAbove)
        val sizeAbove = ring.getProp2DSize(zoom, camAbove)
        val centerAbove = ring.getProp2DCenter(zoom, camAbove)
        val gripAbove = ring.getProp2DGrip(zoom, camAbove)

        val imageBelow = ring.getProp2DImage(zoom, camBelow)
        assertNotNull(imageBelow)
        val sizeBelow = ring.getProp2DSize(zoom, camBelow)
        val centerBelow = ring.getProp2DCenter(zoom, camBelow)
        val gripBelow = ring.getProp2DGrip(zoom, camBelow)

        val imageLevel = ring.getProp2DImage(zoom, camLevel)
        assertNotNull(imageLevel)
        val centerLevel = ring.getProp2DCenter(zoom, camLevel)
        val gripLevel = ring.getProp2DGrip(zoom, camLevel)

        // Bounding boxes should be identical for symmetric elevation angles (60 deg and 120 deg)
        assertEquals(sizeAbove.width, sizeBelow.width)
        assertEquals(sizeAbove.height, sizeBelow.height)

        // When viewing the juggler from above (camangle[1] = 60 deg), the near edge (held by hand)
        // appears higher on screen (smaller y) than the center.
        assertTrue(gripAbove.height < centerAbove.height)

        // When viewing level (camangle[1] = 90 deg), near edge is level with the center.
        assertEquals(centerLevel.height, gripLevel.height)

        // When viewing from below (camangle[1] = 120 deg), the near edge appears lower on screen
        // (larger y) than the center.
        assertTrue(gripBelow.height > centerBelow.height)

        // Symmetric offsets from center
        val offsetAbove = centerAbove.height - gripAbove.height
        val offsetBelow = gripBelow.height - centerBelow.height
        assertEquals(offsetAbove, offsetBelow)
    }
}
