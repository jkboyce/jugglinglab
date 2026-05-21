//
// MhnBodyTest.kt
//
// Unit tests for MhnBody.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import kotlin.test.Test
import kotlin.test.assertEquals

class MhnBodyTest {
    @Test
    fun testMhnBodyPeriod1() {
        val body = MhnBody("(0)...(90)...(180)...(270)...")
        assertEquals(12, body.getPeriod(1))
    }

    @Test
    fun testMhnBodyPeriod2() {
        val body = MhnBody("<(0)...(90)...(180)...(270)...|(0)...(90)...>")
        assertEquals(12, body.getPeriod(1))
        assertEquals(6, body.getPeriod(2))
    }
}
