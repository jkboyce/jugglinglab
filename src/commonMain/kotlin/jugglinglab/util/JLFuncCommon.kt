//
// JLFuncCommon.kt
//
// Some useful functions.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package jugglinglab.util

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Calculate the binomial coefficient (a choose b).

fun jlBinomial(a: Int, b: Int): Int {
    var result = 1
    for (i in 0..<b) {
        result *= (a - i)
        result /= (i + 1)
    }
    return result
}

// Compare two version numbers.
//
// returns 0 if equal, less than 0 if v1 < v2, greater than 0 if v1 > v2.

fun jlCompareVersions(v1: String, v2: String): Int {
    val components1 = v1.split('.')
    val components2 = v2.split('.')

    // Compare parts of the version strings that exist in both
    for ((c1, c2) in components1.zip(components2)) {
        val result = c1.toInt().compareTo(c2.toInt())
        if (result != 0) {
            return result
        }
    }

    // If prefixes are equal, the longer version string is greater
    return components1.size.compareTo(components2.size)
}

// Check if point (x, y) is near a line segment connecting (x1, y1) and
// (x2, y2). "Near" means shortest distance is less than `slop`.

fun jlIsNearLine(x: Int, y: Int, x1: Int, y1: Int, x2: Int, y2: Int, slop: Int): Boolean {
    if (x < (min(x1, x2) - slop) || x > (max(x1, x2) + slop)) {
        return false
    }
    if (y < (min(y1, y2) - slop) || y > (max(y1, y2) + slop)) {
        return false
    }
    var d = ((x2 - x1) * (y - y1) - (x - x1) * (y2 - y1)).toDouble()
    d = abs(d) / sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble())
    return d.toInt() <= slop
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
