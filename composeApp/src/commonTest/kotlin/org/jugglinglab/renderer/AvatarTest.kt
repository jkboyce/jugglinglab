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
    fun `male avatar uses exactly the core skeleton`() {
        val male = MaleAvatar()
        assertEquals(Avatar.CORE_POINT_COUNT, male.pointCount)
        assertEquals(Avatar.TORSO_AND_HEAD_POINTS, male.boundsPoints)
    }

    @Test
    fun `hands and elbows stay out of the body bounds`() {
        // Arms are separate line objects; including them in the body bbox
        // would change the painter's-algorithm cheap-reject behavior.
        val avatar = MaleAvatar()
        for (p in listOf(
            Avatar.LEFT_HAND, Avatar.RIGHT_HAND, Avatar.LEFT_ELBOW, Avatar.RIGHT_ELBOW
        )) {
            assertFalse(p in avatar.boundsPoints)
        }
    }

    @Test
    fun `factory produces the registered avatars`() {
        assertIs<MaleAvatar>(Avatar.newAvatar("male"))
        assertIs<MaleAvatar>(Avatar.newAvatar("Male")) // case-insensitive

        for (type in Avatar.builtinAvatars) {
            Avatar.newAvatar(type) // every registered id must construct
        }
    }

    @Test
    fun `factory rejects unknown avatar types`() {
        assertFailsWith<JuggleExceptionUser> { Avatar.newAvatar("banana") }
    }

    @Test
    fun `draw object grows its point buffer on demand`() {
        val ob = DrawObject2D()
        ob.ensureCapacity(18)
        assertTrue(ob.coord.size >= 18)
        // Growing is monotonic; asking for less never shrinks
        ob.ensureCapacity(2)
        assertTrue(ob.coord.size >= 18)
    }
}
