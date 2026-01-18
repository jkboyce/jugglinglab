// MhnHandsTest.kt
//
// Unit tests for MhnHands.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import kotlin.test.Test
import kotlin.test.assertEquals

class MhnHandsTest {
    @Test
    fun `hands parsing 1`() {
        val config = "(30)-(22,20)c(15)."
        val output = MhnHands(config).toString()
        assertEquals(output, "(30)-(22,20)(15).")
    }

    @Test
    fun `hands parsing 2`() {
        val config = "(-30)-(22,0,-5)c(15)-."
        val output = MhnHands(config).toString()
        assertEquals(output, "(-30)-(22,0,-5)c(15)-.")
    }

    @Test
    fun `hands parsing 3`() {
        val config = "<t(10)c(32.5)(0,45,-25).|(-30)(2.5).(30)(-2.5).(-30)(0).>"
        val output = MhnHands(config).toString()
        assertEquals(output, "<(10)c(32.5)(0,45,-25).|(-30)(2.5).(30)(-2.5).(-30)(0).>")
    }
}