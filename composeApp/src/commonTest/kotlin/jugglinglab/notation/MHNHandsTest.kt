// MHNHandsTest.kt
//
// Unit tests for MHNHands.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import kotlin.test.Test
import kotlin.test.assertEquals

class MHNHandsTest {
    @Test
    fun `hands parsing 1`() {
        val config = "(30)-(22,20)c(15)."
        val output = MHNHands(config).toString()
        assertEquals(output, "(30)-(22,20)(15).")
    }

    @Test
    fun `hands parsing 2`() {
        val config = "(-30)-(22,0,-5)c(15)-."
        val output = MHNHands(config).toString()
        assertEquals(output, "(-30)-(22,0,-5)c(15)-.")
    }
}