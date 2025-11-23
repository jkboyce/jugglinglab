//
// JLFuncCommon.kt
//
// Some useful functions.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package jugglinglab.util

// Calculate the binomial coefficient (a choose b).

fun jlBinomial(a: Int, b: Int): Int {
    var result = 1
    for (i in 0..<b) {
        result *= (a - i)
        result /= (i + 1)
    }
    return result
}

//------------------------------------------------------------------------------
// Helpers for converting numbers to/from strings
//------------------------------------------------------------------------------

@Suppress("unused")
expect object NumberFormatter {
    // Parse a string as a finite-valued Double. Throw an error if there is a
    // number format problem, or if the value is not finite ("NaN", "Infinity", ...)
    fun jlParseFiniteDouble(input: String): Double

    // Convert a double value to a String, rounding to `digits` places after
    // the decimal point, with trailing '.' and '0's suppressed.
    fun jlToStringRounded(value: Double, digits: Int): String
}
