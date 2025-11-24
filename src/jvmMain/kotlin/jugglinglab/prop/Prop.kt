//
// Prop.kt
//
// This is the base type of all props in Juggling Lab.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.prop

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.text.MessageFormat

abstract class Prop {
    protected var initString: String? = null

    @Throws(JuggleExceptionUser::class)
    fun initProp(st: String?) {
        initString = st
        init(st)
    }

    //--------------------------------------------------------------------------
    // Abstract methods defined by subclasses
    //--------------------------------------------------------------------------

    abstract val type: String

    abstract val isColorable: Boolean

    abstract fun getEditorColor(): Color

    abstract fun getParameterDescriptors(): List<ParameterDescriptor>

    @Throws(JuggleExceptionUser::class)
    protected abstract fun init(st: String?)

    abstract fun getMax(): Coordinate? // in cm

    abstract fun getMin(): Coordinate? // in cm

    abstract fun getWidth(): Double // prop width in cm

    abstract fun getProp2DImage(zoom: Double, camangle: DoubleArray): Image?

    abstract fun getProp2DSize(zoom: Double): Dimension?

    abstract fun getProp2DCenter(zoom: Double): Dimension?

    abstract fun getProp2DGrip(zoom: Double): Dimension?

    companion object {
        val builtinProps: List<String> = listOf(
            "Ball",
            "Image",
            "Ring",
        )

        // Create a new prop of the given type.
        @Throws(JuggleExceptionUser::class)
        fun newProp(type: String): Prop {
            if (type.equals("ball", ignoreCase = true)) {
                return BallProp()
            } else if (type.equals("image", ignoreCase = true)) {
                return ImageProp()
            } else if (type.equals("ring", ignoreCase = true)) {
                return RingProp()
            }

            val template = errorstrings.getString("Error_prop_type")
            val arguments = arrayOf<Any?>(type)
            throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
        }

        val COLOR_NAMES: List<String> = listOf(
            "transparent",
            "black",
            "blue",
            "cyan",
            "gray",
            "green",
            "magenta",
            "orange",
            "pink",
            "red",
            "white",
            "yellow",
        )

        val COLOR_VALS: List<Color> = listOf(
            Color(0, 0, 0, 0),
            Color.black,
            Color.blue,
            Color.cyan,
            Color.gray,
            Color.green,
            Color.magenta,
            Color.orange,
            Color.pink,
            Color.red,
            Color.white,
            Color.yellow,
        )

        val COLOR_MIXED: List<String> = listOf(
            "red",
            "green",
            "blue",
            "yellow",
            "cyan",
            "magenta",
            "orange",
            "pink",
            "gray",
            "black",
        )
    }
}
