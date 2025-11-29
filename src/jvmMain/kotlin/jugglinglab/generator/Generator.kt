//
// Generator.kt
//
// This class defines a general object that is capable of generating tricks
// and converting them into commands that the animator understands.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.generator

import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.JuggleExceptionUser

abstract class Generator {
    @Throws(JuggleExceptionUser::class)
    fun initGenerator(arg: String) {
        val args: List<String> = arg.split(' ', '\n').filter { it.isNotEmpty() }
        initGenerator(args)
    }

    // return the notation name
    abstract val notationName: String

    // return a startup text message
    abstract val startupMessage: String

    // use command line args
    @Throws(JuggleExceptionUser::class)
    abstract fun initGenerator(args: List<String>)

    // run the generator with no limits
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun runGenerator(t: GeneratorTarget): Int

    // run the generator with bounds on space and time
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun runGenerator(t: GeneratorTarget, maxNum: Int, secs: Double): Int

    companion object {
        // The built-in generators
        @Suppress("unused")
        val builtinGenerators = arrayOf<String>("Siteswap")

        fun newGenerator(name: String): Generator? {
            if (name.equals("siteswap", ignoreCase = true)) return SiteswapGenerator()
            return null
        }
    }
}

// Interface to receive results from a generator.

interface GeneratorTarget {
    // new result from the generator
    // - `display` is for visual display
    // - `notation` and `anim`, if non-null, describe a pattern
    fun addResult(display: String, notation: String?, anim: String?)

    // called when the generator is done
    fun completed() {}
}
