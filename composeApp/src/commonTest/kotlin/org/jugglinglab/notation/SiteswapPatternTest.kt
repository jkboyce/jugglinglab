//
// SiteswapPatternTest.kt
//
// Unit tests for SiteswapPattern.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlGetStringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SiteswapPatternTest {
    @Test
    fun `pattern parsing 1`() {
        assertPatternLayout("868671", jugglers = 1, paths = 6)
    }

    @Test
    fun `pattern parsing thrown 2`() {
        assertPatternLayout("(4,3x)!(2,0)!(3x,0)!", jugglers = 1, paths = 4)
    }

    @Test
    fun `pattern parsing short beats`() {
        assertPatternLayout("(0,6x)!(0,0)!(6x,0)!(0,0)!", jugglers = 1, paths = 3)
    }

    @Test
    fun `pattern parsing mixed sync async`() {
        assertPatternLayout("4x1(4x,3x)*", jugglers = 1, paths = 3)
    }

    @Test
    fun `pattern parsing squeeze pattern`() {
        assertPatternLayout("([42],4x)*", jugglers = 1, paths = 5)
    }

    @Test
    fun `pattern parsing multiplex zero regressions`() {
        val cases = listOf(
            "([20],[20])([20],[20])" to 2,
            "([40],[40])([40],[40])([40],[40])" to 4,
            "[60][60][60][60]" to 6,
        )

        for ((patternString, expectedPaths) in cases) {
            assertPatternLayout(patternString, jugglers = 1, paths = expectedPaths)
        }
    }

    @Test
    fun `pattern parsing short multiplex zero patterns`() {
        val cases = listOf(
            "([20],[20])" to 2,
            "([40],[40])" to 4,
            "([40],[40])([40],[40])" to 4,
            "[60]" to 6,
            "[60][60]" to 6,
            "[60][60][60]" to 6,
        )

        for ((patternString, expectedPaths) in cases) {
            assertPatternLayout(patternString, jugglers = 1, paths = expectedPaths)
        }
    }

    @Test
    fun `pattern parsing multiplex zero placeholders with holds`() {
        assertPatternLayout("24[504]", jugglers = 1, paths = 5)
    }

    @Test
    fun `pattern parsing sync async transition`() {
        assertPatternLayout("(645^2)65x6x1x((6x,4)*^2)(7,5x)(4,1x)!", jugglers = 1, paths = 5)
    }

    @Test
    fun `pattern parsing passing 1`() {
        assertPatternLayout("<([2xp/2x],[2xp/2])|(2,[2/2xp])><(2,[2p/2])|([2/2p],[2/2p])>", jugglers = 2, paths = 7)
    }

    @Test
    fun `pattern parsing large throws`() {
        assertPatternLayout("{49}1", jugglers = 1, paths = 25)
    }

    @Test
    fun `pattern parsing modifiers 1`() {
        assertPatternLayout("3BB", jugglers = 1, paths = 3)
    }

    @Test
    fun `pattern parsing modifiers 2`() {
        assertPatternLayout("R3R3xL3L3x", jugglers = 1, paths = 3)
    }

    @Test
    fun `pattern parsing modifiers 3`() {
        assertPatternLayout("<R|L><4xp|3><3|4xp>", jugglers = 2, paths = 7)
    }

    @Test
    fun `pattern parsing modifiers 4`() {
        assertPatternLayout("(4,5x)(4,1x)!R5x41x", jugglers = 1, paths = 4)
    }

    @Test
    fun `pattern parsing 0 pattern 1`() {
        // corner case
        assertPatternLayout("0", jugglers = 1, paths = 0)
    }

    @Test
    fun `pattern parsing 0 pattern 2`() {
        assertPatternLayout("<[00]|0>", jugglers = 2, paths = 0)
    }

    @Test
    fun `pattern parsing spaces 1`() {
        assertPatternLayout("[53] 22", jugglers = 1, paths = 4)
    }

    @Test
    fun `pattern parsing spaces 2`() {
        assertPatternLayout("[5 3  ] 2 2 ", jugglers = 1, paths = 4)
    }

    @Test
    fun `pattern parsing spaces 3`() {
        assertPatternLayout(" (2,4x) ([4x 4] , 2) ", jugglers = 1, paths = 4)
    }

    @Test
    fun `pattern parsing non-first brace values`() {
        assertPatternLayout("{5}{1}", jugglers = 1, paths = 3)
        assertPatternLayout("5{1}", jugglers = 1, paths = 3)
        assertPatternLayout("{5}1{5}1", jugglers = 1, paths = 3)
    }

    @Test
    fun `pattern parsing bad average remains a user error`() {
        val exception = assertFailsWith<JuggleExceptionUser> {
            SiteswapPattern().fromString("23")
        }
        assertEquals(jlGetStringResource(Res.string.error_siteswap_bad_average), exception.message)
    }

    private fun assertPatternLayout(patternString: String, jugglers: Int, paths: Int) {
        val pattern = SiteswapPattern().fromString(patternString)
        pattern.asJmlPattern().layout
        assertEquals(jugglers, pattern.numberOfJugglers)
        assertEquals(paths, pattern.numberOfPaths)
    }
}
