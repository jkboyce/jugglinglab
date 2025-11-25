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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.awt.GridBagConstraints
import java.awt.Insets
import java.text.*
import java.util.Locale
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import kotlin.math.min

// Helper extension function to convert Compose Color to AWT Color.

fun Color.toAwtColor(): java.awt.Color {
    // 1. toArgb() returns a 32-bit Int in ARGB format (Alpha in bits 24-31)
    // 2. We pass 'true' to the AWT constructor to indicate the Int includes Alpha
    return java.awt.Color(this.toArgb(), true)
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
