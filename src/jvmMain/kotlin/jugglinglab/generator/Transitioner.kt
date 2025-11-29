//
// Transitioner.kt
//
// Base class for all Transitioner objects, which find transitions between
// two patterns in a given notation.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser

abstract class Transitioner {
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun initTransitioner(arg: String) {
        val args: List<String> = arg.split(' ', '\n').filter { it.isNotEmpty() }
        initTransitioner(args)
    }

    // return the notation name
    abstract val notationName: String

    // use command line args
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun initTransitioner(args: List<String>)

    // run the transitioner with no limits
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun runTransitioner(t: GeneratorTarget): Int

    // run the transitioner with bounds on space and time
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun runTransitioner(t: GeneratorTarget, numLimit: Int, secsLimit: Double): Int

    companion object {
        // The built-in transitioners
        @Suppress("unused")
        val builtinTransitioners: List<String> = listOf(
            "Siteswap",
            )

        fun newTransitioner(name: String): Transitioner? {
            if (name.equals("siteswap", ignoreCase = true)) {
                return SiteswapTransitioner()
            }
            return null
        }
    }
}
