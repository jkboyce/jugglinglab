//
// SiteswapPatternTest.kt
//
// Unit tests for SiteswapPattern.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SiteswapPatternTest {
    @Test
    fun `pattern parsing 1`() {
        val pattern = SiteswapPattern().fromString("868671")
        assertEquals(1, pattern.numberOfJugglers)
        assertEquals(6, pattern.numberOfPaths)
    }
}