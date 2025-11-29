//
// GeneratorTargetPatternList.kt
//
// Adapter to send generator output to a PatternListPanel.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import jugglinglab.core.Constants
import jugglinglab.core.PatternListPanel
import jugglinglab.notation.SiteswapPattern
import jugglinglab.util.JuggleException
import jugglinglab.util.JuggleExceptionInternal
import javax.swing.SwingUtilities

class GeneratorTargetPatternList(val listTarget: PatternListPanel) : GeneratorTarget {
    @Throws(JuggleExceptionInternal::class)
    override fun addResult(display: String, notation: String?, anim: String?) {
        @Suppress("KotlinConstantConditions")
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
        SwingUtilities.invokeLater { listTarget.addPattern(display, null, notation, anim) }
    }

    override fun completed() {}
}
