//
// Generator.kt
//
// This class defines a general object that is capable of generating patterns
// in some notation.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.generator

import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser

abstract class Generator {
    // return the notation name
    abstract val notationName: String

    // run the generator, with optional limits on space and time
    @Throws(
        JuggleExceptionUser::class,
        JuggleExceptionInternal::class,
        kotlin.coroutines.cancellation.CancellationException::class
    )
    abstract suspend fun runGenerator(
        t: GeneratorTarget,
        maxNum: Int = -1,
        maxTime: Double = -1.0
    ): Int

    companion object {
        // The built-in generators
        @Suppress("unused")
        val builtinGenerators = arrayOf("Siteswap")

        fun isGeneratorSupported(name: String): Boolean {
            return name.equals("siteswap", ignoreCase = true)
        }

        fun newGenerator(name: String, arg: String): Generator? {
            if (isGeneratorSupported(name)) return SiteswapGenerator(arg)
            return null
        }
    }
}
