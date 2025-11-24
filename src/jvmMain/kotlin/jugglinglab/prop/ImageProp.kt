//
// ImageProp.kt
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
import java.awt.*
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.max

class ImageProp : Prop() {
    private var url: URL?
    private var image: BufferedImage? = null
    private var scaledImage: BufferedImage? = null
    private var width: Double = 0.0
    private var height: Double = 0.0
    private var size: Dimension? = null
    private var center: Dimension? = null
    private var grip: Dimension? = null
    private var lastZoom = 0.0

    init {
        url = imageUrlDefault ?:
            throw JuggleExceptionUser("ImageProp error: Default image not set")
        loadImage()
        rescaleImage(1.0)
    }

    @Throws(JuggleExceptionUser::class)
    private fun loadImage() {
        try {
            val mt = MediaTracker(object : Component() {})
            image = ImageIO.read(url)
            mt.addImage(image, 0)
            // Try to laod the image
            try {
                mt.waitForAll()
            } catch (_: InterruptedException) {
            }

            if (mt.isErrorAny()) {
                image = null
                // This could also be bad image data, but it is usually a nonexistent file.
                val message = getStringResource(Res.string.error_bad_file)
                throw JuggleExceptionUser(message)
            }

            val aspectRatio = (image!!.height.toDouble()) / (image!!.width.toDouble())
            width = WIDTH_DEF
            height = WIDTH_DEF * aspectRatio
        } catch (_: IOException) {
            val message = getStringResource(Res.string.error_bad_file)
            throw JuggleExceptionUser(message)
        } catch (_: SecurityException) {
            val message = getStringResource(Res.string.error_security_restriction)
            throw JuggleExceptionUser(message)
        }
    }

    private fun rescaleImage(zoom: Double) {
        val imagePixelWidth = max((0.5 + zoom * width).toInt(), 1)
        val imagePixelHeight = max((0.5 + zoom * height).toInt(), 1)
        size = Dimension(imagePixelWidth, imagePixelHeight)
        center = Dimension(imagePixelWidth / 2, imagePixelHeight / 2)

        val offsetx = imagePixelWidth / 2
        val offsety = imagePixelHeight
        grip = Dimension(offsetx, offsety)

        lastZoom = zoom

        scaledImage = BufferedImage(imagePixelWidth, imagePixelHeight, image!!.type)
        val g = scaledImage!!.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g.drawImage(
            image,
            0,
            0,
            imagePixelWidth,
            imagePixelHeight,
            0,
            0,
            image!!.width,
            image!!.height,
            null
        )
        g.dispose()
    }

    override val type = "Image"

    override val isColorable = false

    override fun getEditorColor(): Color {
        // The color that shows up in the visual editor
        // We could try to get an average color for the image
        return Color.white
    }

    override fun getParameterDescriptors(): List<ParameterDescriptor> {
        return listOf(
            ParameterDescriptor(
                "image",
                ParameterDescriptor.TYPE_ICON,
                null,
                imageUrlDefault,
                url
            ),
            ParameterDescriptor(
                "width",
                ParameterDescriptor.TYPE_FLOAT,
                null,
                WIDTH_DEF,
                width
            ),
        )
    }

    @Throws(JuggleExceptionUser::class)
    override fun init(st: String?) {
        if (st == null) {
            return
        }
        val pl = ParameterList(st)

        val sourcestr = pl.getParameter("image")
        if (sourcestr != null) {
            try {
                url = URI(sourcestr).toURL()
                loadImage()
            } catch (_: URISyntaxException) {
                val message = getStringResource(Res.string.error_malformed_url)
                throw JuggleExceptionUser(message)
            } catch (_: MalformedURLException) {
                val message = getStringResource(Res.string.error_malformed_url)
                throw JuggleExceptionUser(message)
            }
        }

        val widthstr = pl.getParameter("width")
        if (widthstr != null) {
            try {
                val temp = jlParseFiniteDouble(widthstr)
                if (temp > 0) {
                    width = temp
                    val aspectRatio = (image!!.getHeight(null).toDouble()) / (image!!.getWidth(null).toDouble())
                    height = width * aspectRatio
                } else {
                    throw NumberFormatException()
                }
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "width")
                throw JuggleExceptionUser(message)
            }
        }
    }

    override fun getProp2DImage(zoom: Double, camangle: DoubleArray): Image? {
        if (zoom != lastZoom) {
            rescaleImage(zoom)
        }
        return scaledImage
    }

    override fun getMax(): Coordinate {
        return Coordinate(width / 2, 0.0, width)
    }

    override fun getMin(): Coordinate {
        return Coordinate(-width / 2, 0.0, 0.0)
    }

    override fun getWidth(): Double {
        return width
    }

    override fun getProp2DSize(zoom: Double): Dimension? {
        if (size == null || zoom != lastZoom) {
            rescaleImage(zoom)
        }
        return size
    }

    override fun getProp2DCenter(zoom: Double): Dimension? {
        if (center == null || zoom != lastZoom) {
            rescaleImage(zoom)
        }
        return center
    }

    override fun getProp2DGrip(zoom: Double): Dimension? {
        if (grip == null || zoom != lastZoom) {
            rescaleImage(zoom)
        }
        return grip
    }

    companion object {
        private const val WIDTH_DEF: Double = 10.0  // in centimeters
        private var imageUrlDefault: URL? = ImageProp::class.java.getResource("/ball.png")
    }
}
