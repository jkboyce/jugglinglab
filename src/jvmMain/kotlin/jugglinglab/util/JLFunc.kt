//
// JLFunc.kt
//
// Some useful functions.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package jugglinglab.util

import jugglinglab.JugglingLab
import jugglinglab.JugglingLab.errorstrings
import jugglinglab.JugglingLab.guistrings
import java.awt.GridBagConstraints
import java.awt.Insets
import java.text.*
import java.util.Locale
import java.util.prefs.Preferences
import java.util.regex.Pattern
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

@Suppress("UnnecessaryVariable")
private fun tryParseRepeat(str: String, fromPos: Int): IntArray? {
    var depth = 0

    for (pos in fromPos..<str.length) {
        val ch = str[pos]

        if (ch == '(') {
            ++depth
        } else if (ch == ')') {
            --depth
            if (depth == 0) {
                // see if we match the form '^(int)...' after the closing
                // parenthesis
                val pat = Pattern.compile("^\\s*\\^\\s*(\\d+).*")
                val m = pat.matcher(str.substring(pos + 1))

                if (!m.matches()) {
                    return null
                }

                val repeatEnd = pos
                val repeats = m.group(1).toInt()
                val resumeStart = m.end(1) + pos + 1

                val result = IntArray(3)
                result[0] = repeatEnd
                result[1] = repeats
                result[2] = resumeStart
                return result
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
    val components1: Array<String?> =
        v1.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val components2: Array<String?> =
        v2.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val length = min(components1.size, components2.size)
    for (i in 0..<length) {
        val result = components1[i]!!.toInt().compareTo(components2[i]!!.toInt())
        if (result != 0) {
            return result
        }
    }
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

actual object NumberFormatter {
    private val nf: NumberFormat by lazy {
        // use US-style number formatting for interoperability of JML files across
        // Locales
        NumberFormat.getInstance(Locale.US)
    }

    @Throws(NumberFormatException::class)
    actual fun jlParseFiniteDouble(input: String): Double {
        try {
            val x = nf.parse(input).toDouble()
            if (x.isFinite()) {
                return x
            }
            throw NumberFormatException("not a finite value")
        } catch (_: ParseException) {
            throw NumberFormatException()
        }
    }

    actual fun jlToStringRounded(value: Double, digits: Int): String {
        val fmt = "###.##########".take(if (digits <= 0) 3 else 4 + min(10, digits))
        val formatter = DecimalFormat(fmt, DecimalFormatSymbols(Locale.US))
        var result = formatter.format(value)
        if (result == "-0") {
            // strange quirk
            result = "0"
        }
        return result
    }
}

//------------------------------------------------------------------------------
// Helpers for GridBagLayout
//------------------------------------------------------------------------------

fun constraints(location: Int, gridX: Int, gridY: Int): GridBagConstraints {
    return GridBagConstraints().apply {
        anchor = location
        fill = GridBagConstraints.NONE
        gridwidth = 1
        gridheight = 1
        gridx = gridX
        gridy = gridY
        weighty = 0.0
        weightx = 0.0
    }
}

fun constraints(location: Int, gridx: Int, gridy: Int, ins: Insets?): GridBagConstraints {
    return constraints(location, gridx, gridy).apply {
        insets = ins ?: Insets(0, 0, 0, 0)
    }
}

//------------------------------------------------------------------------------
// Helpers for file opening/saving
//------------------------------------------------------------------------------

val jfc: JFileChooser by lazy {
    object : JFileChooser() {
        override fun approveSelection() {
            val f = selectedFile

            if (f.exists() && dialogType == SAVE_DIALOG) {
                val template = guistrings.getString("JFC_File_exists_message")
                val arguments = arrayOf<Any?>(f.getName())
                val msg = MessageFormat.format(template, *arguments)
                val title = guistrings.getString("JFC_File_exists_title")

                val result = JOptionPane.showConfirmDialog(
                    this, msg, title, JOptionPane.YES_NO_CANCEL_OPTION
                )
                when (result) {
                    JOptionPane.YES_OPTION -> {
                        super.approveSelection()
                        return
                    }

                    JOptionPane.NO_OPTION, JOptionPane.CLOSED_OPTION -> return
                    JOptionPane.CANCEL_OPTION -> {
                        cancelSelection()
                        return
                    }
                }
            }

            try {
                Preferences.userRoot().node("Juggling Lab").put("base_dir", f.getParent())
            } catch (_: Exception) {
            }

            super.approveSelection()
        }
    }
}

// Sanitize filename based on platform restrictions.
//
// See e.g.:
// https://stackoverflow.com/questions/1976007/
//       what-characters-are-forbidden-in-windows-and-linux-directory-names

fun jlSanitizeFilename(fname: String): String {
    val index = fname.lastIndexOf(".")

    val base = if (index >= 0) fname.take(index) else fname
    val extension = if (index >= 0) fname.substring(index) else ""

    if (JugglingLab.isMacOS) {
        // remove all instances of `:` and `/`
        var b = base.replace("[:/]".toRegex(), "")

        // remove leading `.` and space
        while (b.startsWith(".") || b.startsWith(" ")) {
            b = b.substring(1)
        }

        if (b.isEmpty()) {
            b = "Pattern"
        }

        return b + extension
    } else if (JugglingLab.isWindows) {
        // remove all instances of `\/?:*"`
        var b = base.replace("[/?:*\"]".toRegex(), "")

        // disallow strings with `><|`
        var forbidden = (b.contains(">"))
        forbidden = forbidden || (b.contains("<"))
        forbidden = forbidden || (b.contains("|"))

        if (forbidden || b.isEmpty()) {
            b = "Pattern"
        }

        return b + extension
    } else if (JugglingLab.isLinux) {
        // change all `/` to `:`
        var b = base.replace("/".toRegex(), ":")

        // remove leading `.` and space
        while (b.startsWith(".") || b.startsWith(" ")) {
            b = b.substring(1)
        }

        if (b.isEmpty()) {
            b = "Pattern"
        }

        return b + extension
    } else {
        return fname
    }
}

@Throws(JuggleExceptionUser::class)
fun jlErrorIfNotSanitized(fname: String) {
    if (fname == jlSanitizeFilename(fname)) {
        return
    }
    throw JuggleExceptionUser(errorstrings.getString("Error_saving_disallowed_character"))
}
