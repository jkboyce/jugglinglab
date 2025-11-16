//
// BallProp.java
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.prop

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor
import jugglinglab.util.ParameterList
import jugglinglab.util.jlParseFiniteDouble
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.text.MessageFormat
import java.util.*
import kotlin.math.max
import kotlin.math.min

class BallProp : Prop() {
    private var diam: Double = DIAM_DEF // diameter, in cm
    private var color: Color = COLOR_DEF
    private var colornum: Int = COLORNUM_DEF
    private var highlight: Boolean = HIGHLIGHT_DEF

    private var ballimage: BufferedImage? = null
    private var lastzoom: Double = 0.0
    private var size: Dimension? = null
    private var center: Dimension? = null
    private var grip: Dimension? = null

    override val type = "Ball"

    override fun getEditorColor(): Color {
        return color
    }

    override fun getParameterDescriptors(): Array<ParameterDescriptor> {
        val result = ArrayList<ParameterDescriptor>()
        val colorRange = ArrayList<String>()
        Collections.addAll(colorRange, *COLOR_NAMES)
        result.add(
            ParameterDescriptor(
                "color",
                ParameterDescriptor.TYPE_CHOICE,
                colorRange,
                COLOR_NAMES[COLORNUM_DEF],
                COLOR_NAMES[colornum]
            )
        )
        result.add(
            ParameterDescriptor(
                "diam",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                DIAM_DEF,
                diam
            )
        )
        result.add(
            ParameterDescriptor(
                "highlight",
                ParameterDescriptor.TYPE_BOOLEAN,
                null,
                HIGHLIGHT_DEF,
                highlight
            )
        )

        return result.toTypedArray()
    }

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
                // delete the '{' and '}' characters first
                var str: String? = colorstr
                var pos: Int
                while ((str!!.indexOf('{').also { pos = it }) >= 0) {
                    str = str.take(pos) + str.substring(pos + 1)
                }
                while ((str!!.indexOf('}').also { pos = it }) >= 0) {
                    str = str.take(pos) + str.substring(pos + 1)
                }

                val st2 = StringTokenizer(str, ",", false)
                val tokens = st2.countTokens()

                if (tokens == 3 || tokens == 4) {
                    val red: Int
                    val green: Int
                    val blue: Int
                    var alpha = 255
                    var token: String? = null
                    try {
                        token = st2.nextToken().trim { it <= ' ' }
                        red = token.toInt()
                        token = st2.nextToken().trim { it <= ' ' }
                        green = token.toInt()
                        token = st2.nextToken().trim { it <= ' ' }
                        blue = token.toInt()
                        if (tokens == 4) {
                            token = st2.nextToken().trim { it <= ' ' }
                            alpha = token.toInt()
                        }
                    } catch (_: NumberFormatException) {
                        val template: String = errorstrings.getString("Error_number_format")
                        val arguments = arrayOf<Any?>(token)
                        throw JuggleExceptionUser(
                            "Ball prop color: " + MessageFormat.format(template, *arguments)
                        )
                    }
                    temp = Color(red, green, blue, alpha)
                } else {
                    throw JuggleExceptionUser(
                        "Ball prop color: " + errorstrings.getString("Error_token_count")
                    )
                }
            }

            if (temp != null) {
                color = temp
            } else {
                val template: String = errorstrings.getString("Error_prop_color")
                val arguments = arrayOf<Any?>(colorstr)
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }

        val diamstr = pl.getParameter("diam")
        if (diamstr != null) {
            try {
                val temp = jlParseFiniteDouble(diamstr.trim { it <= ' ' })
                if (temp > 0) {
                    diam = temp
                } else {
                    throw JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"))
                }
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("diam")
                throw JuggleExceptionUser(
                    "Ball prop diameter: " + MessageFormat.format(template, *arguments)
                )
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

    override fun getProp2DImage(zoom: Double, camangle: DoubleArray): Image? {
        if (ballimage == null || zoom != lastzoom) {
            // first call or display resized?
            recalc2D(zoom)
        }
        return ballimage
    }

    override fun getProp2DSize(zoom: Double): Dimension? {
        if (size == null || zoom != lastzoom) {
            // first call or display resized?
            recalc2D(zoom)
        }
        return size
    }

    override fun getProp2DCenter(zoom: Double): Dimension? {
        if (center == null || zoom != lastzoom) {
            recalc2D(zoom)
        }
        return center
    }

    override fun getProp2DGrip(zoom: Double): Dimension? {
        if (grip == null || zoom != lastzoom) {
            // first call or display resized?
            recalc2D(zoom)
        }
        return grip
    }

    private fun recalc2D(zoom: Double) {
        var ballPixelSize = (0.5 + zoom * diam).toInt()
        ballPixelSize = max(ballPixelSize, 1)

        // create a ball image of diameter ball_pixel_size, and transparent background
        ballimage = BufferedImage(
            ballPixelSize + 1, ballPixelSize + 1, BufferedImage.TYPE_INT_ARGB_PRE
        )
        val ballg = ballimage!!.createGraphics()

        /*
        ballg.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
        */
        if (highlight) {
            val highlightOvals = ballPixelSize / 1.2f // Number of concentric circles to draw.
            val rgb = FloatArray(4)
            rgb[0] = color.red.toFloat() / 255f
            rgb[1] = color.green.toFloat() / 255f
            rgb[2] = color.blue.toFloat() / 255f
            rgb[3] = color.alpha.toFloat() / 255f

            // make the color a little darker so that there is some contrast
            for (i in 0..2) {
                rgb[i] = rgb[i] / 2.5f
            }

            ballg.color = Color(rgb[0], rgb[1], rgb[2], rgb[3])
            ballg.fillOval(0, 0, ballPixelSize, ballPixelSize) // Full sized ellipse.

            // draw the highlight on the ball
            var i = 0
            while (i < highlightOvals) {
                // Calculate the new color
                for (j in 0..2) {
                    rgb[j] = min(rgb[j] + (1f / highlightOvals), 1f)
                }
                ballg.color = Color(rgb[0], rgb[1], rgb[2], rgb[3])
                ballg.fillOval(
                    (i / 1.1).toInt(),
                    (i / 2.5).toInt(),  // literals control how fast highlight
                    // moves right and down respectively.
                    ballPixelSize - (i * 1.3).toInt(),  // these control how fast the
                    ballPixelSize - (i * 1.3).toInt()
                ) // highlight converges to a point.
                i++
            }
        } else {
            ballg.color = color
            ballg.fillOval(0, 0, ballPixelSize, ballPixelSize)
        }

        size = Dimension(ballPixelSize, ballPixelSize)
        center = Dimension(ballPixelSize / 2, ballPixelSize / 2)
        grip = Dimension(ballPixelSize / 2, ballPixelSize / 2)

        lastzoom = zoom
    }

    companion object {
        private val COLOR_DEF: Color = Color.red
        private const val COLORNUM_DEF: Int = 9  // red
        private const val DIAM_DEF: Double = 10.0  // in cm
        private const val HIGHLIGHT_DEF: Boolean = false
    }
}
