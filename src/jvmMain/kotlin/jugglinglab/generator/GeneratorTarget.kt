//
// GeneratorTarget.kt
//
// This class is an adapter to handle the generated output. It can send output
// to a PatternListPanel, PrintStream, or StringBuffer.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import jugglinglab.core.Constants
import jugglinglab.core.PatternListPanel
import jugglinglab.notation.SiteswapPattern
import jugglinglab.util.JuggleException
import jugglinglab.util.JuggleExceptionInternal
import java.io.PrintStream
import javax.swing.SwingUtilities

class GeneratorTarget {
    // only one of these is non-null, which defines the target for output
    var patterns: ArrayList<String>? = null
    var listTarget: PatternListPanel? = null
    var printTarget: PrintStream? = null
    var stringTarget: StringBuilder? = null

    constructor() {
        // this form is used for testing
        patterns = ArrayList()
    }

    constructor(target: PatternListPanel) {
        listTarget = target
    }

    constructor(ps: PrintStream) {
        printTarget = ps
    }

    constructor(sb: StringBuilder) {
        stringTarget = sb
    }

    @Throws(JuggleExceptionInternal::class)
    fun writePattern(display: String, notation: String, anim: String) {
        @Suppress("KotlinConstantConditions")
        if (Constants.VALIDATE_GENERATED_PATTERNS) {
            if (listTarget != null || printTarget != null) {
                if (notation.equals("siteswap", ignoreCase = true) && anim.isNotEmpty()) {
                    try {
                        SiteswapPattern().fromString(anim)
                    } catch (_: JuggleException) {
                        val msg = "Error: pattern \"$anim\" did not validate"
                        println(msg)
                        throw JuggleExceptionInternal(msg)
                    }
                    println("pattern \"$anim\" validated")
                }
            }
        }

        if (anim.isNotEmpty()) {
            patterns?.add(anim)
        }
        if (listTarget != null) {
            // Note we may not be running on the event dispatch thread
            SwingUtilities.invokeLater { listTarget?.addPattern(display, null, notation, anim) }
        }
        printTarget?.println(display)
        stringTarget?.append(display)?.append('\n')
    }

    // Messages like "# of patterns found" come through here.

    fun setStatus(display: String) {
        if (listTarget != null) {
            SwingUtilities.invokeLater { listTarget!!.addPattern(display, null, null, null) }
        }
        printTarget?.println(display)
    }
}
