//
// ImageProp.kt
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.prop

import jugglinglab.generated.resources.*
import jugglinglab.util.Coordinate
import jugglinglab.util.JuggleExceptionUser
import jugglinglab.util.JuggleExceptionInternal
import jugglinglab.util.ParameterDescriptor
import jugglinglab.util.ParameterList
import jugglinglab.util.NumberFormatter.jlParseFiniteDouble
import jugglinglab.util.getStringResource
import jugglinglab.util.getImageResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Paint
import kotlin.math.max

class ImageProp : Prop() {
    // set by constructor and init()
    private var imageSource: String = IMAGE_DEF
    private var baseImage: ImageBitmap
    private var width: Double = WIDTH_DEF
    // recalculated based on zoom
    private var image: ImageBitmap? = null
    private var size: IntSize? = null
    private var center: IntSize? = null
    private var grip: IntSize? = null
    private var lastZoom = 0.0

    init {
        try {
            baseImage = getImageResource(IMAGE_DEF)
        } catch (e: Exception) {
            throw JuggleExceptionInternal("ImageProp init error (${e.message})")
        }
    }

    override val type = "Image"

    override val isColorable = false

    override fun getEditorColor(): Color {
        return Color.White
    }

    override fun getParameterDescriptors(): List<ParameterDescriptor> {
        return listOf(
            ParameterDescriptor(
                "image",
                ParameterDescriptor.TYPE_ICON,
                null,
                IMAGE_DEF,
                imageSource
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
            baseImage = getImageResource(sourcestr)
            imageSource = sourcestr
        }

        val widthstr = pl.getParameter("width")
        if (widthstr != null) {
            try {
                val temp = jlParseFiniteDouble(widthstr)
                if (temp > 0) {
                    width = temp
                } else {
                    throw NumberFormatException()
                }
            } catch (_: NumberFormatException) {
                val message = getStringResource(Res.string.error_number_format, "width")
                throw JuggleExceptionUser(message)
            }
        }
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

    override fun getProp2DImage(zoom: Double, camangle: DoubleArray): ImageBitmap? {
        if (zoom != lastZoom) {
            createImage(zoom)
        }
        return image
    }

    override fun getProp2DSize(zoom: Double, camangle: DoubleArray): IntSize? {
        if (size == null || zoom != lastZoom) {
            createImage(zoom)
        }
        return size
    }

    override fun getProp2DCenter(zoom: Double, camangle: DoubleArray): IntSize? {
        if (center == null || zoom != lastZoom) {
            createImage(zoom)
        }
        return center
    }

    override fun getProp2DGrip(zoom: Double, camangle: DoubleArray): IntSize? {
        if (grip == null || zoom != lastZoom) {
            createImage(zoom)
        }
        return grip
    }

    // Refresh the display image and related variables for a given zoom level.

    private fun createImage(zoom: Double) {
        val aspectRatio = (baseImage.height.toDouble()) / (baseImage.width.toDouble())
        val height = width * aspectRatio
        val imagePixelWidth = max((0.5 + zoom * width).toInt(), 1)
        val imagePixelHeight = max((0.5 + zoom * height).toInt(), 1)
        size = IntSize(imagePixelWidth, imagePixelHeight)
        center = IntSize(imagePixelWidth / 2, imagePixelHeight / 2)
        grip = IntSize(imagePixelWidth / 2, imagePixelHeight)
        lastZoom = zoom

        // Create a new ImageBitmap and use a Canvas to draw the scaled original onto it
        val originalImage = baseImage
        val newImage = ImageBitmap(imagePixelWidth, imagePixelHeight)
        val canvas = Canvas(newImage)
        val paint = Paint().apply {
            // Use medium quality for smooth scaling, similar to bilinear interpolation.
            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium
        }
        canvas.drawImageRect(
            image = originalImage,
            dstSize = IntSize(imagePixelWidth, imagePixelHeight),
            paint = paint
        )
        image = newImage
    }

    companion object {
        const val IMAGE_DEF: String = "ball.png"
        const val WIDTH_DEF: Double = 10.0  // centimeters, in juggler space
    }
}
