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
    var ltarget: PatternListPanel? = null
    var ptarget: PrintStream? = null
    var btarget: StringBuilder? = null
    var prefix: String? = null
    var suffix: String? = null

    constructor(target: PatternListPanel) {
        this.ltarget = target
    }

    constructor(ps: PrintStream) {
        this.ptarget = ps
    }

    constructor(sb: StringBuilder) {
        this.btarget = sb
    }

    @Throws(JuggleExceptionInternal::class)
    fun writePattern(display: String, notation: String, anim: String) {
        var display = display
        var anim = anim
        if (prefix != null) {
            display = prefix + display
            anim = prefix + anim
        }
        if (suffix != null) {
            display += suffix
            anim += suffix
        }

        @Suppress("KotlinConstantConditions")
        if (Constants.VALIDATE_GENERATED_PATTERNS) {
            if (ltarget != null || ptarget != null) {
                if (notation.equals("siteswap", ignoreCase = true) && !anim.isEmpty()) {
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

        if (ltarget != null) {
            // Note we may not be running on the event dispatch thread
            SwingUtilities.invokeLater { ltarget!!.addPattern(display, null, notation, anim) }
        }
        if (ptarget != null) {
            ptarget!!.println(display)
        }
        if (btarget != null) {
            btarget!!.append(display).append('\n')
        }
    }

    // Set a prefix and suffix for both the displayed string and animation string.
    fun setPrefixSuffix(pr: String?, su: String?) {
        prefix = pr
        suffix = su
    }

    // Messages like "# of patterns found" come through here.
    fun setStatus(display: String?) {
        if (ltarget != null) {
            SwingUtilities.invokeLater { ltarget!!.addPattern(display, null, null, null) }
        }
        if (ptarget != null) {
            ptarget!!.println(display)
        }
    }
}
