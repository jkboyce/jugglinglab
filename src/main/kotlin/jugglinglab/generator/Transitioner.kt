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
import javax.swing.JPanel

abstract class Transitioner {
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    fun initTransitioner(arg: String) {
        val args: List<String> = arg.split(' ', '\n').filter { it.isNotEmpty() }
        initTransitioner(args.toTypedArray())
    }

    // return the notation name
    abstract val notationName: String

    // return a JPanel to be used by ApplicationPanel in the UI
    abstract val transitionerControl: JPanel

    // reset control values to defaults
    abstract fun resetTransitionerControl()

    // use parameters from transitioner control
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun initTransitioner()

    // use command line args
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun initTransitioner(args: Array<String>)

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
