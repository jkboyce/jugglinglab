//
// AvatarTest.kt
//
// Unit tests for the Avatar hierarchy: addObjectsToPool, polygon structure,
// factory/registry, and DrawObject2D reusability.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.util.JuggleExceptionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AvatarTest {
    @Test
    fun `classic avatar produces head and torso polygons plus arm lines`() {
        val avatar = ClassicAvatar()
        val pat = SiteswapPattern().fromString("3").asJmlPattern()
        val pool = DrawObjectPool()
        avatar.addObjectsToPool(1, pat, 0.0, pool)
        val objs = pool.objects.take(pool.activeCount)

        val polys = objs.filter { it.type == DrawObject2D.Type.POLY }
        val lines = objs.filter { it.type == DrawObject2D.Type.LINE }

        // Head (40 points) and Torso (4 points)
        assertEquals(2, polys.size)
        assertTrue(polys.all { it.isClosed })
        assertEquals(4, lines.size) // 2 upper arm lines + 2 lower arm lines
    }

    @Test
    fun `female avatar produces head ponytail torso and skirt polygons`() {
        val female = FemaleAvatar()
        val pat = SiteswapPattern().fromString("3").asJmlPattern()
        val pool = DrawObjectPool()
        female.addObjectsToPool(1, pat, 0.0, pool)
        val objs = pool.objects.take(pool.activeCount)

        val polys = objs.filter { it.type == DrawObject2D.Type.POLY }
        val lines = objs.filter { it.type == DrawObject2D.Type.LINE }

        // Head, Ponytail (closed) and Torso, Skirt (unclosed at waist)
        assertEquals(4, polys.size)
        assertEquals(2, polys.count { it.isClosed })
        assertEquals(2, polys.count { !it.isClosed })
        assertTrue(lines.isNotEmpty())
    }

    @Test
    fun `factory produces the registered avatars`() {
        assertIs<ClassicAvatar>(Avatar.newAvatar("classic"))
        assertIs<FemaleAvatar>(Avatar.newAvatar("female"))
        assertIs<FemaleAvatar>(Avatar.newAvatar("Female")) // case-insensitive

        for (type in Avatar.builtinAvatars) {
            Avatar.newAvatar(type) // every registered id must construct
        }
    }

    @Test
    fun `factory rejects unknown avatar types`() {
        assertFailsWith<JuggleExceptionUser> { Avatar.newAvatar("banana") }
    }

    @Test
    fun `avatar map assigns a comma list cyclically to jugglers`() {
        val map = Avatar.avatarMap("classic,female", 3)
        assertIs<ClassicAvatar>(map.getValue(1))
        assertIs<FemaleAvatar>(map.getValue(2))
        assertIs<ClassicAvatar>(map.getValue(3)) // wraps around
    }

    @Test
    fun `avatar map is empty for an all-default spec`() {
        assertTrue(Avatar.avatarMap("classic", 3).isEmpty())
        assertTrue(Avatar.avatarMap("classic,classic", 2).isEmpty())
    }

    @Test
    fun `draw object grows coordinate buffers on demand and supports unclosed polygons`() {
        val ob = DrawObject2D()
        val pt1 = JlVector(0.0, 0.0, 0.0)
        val pt2 = JlVector(10.0, 0.0, 0.0)
        val pt3 = JlVector(10.0, 10.0, 0.0)

        ob.set3DCoordinates(DrawObject2D.Type.POLY, 1, listOf(pt1, pt2, pt3), isClosed = false)

        assertEquals(3, ob.numPoints)
        assertFalse(ob.isClosed)
        assertTrue(ob.coords3D.size >= 3)
        assertTrue(ob.coords2D.size >= 3)

        // Monotonic growth test
        ob.set3DCoordinates(DrawObject2D.Type.LINE, 1, listOf(pt1, pt2))
        assertEquals(2, ob.numPoints)
        assertTrue(ob.coords3D.size >= 3)
    }

    @Test
    fun `elbow IK calculates valid position for reachable hand and null for unreachable`() {
        val shoulder = JlVector(0.0, 40.0, 0.0)
        val upperArmLength = 41.0
        val lowerArmLength = 41.0

        val reachableHand = JlVector(0.0, 0.0, 30.0)
        val elbowPos = Avatar.elbow(
            shoulder = shoulder,
            hand = reachableHand,
            upperArmLength = upperArmLength,
            lowerArmLength = lowerArmLength
        )
        assertNotNull(elbowPos)

        val unreachableHand = JlVector(0.0, 0.0, 500.0)
        val outOfReach = Avatar.elbow(
            shoulder = shoulder,
            hand = unreachableHand,
            upperArmLength = upperArmLength,
            lowerArmLength = lowerArmLength
        )
        assertNull(outOfReach)
    }

    @Test
    fun `addSingleArmLines uniformly stretches arm bones and preserves gaps when hand is out of reach`() {
        val pool = DrawObjectPool()
        val shoulder = JlVector(0.0, 40.0, 0.0)
        val hand = JlVector(0.0, 40.0, 200.0) // 200 cm away -> out of reach
        Avatar.addSingleArmLines(
            juggler = 1,
            shoulder = shoulder,
            hand = hand,
            upperArmLength = 40.0,
            lowerArmLength = 40.0,
            upperGapElbow = 0.0,
            upperGapShoulder = 0.0,
            lowerGapWrist = 10.0,
            lowerGapElbow = 0.0,
            pool = pool
        )
        val lines = pool.objects.take(pool.activeCount).filter { it.type == DrawObject2D.Type.LINE }
        assertEquals(2, lines.size) // 1 upper arm line + 1 lower arm line
        // Check wrist gap of 10cm on lower arm line end: line goes to z = 190.0 (200 - 10)
        val lowerLine = lines[1]
        assertEquals(190.0, lowerLine.coords3D[1].z, 0.001)
    }
}
