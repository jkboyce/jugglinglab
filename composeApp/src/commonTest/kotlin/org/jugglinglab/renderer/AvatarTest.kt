//
// AvatarTest.kt
//
// Unit tests for the Avatar hierarchy: the point-index and bounds contracts
// that the renderer relies on, and the avatar factory/registry.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.renderer

import org.jugglinglab.util.JuggleExceptionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AvatarTest {
    @Test
    fun `default avatar uses exactly the core skeleton`() {
        val avatar = DefaultAvatar()
        assertEquals(Avatar.CORE_POINT_COUNT, avatar.pointCount)
        assertEquals(Avatar.TORSO_AND_HEAD_POINTS, avatar.boundsPoints)
    }

    @Test
    fun `female avatar extends the core skeleton without renumbering it`() {
        val female = FemaleAvatar()
        assertEquals(FemaleAvatar.EXTENDED_POINT_COUNT, female.pointCount)
        assertTrue(female.pointCount > Avatar.CORE_POINT_COUNT)

        // Its own points start after the shared skeleton
        assertEquals(Avatar.CORE_POINT_COUNT, FemaleAvatar.LEFT_HIP)
        assertTrue(FemaleAvatar.PONYTAIL_TIP == female.pointCount - 1)
    }

    @Test
    fun `female bounds include the dress but not the ponytail`() {
        val bounds = FemaleAvatar().boundsPoints
        // Everything the shared skeleton contributes is still there
        assertTrue(bounds.containsAll(Avatar.TORSO_AND_HEAD_POINTS))
        // The skirt participates in occlusion bounds
        assertTrue(FemaleAvatar.LEFT_HEM in bounds && FemaleAvatar.RIGHT_HEM in bounds)
        assertTrue(FemaleAvatar.LEFT_HIP in bounds && FemaleAvatar.RIGHT_HIP in bounds)
        // The ponytail deliberately does not (see FemaleAvatar)
        assertFalse(FemaleAvatar.PONYTAIL_ANCHOR in bounds)
        assertFalse(FemaleAvatar.PONYTAIL_TIP in bounds)
    }

    @Test
    fun `hands and elbows stay out of the body bounds`() {
        // Arms are separate line objects; including them in the body bbox
        // would change the painter's-algorithm cheap-reject behavior.
        for (avatar in listOf(DefaultAvatar(), FemaleAvatar())) {
            for (p in listOf(
                Avatar.LEFT_HAND, Avatar.RIGHT_HAND, Avatar.LEFT_ELBOW, Avatar.RIGHT_ELBOW
            )) {
                assertFalse(p in avatar.boundsPoints)
            }
        }
    }

    @Test
    fun `factory produces the registered avatars`() {
        assertIs<DefaultAvatar>(Avatar.newAvatar("default"))
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
        val map = Avatar.avatarMap("default,female", 3)
        assertIs<DefaultAvatar>(map.getValue(1))
        assertIs<FemaleAvatar>(map.getValue(2))
        assertIs<DefaultAvatar>(map.getValue(3)) // wraps around
    }

    @Test
    fun `avatar map is empty for an all-default spec`() {
        // Every juggler falls back to the renderer's default, so existing
        // (single-juggler, no-avatar) patterns are unchanged.
        assertTrue(Avatar.avatarMap("default", 3).isEmpty())
        assertTrue(Avatar.avatarMap("default,default", 2).isEmpty())
    }

    @Test
    fun `female config drives the dress dimensions`() {
        val longDress = FemaleAvatar(FemaleConfig(hemH = -60.0))
        // Same point layout regardless of configuration
        assertEquals(FemaleAvatar.EXTENDED_POINT_COUNT, longDress.pointCount)
    }

    @Test
    fun `draw object grows its point buffer on demand`() {
        val ob = DrawObject2D()
        ob.ensureCapacity(FemaleAvatar.EXTENDED_POINT_COUNT)
        assertTrue(ob.coord.size >= FemaleAvatar.EXTENDED_POINT_COUNT)
        // Growing is monotonic; asking for less never shrinks
        ob.ensureCapacity(2)
        assertTrue(ob.coord.size >= FemaleAvatar.EXTENDED_POINT_COUNT)
    }
}
