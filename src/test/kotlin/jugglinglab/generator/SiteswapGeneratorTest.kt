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

    private fun Int.pow(b: Int): Int {
        var result = 1
        repeat (b) {
            result *= this
        }
        return result
    }

    @Test
    fun `3 balls async siteswaps`() {
        val patterns = runGeneratorTestCase("3 5 6 -se -f")
        assertEquals(55, patterns.size)
        assertEquals("444042", patterns[10])
    }

    @Test
    fun `siteswap counting rule 1`() {
        // Verify the Buhler et al formula
        val balls = 4
        val period = 3
        val formula = (balls+1).pow(period)-balls.pow(period)
        val patterns = runGeneratorTestCase("$balls ${balls * period} $period -f -rot -se")
        assertEquals(formula, patterns.size)
    }

    @Test
    fun `siteswap counting rule 2`() {
        // Verify the Buhler et al formula
        val balls = 5
        val period = 5
        val formula = (balls+1).pow(period)-balls.pow(period)
        val patterns = runGeneratorTestCase("$balls ${balls * period} $period -f -rot -se")
        assertEquals(formula, patterns.size)
    }

    @Test
    fun `prime siteswap counting height limited`() {
        // Generating prime patterns in the height-limited case
        val patterns = runGeneratorTestCase("5 7 - -prime -se")
        assertEquals(337, patterns.size)
        assertEquals("777717077717707740", patterns.last())
    }

    @Test
    fun `prime siteswap counting period limited`() {
        // Generating prime patterns in the period-limited case, validated
        // against `jprime`
        val patterns = runGeneratorTestCase("4 24 6 -prime -se")
        assertEquals(1663, patterns.size)
    }

    // TODO:
    // - multiplexing
    // - sync mode
    // - passing mode
    // - include, exclude, regular expressions
    // - passing delay
    // - "true" multiplexing
    // - no clustered throws
}