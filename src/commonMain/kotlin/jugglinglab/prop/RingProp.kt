//
// RingProp.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.prop

import jugglinglab.generated.resources.*
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor
import jugglinglab.util.ParameterList
import jugglinglab.util.NumberFormatter.jlParseFiniteDouble
import jugglinglab.util.getStringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.BlendMode
import kotlin.math.*

class RingProp : Prop() {
    // set by init()
    private var color: Color = COLOR_DEF
    private var colornum: Int = COLORNUM_DEF
    private var outsideDiam: Double = OUTSIDE_DIAM_DEF
    private var insideDiam: Double = INSIDE_DIAM_DEF
    // recalculated based on zoom and camangle
    private var image: ImageBitmap? = null
    private var size: IntSize? = null
    private var center: IntSize? = null
    private var grip: IntSize? = null
    private var lastzoom: Double = 0.0
    private var lastcamangle: DoubleArray = doubleArrayOf(0.0, 0.0)

    override val type = "Ring"

    override val isColorable = true

    override fun getEditorColor(): Color {
        return color
    }

    override val parameterDescriptors
        get() = listOf(
            ParameterDescriptor(
                "color",
                ParameterDescriptor.TYPE_CHOICE,
                COLOR_NAMES,
                COLOR_NAMES[COLORNUM_DEF],
                COLOR_NAMES[colornum]
            ),
            ParameterDescriptor(
                "outside",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                OUTSIDE_DIAM_DEF,
                outsideDiam
            ),
            ParameterDescriptor(
                "inside",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                INSIDE_DIAM_DEF,
                insideDiam
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
                for (i in COLOR_NAMES.indices) {
                    if (COLOR_NAMES[i].equals(colorstr, ignoreCase = true)) {
                        temp = COLOR_VALS[i]
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
                            val message = getStringResource(Res.string.error_prop_color, colorstr)
                            throw JuggleExceptionUser(message)
                        }

                        temp = Color(red, green, blue, alpha)
                    } catch (_: NumberFormatException) {
                        val message = getStringResource(Res.string.error_prop_color, colorstr)
                        throw JuggleExceptionUser(message)
                    }
                } else {
                    val message = getStringResource(Res.string.error_token_count)
                    throw JuggleExceptionUser(message)
                }
            }

            if (temp != null) {
                color = temp
            } else {
                val message = getStringResource(Res.string.error_prop_color, colorstr)
                throw JuggleExceptionUser(message)
            }
        }

        val outsidestr = pl.getParameter("outside")
        if (outsidestr != null) {
            try {
                val temp = jlParseFiniteDouble(outsidestr)
                if (temp > 0) {
                    outsideDiam = temp
                } else {
                    val message = getStringResource(Res.string.error_prop_diameter)
                    throw JuggleExceptionUser(message)
                }
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "outside")
                throw JuggleExceptionUser(message)
            }
        }

        val insidestr = pl.getParameter("inside")
        if (insidestr != null) {
            try {
                val temp = jlParseFiniteDouble(insidestr)
                if (temp > 0) {
                    insideDiam = temp
                } else {
                    val message = getStringResource(Res.string.error_prop_diameter)
                    throw JuggleExceptionUser(message)
                }
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "inside")
                throw JuggleExceptionUser(message)
            }
        }
    }

    override fun getMax(): Coordinate {
        return Coordinate(outsideDiam / 2, 0.0, outsideDiam / 2)
    }

    override fun getMin(): Coordinate {
        return Coordinate(-outsideDiam / 2, 0.0, -outsideDiam / 2)
    }

    override fun getWidth(): Double {
        // this is for the purposes of default zoom, to fit the juggler into
        // the view area
        return 0.05 * outsideDiam
    }

    override fun getProp2DImage(zoom: Double, camangle: DoubleArray): ImageBitmap? {
        if (image == null || zoom != lastzoom || !camangle.contentEquals(lastcamangle)) {
            createImage(zoom, camangle)
        }
        return image
    }

    override fun getProp2DSize(zoom: Double, camangle: DoubleArray): IntSize? {
        if (size == null || zoom != lastzoom || !camangle.contentEquals(lastcamangle)) {
            createImage(zoom, lastcamangle)
        }
        return size
    }

    override fun getProp2DCenter(zoom: Double, camangle: DoubleArray): IntSize? {
        if (center == null || zoom != lastzoom || !camangle.contentEquals(lastcamangle)) {
            createImage(zoom, lastcamangle)
        }
        return center
    }

    override fun getProp2DGrip(zoom: Double, camangle: DoubleArray): IntSize? {
        if (grip == null || zoom != lastzoom || !camangle.contentEquals(lastcamangle)) {
            createImage(zoom, lastcamangle)
        }
        return grip
    }

    // Refresh the display image and related variables for a given zoom level
    // and camera angle.

    private fun createImage(zoom: Double, camangle: DoubleArray) {
        val outsidePixelDiam = (0.5 + zoom * outsideDiam).toInt()
        val insidePixelDiam = (0.5 + zoom * insideDiam).toInt()

        val c0 = cos(camangle[0])
        val s0 = sin(camangle[0])
        val s1 = sin(camangle[1])

        val width = max(2, (outsidePixelDiam * abs(s0 * s1)).toInt())
        val height = max(2, outsidePixelDiam)

        var insideWidth = (insidePixelDiam * abs(s0 * s1)).toInt()
        if (insideWidth == width) {
            insideWidth -= 2
        }

        var insideHeight = insidePixelDiam
        if (insideHeight == height) {
            insideHeight -= 2
        }

        // The angle of rotation of the ring.
        val term1 = sqrt(c0 * c0 / (1 - s0 * s0 * s1 * s1))
        var angle = if (term1 < 1) acos(term1) else 0.0
        if (c0 * s0 > 0) {
            angle = -angle
        }
        val sa = sin(angle)
        val ca = cos(angle)

        val px = IntArray(POLYSIDES)
        val py = IntArray(POLYSIDES)
        var pxmin = 0
        var pxmax = 0
        var pymin = 0
        var pymax = 0

        for (i in 0..<POLYSIDES) {
            val theta = i.toDouble() * 2 * Math.PI / POLYSIDES.toDouble()
            val x = width.toDouble() * cos(theta) * 0.5
            val y = height.toDouble() * sin(theta) * 0.5
            px[i] = (ca * x - sa * y + 0.5).toInt()
            py[i] = (ca * y + sa * x + 0.5).toInt()
            if (i == 0 || px[i] < pxmin) {
                pxmin = px[i]
            }
            if (i == 0 || px[i] > pxmax) {
                pxmax = px[i]
            }
            if (i == 0 || py[i] < pymin) {
                pymin = py[i]
            }
            if (i == 0 || py[i] > pymax) {
                pymax = py[i]
            }
        }

        val bbwidth = pxmax - pxmin + 1
        val bbheight = pymax - pymin + 1
        size = IntSize(bbwidth, bbheight)

        val resultImage = ImageBitmap(bbwidth, bbheight)
        val canvas = Canvas(resultImage)
        val paint = Paint()  /*.apply {
            isAntiAlias = true
        } */

        // Draw the outer ring polygon
        paint.color = color
        val outerPath = Path()
        for (i in 0..<POLYSIDES) {
            px[i] -= pxmin
            py[i] -= pymin
            if (i == 0) {
                outerPath.moveTo(px[i].toFloat(), py[i].toFloat())
            } else {
                outerPath.lineTo(px[i].toFloat(), py[i].toFloat())
            }
        }
        canvas.drawPath(outerPath, paint)

        // Draw the transparent hole in the center
        paint.color = Color.Transparent
        paint.blendMode = BlendMode.Src

        val innerPath = Path()
        for (i in 0..<POLYSIDES) {
            val theta = i.toDouble() * 2.0 * Math.PI / POLYSIDES.toDouble()
            val x = insideWidth.toDouble() * cos(theta) * 0.5
            val y = insideHeight.toDouble() * sin(theta) * 0.5
            px[i] = (ca * x - sa * y + 0.5).toInt() - pxmin
            py[i] = (ca * y + sa * x + 0.5).toInt() - pymin
            if (i == 0) {
                innerPath.moveTo(px[i].toFloat(), py[i].toFloat())
            } else {
                innerPath.lineTo(px[i].toFloat(), py[i].toFloat())
            }
        }
        canvas.drawPath(innerPath, paint)

        image = resultImage
        center = IntSize(bbwidth / 2, bbheight / 2)

        val gripx = if (s0 < 0) (bbwidth - 1) else 0
        val bbw = sa * sa + ca * ca * abs(s0 * s1)
        val dsq = s0 * s0 * s1 * s1 * ca * ca + sa * sa - bbw * bbw
        var d = if (dsq > 0) sqrt(dsq) else 0.0
        if (c0 > 0) {
            d = -d
        }
        val gripy = (outsidePixelDiam.toDouble() * d).toInt() + bbheight / 2
        grip = IntSize(gripx, gripy)

        lastzoom = zoom
        lastcamangle = doubleArrayOf(camangle[0], camangle[1])
    }

    companion object {
        private val COLOR_DEF: Color = Color.Red
        private const val COLORNUM_DEF: Int = 9 // red
        private const val OUTSIDE_DIAM_DEF: Double = 25.0 // in cm
        private const val INSIDE_DIAM_DEF: Double = 20.0 // in cm
        private const val POLYSIDES: Int = 200
    }
}
