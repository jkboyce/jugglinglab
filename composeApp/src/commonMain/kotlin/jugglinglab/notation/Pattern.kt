//
// Pattern.kt
//
// This is the base class for all non-JML pattern types in Juggling Lab.
// It parses from a string representation and creates a JmlPattern version of
// itself for the animator.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import jugglinglab.jml.JmlPattern
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterList

abstract class Pattern {
    // return the notation name
    abstract val notationName: String

    // define pattern from textual representation
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun fromString(config: String): Pattern

    // define pattern from ParameterList input
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun fromParameters(pl: ParameterList): Pattern

    // canonical string representation
    abstract override fun toString(): String

    // convert pattern to JML
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun asJmlPattern(): JmlPattern

    @Suppress("unused")
    companion object {
        // The built-in notations
        val builtinNotations: List<String> = listOf(
            "Siteswap",
            )

        // these should be in the same order as in the builtinNotations array
        const val NOTATION_NONE: Int = 0
        const val NOTATION_SITESWAP: Int = 1

        // Create a new blank pattern in the given notation.

        @Throws(JuggleExceptionUser::class)
        fun newPattern(notation: String): Pattern {
            if (notation.equals("siteswap", ignoreCase = true)) {
                return SiteswapPattern()
            }
            throw JuggleExceptionUser("Notation type '$notation' not recognized")
        }

        // Return the notation name with canonical capitalization.

        fun canonicalNotation(notation: String?): String? {
            for (n in builtinNotations) {
                if (n.equals(notation, ignoreCase = true)) {
                    return n
                }
            }
            return null
        }
    }
}
