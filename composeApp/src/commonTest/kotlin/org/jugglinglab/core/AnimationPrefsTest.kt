//
// AnimationPrefsTest.kt
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.core

import org.jugglinglab.util.JuggleExceptionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnimationPrefsTest {
    @Test
    fun testSilentlyIgnoresLegacyDimensionsAndFps() {
        val prefs = AnimationPrefs.fromString("width=600;height=400;fps=30;slowdown=2.0")
        assertEquals(2.0, prefs.slowdown)
    }

    @Test
    fun testDoesNotParseIgnoredValues() {
        val prefs = AnimationPrefs.fromString("width=notanumber;height=invalid;fps=abc")
        assertEquals(AnimationPrefs.SLOWDOWN_DEF, prefs.slowdown)
    }

    @Test
    fun testStillErrorsOnUnknownParameters() {
        assertFailsWith<JuggleExceptionUser> {
            AnimationPrefs.fromString("unknownparam=123")
        }
    }
}
