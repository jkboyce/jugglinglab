//
// Generator.kt
//
// This class defines a general object that is capable of generating patterns
// in some notation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
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

    // use command line args
    @Throws(JuggleExceptionUser::class)
    abstract fun initGenerator(args: List<String>)

    // run the generator with bounds on space and time; negative numbers mean
    // no limits
    @Throws(JuggleExceptionUser::class, JuggleExceptionInternal::class)
    abstract fun runGenerator(t: GeneratorTarget, maxNum: Int = -1, secs: Double = -1.0): Int

    companion object {
        // The built-in generators
        @Suppress("unused")
        val builtinGenerators = arrayOf("Siteswap")

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
