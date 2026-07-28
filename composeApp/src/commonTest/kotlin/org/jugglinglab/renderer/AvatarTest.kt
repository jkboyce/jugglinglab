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

        val polys = objs.filter { it.type == DrawObject2D.TYPE_POLY }
        val lines = objs.filter { it.type == DrawObject2D.TYPE_LINE }

        // Head (40 points) and Torso (4 points)
        assertEquals(2, polys.size)
        assertTrue(polys.all { it.isClosed })
        assertTrue(lines.isNotEmpty())
    }

    @Test
    fun `female avatar produces head ponytail torso and skirt polygons`() {
        val female = FemaleAvatar()
        val pat = SiteswapPattern().fromString("3").asJmlPattern()
        val pool = DrawObjectPool()
        female.addObjectsToPool(1, pat, 0.0, pool)
        val objs = pool.objects.take(pool.activeCount)

        val polys = objs.filter { it.type == DrawObject2D.TYPE_POLY }
        val lines = objs.filter { it.type == DrawObject2D.TYPE_LINE }

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

        ob.set3DCoordinates(DrawObject2D.TYPE_POLY, 1, listOf(pt1, pt2, pt3), isClosed = false)

        assertEquals(3, ob.numPoints)
        assertFalse(ob.isClosed)
        assertTrue(ob.coords3D.size >= 3)
        assertTrue(ob.coords2D.size >= 3)

        // Monotonic growth test
        ob.set3DCoordinates(DrawObject2D.TYPE_LINE, 1, listOf(pt1, pt2))
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
}
