//
// Prop.kt
//
// This is the base type of all props in Juggling Lab.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.prop

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor
import jugglinglab.util.getStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import org.jetbrains.compose.resources.StringResource

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

    abstract val parameterDescriptors: List<ParameterDescriptor>

    @Throws(JuggleExceptionUser::class)
    protected abstract fun init(st: String?)

    abstract fun getMax(): Coordinate? // in cm

    abstract fun getMin(): Coordinate? // in cm

    abstract fun getWidth(): Double // prop width in cm, in juggler space

    abstract fun getProp2DImage(zoom: Double, camangle: DoubleArray): ImageBitmap?

    abstract fun getProp2DSize(zoom: Double, camangle: DoubleArray): IntSize?

    abstract fun getProp2DCenter(zoom: Double, camangle: DoubleArray): IntSize?

    abstract fun getProp2DGrip(zoom: Double, camangle: DoubleArray): IntSize?

    companion object {
        val builtinProps: List<String> = listOf(
            "Ball",
            "Image",
            "Ring",
        )
        val builtinPropsStringResources: List<StringResource> = listOf(
            Res.string.gui_prop_name_ball,
            Res.string.gui_prop_name_image,
            Res.string.gui_prop_name_ring,
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

            val message = getStringResource(Res.string.error_prop_type, type)
            throw JuggleExceptionUser(message)
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
            Color(0x00000000),
            Color.Black,
            Color.Blue,
            Color.Cyan,
            Color.Gray,
            Color.Green,
            Color.Magenta,
            Color(0xFFFFC800),
            Color(0xFFFFAFAF),
            Color.Red,
            Color.White,
            Color.Yellow,
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
