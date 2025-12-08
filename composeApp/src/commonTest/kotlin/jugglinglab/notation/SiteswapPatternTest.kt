//
// SiteswapPatternTest.kt
//
// Unit tests for SiteswapPattern.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import kotlin.test.Test
import kotlin.test.assertEquals

class SiteswapPatternTest {
    @Test
    fun `pattern parsing 1`() {
        val pattern = SiteswapPattern().fromString("868671")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(6, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing thrown 2`() {
        val pattern = SiteswapPattern().fromString("(4,3x)!(2,0)!(3x,0)!")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(4, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing short beats`() {
        val pattern = SiteswapPattern().fromString("(0,6x)!(0,0)!(6x,0)!(0,0)!")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing mixed sync async`() {
        val pattern = SiteswapPattern().fromString("4x1(4x,3x)*")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing squeeze pattern`() {
        val pattern = SiteswapPattern().fromString("([42],4x)*")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(5, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing sync async transition`() {
        val pattern = SiteswapPattern().fromString("(645^2)65x6x1x((6x,4)*^2)(7,5x)(4,1x)!")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(5, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing passing 1`() {
        val pattern = SiteswapPattern().fromString("<([2xp/2x],[2xp/2])|(2,[2/2xp])><(2,[2p/2])|([2/2p],[2/2p])>")
        assertEquals(2, pattern.numberOfJugglers)
        assertEquals(7, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing large throws`() {
        val pattern = SiteswapPattern().fromString("{49}1")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(25, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 1`() {
        val pattern = SiteswapPattern().fromString("3BB")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 2`() {
        val pattern = SiteswapPattern().fromString("R3R3xL3L3x")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(3, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 3`() {
        val pattern = SiteswapPattern().fromString("<R|L><4xp|3><3|4xp>")
        assertEquals(2, pattern.numberOfJugglers)
        assertEquals(7, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing modifiers 4`() {
        val pattern = SiteswapPattern().fromString("(4,5x)(4,1x)!R5x41x")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(4, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing 0 pattern 1`() {
        // corner case
        val pattern = SiteswapPattern().fromString("0")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(0, pattern.numberOfPaths)
    }

    @Test
    fun `pattern parsing 0 pattern 2`() {
        val pattern = SiteswapPattern().fromString("<0|0>")
        assertEquals(2, pattern.numberOfJugglers)
        assertEquals(0, pattern.numberOfPaths)
    }
}