//
// BallProp.java
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.prop

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.Coordinate
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.ParameterDescriptor
import org.jugglinglab.util.ParameterList
import org.jugglinglab.util.jlParseFiniteDouble
import org.jugglinglab.util.jlGetStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.geometry.Rect
import kotlin.math.max
import kotlin.math.min

class BallProp : Prop() {
    // set by init()
    private var color: Color = COLOR_DEF
    private var colornum: Int = COLORNUM_DEF
    private var diam: Double = DIAM_DEF // diameter, in cm
    private var highlight: Boolean = HIGHLIGHT_DEF
    // recalculated based on zoom
    private var image: ImageBitmap? = null
    private var size: IntSize? = null
    private var center: IntSize? = null
    private var grip: IntSize? = null
    private var lastzoom: Double = 0.0

    override val type = "Ball"

    override val isColorable = true

    override fun getEditorColor(): Color {
        return color
    }

    override val parameterDescriptors
        get() = listOf(
            ParameterDescriptor(
                "color",
                ParameterDescriptor.Companion.TYPE_CHOICE,
                colorNames,
                colorNames[COLORNUM_DEF],
                colorNames[colornum]
            ),
            ParameterDescriptor(
                "diam",
                ParameterDescriptor.Companion.TYPE_FLOAT,
                null,
                DIAM_DEF,
                diam
            ),
            ParameterDescriptor(
                "highlight",
                ParameterDescriptor.Companion.TYPE_BOOLEAN,
                null,
                HIGHLIGHT_DEF,
                highlight
            ),
        )

    @Throws(JuggleExceptionUser::class)
    override fun init(st: String?) {
        if (st == null) {
            return
        }
        val pl = ParameterList(st)

        val colorstr = pl.getParameter("color")
        if (colorstr != null) {
            var temp: Color? = null

            if (colorstr.indexOf(',') == -1) {  // color name
                for (i in colorNames.indices) {
                    if (colorNames[i].equals(colorstr, ignoreCase = true)) {
                        temp = colorValues[i]
                        colornum = i
                        break
                    }
                }
            } else {  // RGB or RGBA
                val tokens = colorstr.replace("{", "").replace("}", "").split(',')
                if (tokens.size == 3 || tokens.size == 4) {
                    try {
                        val intTokens = tokens.map { it.trim().toInt() }
                        val red = intTokens[0]
                        val green = intTokens[1]
                        val blue = intTokens[2]
                        val alpha = if (tokens.size == 4) intTokens[3] else 255

                        if (listOf(red, green, blue, alpha).any { it !in 0..255 }) {
                            val message = jlGetStringResource(Res.string.error_prop_color, colorstr)
                            throw JuggleExceptionUser(message)
                        }

                        temp = Color(red, green, blue, alpha)
                    } catch (_: NumberFormatException) {
                        val message = jlGetStringResource(Res.string.error_prop_color, colorstr)
                        throw JuggleExceptionUser(message)
                    }
                } else {
                    val message = jlGetStringResource(Res.string.error_token_count)
                    throw JuggleExceptionUser(message)
                }
            }

            if (temp != null) {
                color = temp
            } else {
                val message = jlGetStringResource(Res.string.error_prop_color, colorstr)
                throw JuggleExceptionUser(message)
            }
        }

        val diamstr = pl.getParameter("diam")
        if (diamstr != null) {
            try {
                val temp = jlParseFiniteDouble(diamstr.trim { it <= ' ' })
                if (temp > 0) {
                    diam = temp
                } else {
                    val message = jlGetStringResource(Res.string.error_prop_diameter)
                    throw JuggleExceptionUser(message)
                }
            } catch (_: NumberFormatException) {
                val message = jlGetStringResource(Res.string.error_number_format, "diam")
                throw JuggleExceptionUser(message)
            }
        }

        val highlightstr = pl.getParameter("highlight")
        if (highlightstr != null) {
            highlight = highlightstr.toBoolean()
        }
    }

    override fun getMax(): Coordinate {
        return Coordinate(diam / 2, 0.0, diam / 2)
    }

    override fun getMin(): Coordinate {
        return Coordinate(-diam / 2, 0.0, -diam / 2)
    }

    override fun getWidth(): Double {
        return diam
    }

    override fun getProp2DImage(zoom: Double, camangle: DoubleArray): ImageBitmap? {
        if (image == null || zoom != lastzoom) {
            // first call or display resized?
            createImage(zoom)
        }
        return image
    }

    override fun getProp2DSize(zoom: Double, camangle: DoubleArray): IntSize? {
        if (size == null || zoom != lastzoom) {
            createImage(zoom)
        }
        return size
    }

    override fun getProp2DCenter(zoom: Double, camangle: DoubleArray): IntSize? {
        if (center == null || zoom != lastzoom) {
            createImage(zoom)
        }
        return center
    }

    override fun getProp2DGrip(zoom: Double, camangle: DoubleArray): IntSize? {
        if (grip == null || zoom != lastzoom) {
            createImage(zoom)
        }
        return grip
    }

    // Refresh the display image and related variables for a given zoom level.

    private fun createImage(zoom: Double) {
        var ballPixelSize = (0.5 + zoom * diam).toInt()
        ballPixelSize = max(ballPixelSize, 1)

        // Create a ball image of diameter ball_pixel_size with a transparent background
        val newImage = ImageBitmap(ballPixelSize + 1, ballPixelSize + 1)
        val canvas = Canvas(newImage)
        val paint = Paint()

        if (highlight) {
            val highlightOvals = ballPixelSize / 1.2f // Number of concentric circles to draw.
            // Make the base color a little darker for contrast
            var currentColor = Color(
                red = color.red / 2.5f,
                green = color.green / 2.5f,
                blue = color.blue / 2.5f,
                alpha = color.alpha
            )

            paint.color = currentColor
            canvas.drawOval(
                rect = Rect(0f, 0f, ballPixelSize.toFloat(), ballPixelSize.toFloat()),
                paint = paint
            )

            // Draw the highlight as a series of concentric, lighter ovals
            for (i in 0 until highlightOvals.toInt()) {
                // Progressively lighten the color towards white
                currentColor = Color(
                    red = min(currentColor.red + (1f / highlightOvals), 1f),
                    green = min(currentColor.green + (1f / highlightOvals), 1f),
                    blue = min(currentColor.blue + (1f / highlightOvals), 1f),
                    alpha = currentColor.alpha
                )
                paint.color = currentColor

                val left = (i / 1.1f)
                val top = (i / 2.5f)
                val size = ballPixelSize - (i * 1.3f)
                canvas.drawOval(Rect(left, top, left + size, top + size), paint)
            }
        } else {
            paint.color = color
            canvas.drawOval(
                rect = Rect(0f, 0f, ballPixelSize.toFloat(), ballPixelSize.toFloat()),
                paint = paint
            )
        }

        image = newImage
        size = IntSize(ballPixelSize, ballPixelSize)
        center = IntSize(ballPixelSize / 2, ballPixelSize / 2)
        grip = IntSize(ballPixelSize / 2, ballPixelSize / 2)
        lastzoom = zoom
    }

    companion object {
        private val COLOR_DEF: Color = Color.Red
        private const val COLORNUM_DEF: Int = 9  // red
        private const val DIAM_DEF: Double = 10.0  // in cm
        private const val HIGHLIGHT_DEF: Boolean = false
    }
}
