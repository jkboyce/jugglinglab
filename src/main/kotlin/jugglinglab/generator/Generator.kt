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
import javax.swing.JPanel

abstract class Generator {
    @Throws(JuggleExceptionUser::class)
    fun initGenerator(arg: String) {
        val args: List<String> = arg.split(' ', '\n').filter { it.isNotEmpty() }
        initGenerator(args.toTypedArray())
    }

    // return the notation name
    abstract val notationName: String

    // return a startup text message
    abstract val startupMessage: String

    // return a JPanel to be used by ApplicationPanel in the UI
    abstract val generatorControl: JPanel

    // reset control values to defaults
    abstract fun resetGeneratorControl()

    // use parameters from generator control
    @Throws(JuggleExceptionUser::class)
    abstract fun initGenerator()

    // use command line args
    @Throws(JuggleExceptionUser::class)
    abstract fun initGenerator(args: Array<String>)

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
