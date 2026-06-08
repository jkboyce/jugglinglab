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
import kotlin.math.PI

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

fun Double.toRadians(): Double = this * PI / 180.0

fun Double.toDegrees(): Double = this * 180.0 / PI

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
                val sub = str.substring(pos + 1)
                val regex = Regex("^\\s*\\^\\s*(\\d+).*")
                val match = regex.matchEntire(sub) ?: return null

                val repeats = match.groupValues[1].toInt()
                val digitsStart = sub.indexOf(match.groupValues[1])
                val resumeStart = digitsStart + match.groupValues[1].length + pos + 1
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
    if (result.size > 1 && result.last().isEmpty() && input.endsWith(delimiter)) {
        result.removeAt(result.size - 1)
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

// Convert a digit to a single character, for siteswap output.

fun jlCharForDigit(digit: Int, radix: Int): Char {
    if (digit !in 0..<radix) return '\u0000'
    return if (digit < 10) {
        '0' + digit
    } else {
        'a' + (digit - 10)
    }
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
// Helper for logging/reporting crashes
//------------------------------------------------------------------------------

interface CrashReporter {
    fun recordThrowable(throwable: Throwable, message: String? = null)
}

// global reference configured at startup
lateinit var crashReporter: CrashReporter

//------------------------------------------------------------------------------
// Helpers for execution context information
//------------------------------------------------------------------------------

// Platform information.
expect val jlCurrentPlatform: String

expect val jlAboutBoxPlatform: String

expect val jlCurrentVersion: String

expect val jlIsDesktop: Boolean

expect val jlIsMobile: Boolean

// Platform type shortcuts.
val jlIsMacOs: Boolean by lazy {
    jlIsDesktop && jlCurrentPlatform.lowercase().startsWith("mac os x")
}
val jlIsWindows: Boolean by lazy {
    jlIsDesktop && jlCurrentPlatform.lowercase().startsWith("windows")
}
val jlIsLinux: Boolean by lazy {
    jlIsDesktop && jlCurrentPlatform.lowercase().startsWith("linux")
}
val jlIsAndroid: Boolean by lazy {
    jlIsMobile && jlCurrentPlatform.lowercase().startsWith("android")
}

// Timing and execution.
expect fun jlCurrentTimeMillis(): Long

expect val jlFileSystem: okio.FileSystem

expect fun jlExitProcess(status: Int)

//------------------------------------------------------------------------------
// Helpers for file opening/saving files
//------------------------------------------------------------------------------

// Sanitize filename based on platform-independent rules to ensure files can be
// transferred safely across Windows, macOS, Linux, Android, and iOS.
//
// Windows is the most restrictive, which forms the base of these rules.

fun jlSanitizeFilename(fname: String): String {
    if (fname.isEmpty()) return "Pattern"

    // 1. Separate base and extension
    val index = fname.lastIndexOf(".")
    var base = if (index >= 0) fname.take(index) else fname
    var extension = if (index >= 0) fname.substring(index) else ""

    // 2. Replace path separators with underscores (safe on all OSs)
    val pathsepRegex = "[\\\\/]".toRegex()
    base = base.replace(pathsepRegex, "_")
    extension = extension.replace(pathsepRegex, "_")

    // 3. Replace other forbidden characters with underscores:
    // : * ? " < > | and control characters (ASCII 0-31)
    val forbiddenRegex = "[:*?\"<>|\\x00-\\x1F]".toRegex()
    base = base.replace(forbiddenRegex, "_")
    extension = extension.replace(forbiddenRegex, "_")

    // 4. Remove leading dots and leading/trailing spaces from base
    base = base.trim()
    while (base.startsWith(".") || base.startsWith(" ")) {
        base = base.substring(1)
    }
    while (base.endsWith(".") || base.endsWith(" ")) {
        base = base.dropLast(1)
    }

    // 5. Trim trailing dots and spaces from extension
    extension = extension.trim()
    while (extension.endsWith(".") || extension.endsWith(" ")) {
        extension = extension.dropLast(1)
    }

    // 6. Check for Windows reserved device names (case-insensitive) on the base name
    val reservedNames = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    )
    if (reservedNames.contains(base.uppercase())) {
        base += "_pattern"
    }

    // 7. Ensure base is not empty after sanitization
    if (base.isEmpty()) {
        base = "Pattern"
    }

    return base + extension
}

@Throws(JuggleExceptionUser::class)
fun jlErrorIfNotSanitized(fname: String) {
    if (fname == jlSanitizeFilename(fname)) {
        return
    }
    throw JuggleExceptionUser(jlGetStringResource(Res.string.error_saving_disallowed_character))
}

//------------------------------------------------------------------------------
// Helpers for loading resources (UI strings, error messages, images, ...)
//------------------------------------------------------------------------------

// Drop-in replacement for java.lang.String.format()

private fun jlFormatString(format: String, vararg args: Any?): String {
    var result = format
    // 1. Replace indexed placeholders like %1$s, %2$d, etc.
    for (i in args.indices) {
        val index = i + 1
        val valueStr = args[i]?.toString() ?: "null"
        val regex = Regex("%$index\\$[-+]?[0-9]*\\.?[0-9]*[a-zA-Z]")
        result = result.replace(regex, valueStr)
    }

    // 2. Replace sequential placeholders like %s, %d, %f, etc.
    var argIndex = 0
    val sb = StringBuilder()
    var pos = 0
    val length = result.length
    while (pos < length) {
        val ch = result[pos]
        if (ch == '%' && pos + 1 < length) {
            val nextCh = result[pos + 1]
            if (nextCh == '%') {
                sb.append('%')
                pos += 2
                continue
            }

            var scan = pos + 1
            while (scan < length && (result[scan].isDigit() || result[scan] == '.' || result[scan] == '-' || result[scan] == '+')) {
                scan++
            }
            if (scan < length && (result[scan] == 's' || result[scan] == 'd' || result[scan] == 'f' || result[scan] == 'g' || result[scan] == 'x')) {
                if (argIndex < args.size) {
                    val value = args[argIndex++]
                    sb.append(value?.toString() ?: "null")
                } else {
                    sb.append("%" + result.substring(pos + 1, scan + 1))
                }
                pos = scan + 1
            } else {
                sb.append('%')
                pos++
            }
        } else {
            sb.append(ch)
            pos++
        }
    }
    return sb.toString()
}

fun jlGetStringResource(key: StringResource, vararg args: Any?): String {
    val message = runBlocking { getString(key) }
    return if (args.isEmpty()) {
        message
    } else {
        jlFormatString(message, *args)
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

// Open the platform's native share UI with the given URL string.
// `subject`  is used as the email subject.
// `htmlText` is used as a rich-text email body (falls back to `url` on
//            plain-text targets).

expect fun jlShareUrl(url: String, subject: String? = null, htmlText: String? = null)

// Share a file via the platform's native share UI.
// `content`  is the file's text content.
// `filename` is the suggested filename (e.g. "pattern.jml").
// `mimeType` is the MIME type (e.g. "application/xml").
// `subject`  is used as the email subject line.

expect fun jlShareFile(
    content: String,
    filename: String,
    mimeType: String,
    subject: String? = null
)

//------------------------------------------------------------------------------
// Helpers for playing audio
//------------------------------------------------------------------------------

expect fun jlPlayCatchSound(volume: Float = 1f)

expect fun jlPlayBounceSound(volume: Float = 1f)

//------------------------------------------------------------------------------
// Helper for back navigation
//------------------------------------------------------------------------------

@androidx.compose.runtime.Composable
expect fun BackHandler(enabled: Boolean = true, onBack: () -> Unit)
