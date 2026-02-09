//
// JlFunc.kt
//
// Some useful functions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.jugglinglab.util

import org.jugglinglab.composeapp.generated.resources.*
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

//------------------------------------------------------------------------------
// Mathematical conveniences
//------------------------------------------------------------------------------

// Calculate the binomial coefficient (a choose b).

fun jlBinomial(a: Int, b: Int): Int {
    var result = 1
    for (i in 0..<b) {
        result *= (a - i)
        result /= (i + 1)
    }
    return result
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
// Helpers for string processing
//------------------------------------------------------------------------------

// Throughout Juggling Lab we use a notation within strings to indicate
// repeated sections:  `...(stuff)^n...`. This function expands all such
// repeats (including nested ones) to produce a fully-expanded string.
//
// NOTE: A limitation is that if `stuff` contains parentheses they must
// be balanced. Otherwise we get ambiguous cases like '(()^5' --> does this
// expand to '(((((' or '('?

fun jlExpandRepeats(str: String): String {
    val sb = StringBuilder()
    addExpansionToBuffer(str, sb)
    return sb.toString()

    /*
    System.out.println(JLFunc.expandRepeats("hello"));
    System.out.println(JLFunc.expandRepeats("he(l)^2o"));
    System.out.println(JLFunc.expandRepeats("hel(lo)^2"));
    System.out.println(JLFunc.expandRepeats("(hello)^0world"));
    System.out.println(JLFunc.expandRepeats("((hello )^2there)^2"));
    System.out.println(JLFunc.expandRepeats("((hello )there)^2"));
    System.out.println(JLFunc.expandRepeats("((hello )^2there)"));
    */
}

private fun addExpansionToBuffer(str: String, sb: StringBuilder) {
    var pos = 0
    while (pos < str.length) {
        val ch = str[pos]

        if (ch == '(') {
            val result = tryParseRepeat(str, pos)

            if (result == null) {
                // no repeat found, treat like a normal character
                sb.append(ch)
                ++pos
            } else {
                val repeatEnd = result[0]
                val repeats = result[1]
                val resumeStart = result[2]

                // snip out the string to be repeated:
                val str2 = str.substring(pos + 1, repeatEnd)

                repeat(repeats) {
                    addExpansionToBuffer(str2, sb)
                }

                pos = resumeStart
            }
        } else {
            sb.append(ch)
            ++pos
        }
    }
}

// Scan forward in the string to find:
// (1) the end of the repeat (buffer position of ')' where depth returns to 0)
// (2) the number of repeats
//     - if the next non-whitespace char after (a) is not '^' -> no repeat
//     - if the next non-whitespace char after '^' is not a number -> no repeat
//     - parse the numbers after '^' up through the first non-number (or end
//       of string) into an int = `repeats`
// (3) the buffer position of the first non-numeric character after the
//     repeat number (i.e. where to resume) = `resume_start`
//     (=str.length() if hit end of buffer)
//
// We always call this function with `fromPos` sitting on the '(' that starts
// the repeat section.

private fun tryParseRepeat(str: String, fromPos: Int): IntArray? {
    var depth = 0

    for (pos in fromPos..<str.length) {
        val ch = str[pos]
        if (ch == '(') {
            ++depth
        } else if (ch == ')') {
            --depth
            if (depth == 0) {
                // see if we match the form '^(int)...' after the closing parenthesis
                val regex = Regex("^\\s*\\^\\s*(\\d+).*")
                val match = regex.matchEntire(str.substring(pos + 1)) ?: return null

                val repeats = match.groupValues[1].toInt()
                val group1Range = match.groups[1]!!.range
                val resumeStart = group1Range.last + 1 + pos + 1
                return intArrayOf(pos, repeats, resumeStart)
            }
        }
    }
    return null
}

// Split an input string at a given delimiter, but only outside of parentheses.

fun jlSplitOnCharOutsideParens(input: String, delimiter: Char): List<String> {
    if (input.isEmpty()) return listOf("")

    val result = mutableListOf<String>()
    var parenLevel = 0
    var lastSplit = 0
    for (i in input.indices) {
        when (input[i]) {
            '(' -> parenLevel++
            ')' -> parenLevel--
            delimiter -> {
                if (parenLevel == 0) {
                    result.add(input.substring(lastSplit, i))
                    lastSplit = i + 1
                }
            }
        }
    }
    result.add(input.substring(lastSplit))
    return result.filter { it.isNotEmpty() }
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

//------------------------------------------------------------------------------
// Helper for converting numbers to/from strings
//------------------------------------------------------------------------------

// Parse a string as a finite-valued Double. Throw an error if there is a
// number format problem, or if the value is not finite ("NaN", "Infinity", ...)

expect fun jlParseFiniteDouble(input: String): Double

// Convert a double value to a String, rounding to `digits` places after
// the decimal point, with trailing '.' and '0's suppressed.

expect fun jlToStringRounded(value: Double, digits: Int): String

//------------------------------------------------------------------------------
// Helpers for message display and error handling
//------------------------------------------------------------------------------

// Handle an informational message.

expect fun jlHandleUserMessage(parent: Any?, title: String?, msg: String?)

// Handle a recoverable user error.

expect fun jlHandleUserException(parent: Any?, msg: String?)

// Handle a fatal exception. The intent is that these exceptions only happen in
// the event of a bug in Juggling Lab.

expect fun jlHandleFatalException(e: Exception)

//------------------------------------------------------------------------------
// Helpers for loading resources (UI strings, error messages, images, ...)
//------------------------------------------------------------------------------

// Load a string resource.

fun jlGetStringResource(key: StringResource, vararg args: Any?): String {
    val message = runBlocking { getString(key) }
    return if (args.isEmpty()) {
        message
    } else {
        message.format(*args)
    }
}

// Load an image from the given source.
//
// `source` is either a URL or the name of a Compose drawable resource.
// URLs may not be loadable in all contexts. In the event of a problem,
// throw a JuggleExceptionUser with a relevant error message.

@Throws(JuggleExceptionUser::class)
fun jlGetImageResource(source: String): ImageBitmap {
    return try {
        if (source.contains('/')) {
            // assume it's a URL and load accordingly
            jlLoadComposeImageFromUrl(source)
        } else {
            // load from a Compose resource
            runBlocking {
                val imageBytes = Res.readBytes("drawable/$source")
                jlBytesToImageBitmap(imageBytes)
            }
        }
    } catch (e: Exception) {
        val message = jlGetStringResource(Res.string.error_bad_file, e.message ?: "")
        throw JuggleExceptionUser(message)
    }
}

expect fun jlLoadComposeImageFromUrl(urlString: String): ImageBitmap

expect fun jlBytesToImageBitmap(bytes: ByteArray): ImageBitmap

//------------------------------------------------------------------------------
// Other
//------------------------------------------------------------------------------

// Return the native screen refresh rate.

expect fun jlGetScreenFps(): Double

// Return platform information.

expect fun jlGetCurrentPlatform(): String

expect fun jlGetAboutBoxPlatform(): String
