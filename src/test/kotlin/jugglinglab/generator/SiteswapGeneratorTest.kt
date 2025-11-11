//
// SiteswapGeneratorTest.kt
//
// Unit tests for SiteswapGenerator.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class SiteswapGeneratorTest {
    private fun runGeneratorTestCase(input: String): ArrayList<String> {
        val gen = SiteswapGenerator()
        gen.initGenerator(input.split(" ").toTypedArray())
        val target = GeneratorTarget()
        gen.runGenerator(target)
        return target.patterns!!
    }

    @Test
    fun `3 balls async siteswaps`() {
        val patterns = runGeneratorTestCase("3 5 6 -se -f")
        assertEquals(55, patterns.size)
        assertEquals("444042", patterns[10])
    }
}