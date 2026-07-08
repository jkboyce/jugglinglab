//
// SiteswapPatternTest.kt
//
// Unit tests for SiteswapPattern.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import org.jugglinglab.composeapp.generated.resources.Res
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlGetStringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SiteswapPatternTest {
    @Test
    fun `pattern parsing 1`() {
        val pattern = SiteswapPattern().fromString("868671")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(6, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing thrown 2`() {
        val pattern = SiteswapPattern().fromString("(4,3x)!(2,0)!(3x,0)!")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(4, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing short beats`() {
        val pattern = SiteswapPattern().fromString("(0,6x)!(0,0)!(6x,0)!(0,0)!")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing mixed sync async`() {
        val pattern = SiteswapPattern().fromString("4x1(4x,3x)*")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing squeeze pattern`() {
        val pattern = SiteswapPattern().fromString("([42],4x)*")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(5, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing multiplex zero regressions`() {
        val cases = listOf(
            "([20],[20])([20],[20])" to 2,
            "([40],[40])([40],[40])([40],[40])" to 4,
            "[60][60][60][60]" to 6,
        )

        for ((patternString, expectedPaths) in cases) {
            assertPatternLayouts(patternString, expectedPaths)
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
            assertPatternLayouts(patternString, expectedPaths)
        }
    }

    @Test
    fun `pattern parsing multiplex zero placeholders with holds`() {
        assertPatternLayouts("24[504]", 5)
    }

    @Test
    fun `pattern parsing sync async transition`() {
        val pattern = SiteswapPattern().fromString("(645^2)65x6x1x((6x,4)*^2)(7,5x)(4,1x)!")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(5, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing passing 1`() {
        val pattern = SiteswapPattern().fromString("<([2xp/2x],[2xp/2])|(2,[2/2xp])><(2,[2p/2])|([2/2p],[2/2p])>")
        pattern.asJmlPattern().layout
        assertEquals(2, pattern.numberOfJugglers)
        assertEquals(7, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing large throws`() {
        val pattern = SiteswapPattern().fromString("{49}1")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(25, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 1`() {
        val pattern = SiteswapPattern().fromString("3BB")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 2`() {
        val pattern = SiteswapPattern().fromString("R3R3xL3L3x")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 3`() {
        val pattern = SiteswapPattern().fromString("<R|L><4xp|3><3|4xp>")
        pattern.asJmlPattern().layout
        assertEquals(2, pattern.numberOfJugglers)
        assertEquals(7, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 4`() {
        val pattern = SiteswapPattern().fromString("(4,5x)(4,1x)!R5x41x")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(4, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing 0 pattern 1`() {
        // corner case
        val pattern = SiteswapPattern().fromString("0")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(0, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing 0 pattern 2`() {
        val pattern = SiteswapPattern().fromString("<0|0>")
        pattern.asJmlPattern().layout
        assertEquals(2, pattern.numberOfJugglers)
        assertEquals(0, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing spaces 1`() {
        val pattern = SiteswapPattern().fromString("[53] 22")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(4, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing spaces 2`() {
        val pattern = SiteswapPattern().fromString("[5 3  ] 2 2 ")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(4, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing spaces 3`() {
        val pattern = SiteswapPattern().fromString(" (2,4x) ([4x 4] , 2) ")
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(4, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing non-first brace values`() {
        val pattern1 = SiteswapPattern().fromString("{5}{1}")
        pattern1.asJmlPattern().layout
        assertEquals(1, pattern1.numberOfJugglers)

        val pattern2 = SiteswapPattern().fromString("5{1}")
        pattern2.asJmlPattern().layout
        assertEquals(1, pattern2.numberOfJugglers)

        val pattern3 = SiteswapPattern().fromString("{5}1{5}1")
        pattern3.asJmlPattern().layout
        assertEquals(1, pattern3.numberOfJugglers)
    }

    @Test
    fun `pattern parsing bad average remains a user error`() {
        val exception = assertFailsWith<JuggleExceptionUser> {
            SiteswapPattern().fromString("23")
        }
        assertEquals(jlGetStringResource(Res.string.error_siteswap_bad_average), exception.message)
    }

    private fun assertPatternLayouts(patternString: String, expectedPaths: Int) {
        val pattern = SiteswapPattern().fromString(patternString)
        pattern.asJmlPattern().layout
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(expectedPaths, pattern.numberOfPaths)
    }
}
