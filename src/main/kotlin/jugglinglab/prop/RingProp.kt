//
// RingProp.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.prop

import jugglinglab.JugglingLab.errorstrings
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.ParameterDescriptor
import jugglinglab.util.ParameterList
import jugglinglab.util.parseDouble
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.text.MessageFormat
import java.util.*
import kotlin.math.*

class RingProp : Prop() {
    private var outsideDiam: Double = OUTSIDE_DIAM_DEF
    private var insideDiam: Double = INSIDE_DIAM_DEF
    private var color: Color = COLOR_DEF
    private var colornum: Int = COLORNUM_DEF
    private var image: BufferedImage? = null
    private var lastzoom: Double = 0.0
    private var lastcamangle: DoubleArray = doubleArrayOf(0.0, 0.0)
    private var size: Dimension? = null
    private var center: Dimension? = null
    private var grip: Dimension? = null
    private lateinit var px: IntArray
    private lateinit var py: IntArray

    override val type = "Ring"

    override fun getEditorColor(): Color {
        return color
    }

    override fun getParameterDescriptors(): Array<ParameterDescriptor> {
        val result = ArrayList<ParameterDescriptor>()

        val range = ArrayList<String>()
        Collections.addAll(range, *COLOR_NAMES)
        result.add(
            ParameterDescriptor(
                "color",
                ParameterDescriptor.TYPE_CHOICE,
                range,
                COLOR_NAMES[COLORNUM_DEF],
                COLOR_NAMES[colornum]
            )
        )
        result.add(
            ParameterDescriptor(
                "outside",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                OUTSIDE_DIAM_DEF,
                outsideDiam
            )
        )
        result.add(
            ParameterDescriptor(
                "inside",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                INSIDE_DIAM_DEF,
                insideDiam
            )
        )
        return result.toTypedArray()
    }

    @Throws(JuggleExceptionUser::class)
    override fun init(st: String?) {
        px = IntArray(POLYSIDES)
        py = IntArray(POLYSIDES)
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
                            "Ring prop color: " + MessageFormat.format(template, *arguments)
                        )
                    }
                    temp = Color(red, green, blue, alpha)
                } else {
                    throw JuggleExceptionUser(
                        "Ring prop color: " + errorstrings.getString("Error_token_count")
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

        val outsidestr = pl.getParameter("outside")
        if (outsidestr != null) {
            try {
                val temp = parseDouble(outsidestr)
                if (temp > 0) {
                    outsideDiam = temp
                } else {
                    throw JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"))
                }
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("diam")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
            }
        }

        val insidestr = pl.getParameter("inside")
        if (insidestr != null) {
            try {
                val temp = parseDouble(insidestr)
                if (temp > 0) {
                    insideDiam = temp
                } else {
                    throw JuggleExceptionUser(errorstrings.getString("Error_prop_diameter"))
                }
            } catch (_: NumberFormatException) {
                val template: String = errorstrings.getString("Error_number_format")
                val arguments = arrayOf<Any?>("diam")
                throw JuggleExceptionUser(MessageFormat.format(template, *arguments))
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
        return 0.05 * outsideDiam
    }

    override fun getProp2DImage(zoom: Double, camangle: DoubleArray): Image? {
        if (image == null || zoom != lastzoom || camangle[0] != lastcamangle[0] ||
            camangle[1] != lastcamangle[1]
        ) {
            // first call or display resized?
            redrawImage(zoom, camangle)
        }
        return image
    }

    override fun getProp2DSize(zoom: Double): Dimension? {
        if (size == null || zoom != lastzoom) {
            // first call or display resized?
            redrawImage(zoom, lastcamangle)
        }
        return size
    }

    override fun getProp2DCenter(zoom: Double): Dimension? {
        if (center == null || zoom != lastzoom) {
            // first call or display resized?
            redrawImage(zoom, lastcamangle)
        }
        return center
    }

    override fun getProp2DGrip(zoom: Double): Dimension? {
        if (grip == null || zoom != lastzoom) {
            // first call or display resized?
            redrawImage(zoom, lastcamangle)
        }
        return grip
    }

    private fun redrawImage(zoom: Double, camangle: DoubleArray) {
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
        size = Dimension(bbwidth, bbheight)

        image = BufferedImage(bbwidth, bbheight, BufferedImage.TYPE_INT_ARGB_PRE)
        val g = image!!.createGraphics()

        /*
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                       RenderingHints.VALUE_ANTIALIAS_ON);
    */
        g.color = color
        for (i in 0..<POLYSIDES) {
            px[i] -= pxmin
            py[i] -= pymin
        }
        g.fillPolygon(px, py, POLYSIDES)

        // make the transparent hole in the center
        g.composite = AlphaComposite.Src
        g.color = Color(1f, 1f, 1f, 0f)

        for (i in 0..<POLYSIDES) {
            val theta = i.toDouble() * 2.0 * Math.PI / POLYSIDES.toDouble()
            val x = insideWidth.toDouble() * cos(theta) * 0.5
            val y = insideHeight.toDouble() * sin(theta) * 0.5
            px[i] = (ca * x - sa * y + 0.5).toInt() - pxmin
            py[i] = (ca * y + sa * x + 0.5).toInt() - pymin
        }
        g.fillPolygon(px, py, POLYSIDES)

        center = Dimension(bbwidth / 2, bbheight / 2)

        val gripx = if (s0 < 0) (bbwidth - 1) else 0
        val bbw = sa * sa + ca * ca * abs(s0 * s1)
        val dsq = s0 * s0 * s1 * s1 * ca * ca + sa * sa - bbw * bbw
        var d = if (dsq > 0) sqrt(dsq) else 0.0
        if (c0 > 0) {
            d = -d
        }
        val gripy = (outsidePixelDiam.toDouble() * d).toInt() + bbheight / 2
        grip = Dimension(gripx, gripy)

        lastzoom = zoom
        lastcamangle = doubleArrayOf(camangle[0], camangle[1])
    }

    companion object {
        private val COLOR_NAMES: Array<String> = arrayOf<String>(
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

        private val COLOR_VALS: Array<Color> = arrayOf<Color>(
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

        private val COLOR_DEF: Color = Color.red
        private const val COLORNUM_DEF: Int = 9 // red
        private const val OUTSIDE_DIAM_DEF: Double = 25.0 // in cm
        private const val INSIDE_DIAM_DEF: Double = 20.0 // in cm
        private const val POLYSIDES: Int = 200
    }
}
