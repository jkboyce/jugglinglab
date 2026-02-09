//
// GeneratorTargetPatternList.kt
//
// Adapter to send generator output to a PatternListPanel.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.generator

import org.jugglinglab.core.Constants
import org.jugglinglab.ui.PatternListPanel
import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.util.JuggleException
import org.jugglinglab.util.JuggleExceptionInternal
import javax.swing.SwingUtilities

class GeneratorTargetPatternList(
    val patternListTarget: PatternListPanel
) : GeneratorTarget {
    @Throws(JuggleExceptionInternal::class)
    override fun addResult(display: String, notation: String?, anim: String?) {
        if (Constants.VALIDATE_GENERATED_PATTERNS) {
            if (notation != null && anim != null && notation.equals(
                    "siteswap",
                    ignoreCase = true
                ) && anim.isNotEmpty()
            ) {
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

        // Note we may not be running on the event dispatch thread
        SwingUtilities.invokeLater { patternListTarget.addPattern(display, null, notation, anim) }
    }

    override fun completed() {
        SwingUtilities.invokeLater { patternListTarget.parentFrame?.onGeneratorDone() }
    }
}
