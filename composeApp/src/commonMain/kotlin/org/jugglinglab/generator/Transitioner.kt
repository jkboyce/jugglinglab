//
// Transitioner.kt
//
// Base class for all Transitioner objects, which find transitions between
// two patterns in a given notation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.generator

import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser

abstract class Transitioner {
    // return the notation name
    abstract val notationName: String

    // run the transitioner with no limits
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class, kotlin.coroutines.cancellation.CancellationException::class)
    abstract suspend fun runTransitioner(t: GeneratorTarget): Int

    // run the transitioner with bounds on space and time
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class, kotlin.coroutines.cancellation.CancellationException::class)
    abstract suspend fun runTransitioner(t: GeneratorTarget, maxNum: Int = -1, maxTime: Double = -1.0): Int

    companion object {
        // The built-in transitioners
        @Suppress("unused")
        val builtinTransitioners: List<String> = listOf(
            "Siteswap",
            )

        fun isTransitionerSupported(name: String): Boolean {
            return name.equals("siteswap", ignoreCase = true)
        }

        fun newTransitioner(name: String, arg: String): Transitioner? {
            if (isTransitionerSupported(name)) {
                return SiteswapTransitioner(arg)
            }
            return null
        }
    }
}
