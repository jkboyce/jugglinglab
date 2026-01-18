//
// FrameDrawer.kt
//
// This class draws individual frames of juggling. It is independent of JPanel
// or other GUI elements so that it can be used in headless mode, e.g., as when
// creating an animated GIF from the command line.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

@file:Suppress("KotlinConstantConditions")

package jugglinglab.renderer

import jugglinglab.core.AnimationPrefs
import jugglinglab.core.Constants
import jugglinglab.core.PatternAnimationState
import jugglinglab.jml.JMLEvent
import jugglinglab.util.Coordinate
import jugglinglab.util.Coordinate.Companion.add
import jugglinglab.util.Coordinate.Companion.max
import jugglinglab.util.Coordinate.Companion.min
import jugglinglab.util.JuggleExceptionInternal
import java.awt.*
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.OutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOInvalidTreeException
import javax.imageio.metadata.IIOMetadata
import javax.imageio.metadata.IIOMetadataNode
import javax.imageio.stream.ImageOutputStream
import javax.imageio.stream.MemoryCacheImageOutputStream
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.max

class FrameDrawer(
    val state: PatternAnimationState
) {
    var ren1: Renderer = Renderer2D()
    var ren2: Renderer = Renderer2D()
    private var overallMax: Coordinate? = null
    private var overallMin: Coordinate? = null

    var simIntervalSecs: Double = 0.0
        private set
    var realIntervalMillis: Long = 0
        private set

    init {
        // sync the renderers to the initial pattern
        changeAnimatorPattern()
        changeAnimatorCameraAngle()
    }

    // Propagate a change in pattern to update the renderers and
    // other data structures. Rescale the drawing so that the pattern
    // and key parts of the juggler are visible.

    fun changeAnimatorPattern() {
        val pattern = state.pattern
        val sg = (state.prefs.showGround == AnimationPrefs.GROUND_ON ||
            (state.prefs.showGround == AnimationPrefs.GROUND_AUTO && pattern.isBouncePattern))
        ren1.setPattern(pattern)
        ren1.setGround(sg)
        if (state.prefs.stereo) {
            ren2.setPattern(pattern)
            ren2.setGround(sg)
        }

        if (state.fitToFrame) {
            findMaxMin()
            syncRenderersToSize()
        }

        // figure out timing constants; this in effect adjusts fps to get an integer
        // number of frames in one repetition of the pattern
        val numFrames = (0.5 + (pattern.loopEndTime - pattern.loopStartTime) * state.prefs.slowdown * state.prefs.fps).toInt()
        simIntervalSecs = (pattern.loopEndTime - pattern.loopStartTime) / numFrames
        realIntervalMillis = (1000.0 * simIntervalSecs * state.prefs.slowdown).toLong()
    }

    // Propagate a change in camera angle to the renderers.

    fun changeAnimatorCameraAngle() {
        val ca = doubleArrayOf(state.cameraAngle[0], state.cameraAngle[1])
        while (ca[0] < 0) {
            ca[0] += 2 * Math.PI
        }
        while (ca[0] >= 2 * Math.PI) {
            ca[0] -= 2 * Math.PI
        }

        if (state.prefs.stereo) {
            ren1.cameraAngle = doubleArrayOf(ca[0] - STEREO_SEPARATION_RADIANS / 2, ca[1])
            ren2.cameraAngle = doubleArrayOf(ca[0] + STEREO_SEPARATION_RADIANS / 2, ca[1])
        } else {
            ren1.cameraAngle = ca
        }
    }

    // Draw the frame of juggling

    @Suppress("UnnecessaryVariable")
    @Throws(JuggleExceptionInternal::class)
    fun drawFrame(simTime: Double, g: Graphics, drawAxes: Boolean, drawBackground: Boolean) {
        if (drawBackground) {
            drawBackground(g)
        }

        if (state.prefs.stereo) {
            ren1.drawFrame(
                simTime,
                state.propForPath,
                state.prefs.hideJugglers,
                g.create(0, 0, state.prefs.width / 2, state.prefs.height)
            )
            ren2.drawFrame(
                simTime,
                state.propForPath,
                state.prefs.hideJugglers,
                g.create(state.prefs.width / 2, 0, state.prefs.width / 2, state.prefs.height)
            )
        } else {
            ren1.drawFrame(simTime, state.propForPath, state.prefs.hideJugglers, g)
        }

        if (drawAxes) {
            if (g is Graphics2D) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            }

            for (i in 0..<(if (state.prefs.stereo) 2 else 1)) {
                val ren = if (i == 0) ren1 else ren2
                val ca = ren.cameraAngle
                val theta = ca[0]
                val phi = ca[1]

                val xya = 30.0
                val xyb = xya * cos(phi)
                val zlen = xya * sin(phi)
                val cx = 38 + i * (state.prefs.width / 2)
                val cy = 45
                val xx = cx + (0.5 - xya * cos(theta)).toInt()
                val xy = cy + (0.5 + xyb * sin(theta)).toInt()
                val yx = cx + (0.5 + xya * sin(theta)).toInt()
                val yy = cy + (0.5 + xyb * cos(theta)).toInt()
                val zx = cx
                val zy = cy - (0.5 + zlen).toInt()

                g.color = Color.green
                g.drawLine(cx, cy, xx, xy)
                g.drawLine(cx, cy, yx, yy)
                g.drawLine(cx, cy, zx, zy)
                g.fillOval(xx - 2, xy - 2, 5, 5)
                g.fillOval(yx - 2, yy - 2, 5, 5)
                g.fillOval(zx - 2, zy - 2, 5, 5)
                g.drawString("x", xx - 2, xy - 4)
                g.drawString("y", yx - 2, yy - 4)
                g.drawString("z", zx - 2, zy - 4)
            }
        }
    }

    fun drawBackground(g: Graphics) {
        g.color = ren1.getBackground()
        g.fillRect(0, 0, state.prefs.width, state.prefs.height)
    }

    var zoomLevel: Double
        get() = ren1.getZoomLevel()
        set(z) {
            ren1.setZoomLevel(z)
            if (state.prefs.stereo) {
                ren2.setZoomLevel(z)
            }
        }

    // Find the overall bounding box of the juggler and pattern, in real-space
    // (centimeters) global coordinates. Output is the variables `overallmin`
    // and `overallmax`, which determine a bounding box.

    private fun findMaxMin() {
        val pattern = state.pattern

        // Step 1: Work out a bounding box that contains all paths through space
        // for the pattern, including the props
        var patternmax: Coordinate? = null
        var patternmin: Coordinate? = null
        for (i in 1..pattern.numberOfPaths) {
            patternmax = max(patternmax, pattern.layout.getPathMax(i))
            patternmin = min(patternmin, pattern.layout.getPathMin(i))

            if (Constants.DEBUG_LAYOUT) {
                println("Data from Animator.findMaxMin():")
                println("Path max $i = " + pattern.layout.getPathMax(i))
                println("Path min $i = " + pattern.layout.getPathMin(i))
            }
        }

        var propmax: Coordinate? = null
        var propmin: Coordinate? = null
        for (i in 1..pattern.numberOfProps) {
            propmax = max(propmax, pattern.getProp(i).getMax())
            propmin = min(propmin, pattern.getProp(i).getMin())
        }

        // Make sure props are entirely visible along all paths. In principle
        // not all props go on all paths so this could be done more carefully.
        if (patternmax != null && patternmin != null) {
            patternmax = add(patternmax, propmax)
            patternmin = add(patternmin, propmin)
        }

        // Step 2: Work out a bounding box that contains the hands at all times,
        // factoring in the physical extent of the hands.
        var handmax: Coordinate? = null
        var handmin: Coordinate? = null
        for (i in 1..pattern.numberOfJugglers) {
            handmax = max(handmax, pattern.layout.getHandMax(i, JMLEvent.LEFT_HAND))
            handmin = min(handmin, pattern.layout.getHandMin(i, JMLEvent.LEFT_HAND))
            handmax = max(handmax, pattern.layout.getHandMax(i, JMLEvent.RIGHT_HAND))
            handmin = min(handmin, pattern.layout.getHandMin(i, JMLEvent.RIGHT_HAND))

            if (Constants.DEBUG_LAYOUT) {
                println("Data from Animator.findMaxMin():")
                println("Hand max $i left = " + pattern.layout.getHandMax(i, JMLEvent.LEFT_HAND))
                println("Hand min $i left = " + pattern.layout.getHandMin(i, JMLEvent.LEFT_HAND))
                println("Hand max $i right = " + pattern.layout.getHandMax(i, JMLEvent.RIGHT_HAND))
                println("Hand min $i right = " + pattern.layout.getHandMin(i, JMLEvent.RIGHT_HAND))
            }
        }

        // The renderer's hand window is in local coordinates. We don't know
        // the juggler's rotation angle when `handmax` and `handmin` are
        // achieved. So we create a bounding box that contains the hand
        // regardless of rotation angle.
        val hwmax = ren1.handWindowMax
        val hwmin = ren1.handWindowMin
        hwmax.x = max(
            max(abs(hwmax.x), abs(hwmin.x)),
            max(abs(hwmax.y), abs(hwmin.y))
        )
        hwmin.x = -hwmax.x
        hwmax.y = hwmax.x
        hwmin.y = hwmin.x

        // make sure hands are entirely visible
        handmax = add(handmax, hwmax)
        handmin = add(handmin, hwmin)

        // Step 3: Find a bounding box that contains the juggler's body
        // at all times, incorporating any juggler movements as well as the
        // physical extent of the juggler's body.
        val jwmax = ren1.jugglerWindowMax
        val jwmin = ren1.jugglerWindowMin

        // Step 4: Combine the pattern, hand, and juggler bounding boxes into
        // an overall bounding box.
        overallMax = max(patternmax, max(handmax, jwmax))
        overallMin = min(patternmin, min(handmin, jwmin))

        if (Constants.DEBUG_LAYOUT) {
            println("Data from Animator.findMaxMin():")
            println("Hand max = $handmax")
            println("Hand min = $handmin")
            println("Prop max = $propmax")
            println("Prop min = $propmin")
            println("Pattern max = $patternmax")
            println("Pattern min = $patternmin")
            println("Juggler max = $jwmax")
            println("Juggler min = $jwmin")
            println("Overall max = $overallMax")
            println("Overall min = $overallMin")
        }
    }

    // Pass the global bounding box, and the viewport pixel size, to the
    // renderer so it can calculate a zoom factor.

    private fun syncRenderersToSize() {
        val d = Dimension(state.prefs.width, state.prefs.height)
        if (state.prefs.stereo) {
            d.width /= 2
            ren1.initDisplay(d, state.prefs.borderPixels, overallMax!!, overallMin!!)
            ren2.initDisplay(d, state.prefs.borderPixels, overallMax!!, overallMin!!)
        } else {
            ren1.initDisplay(d, state.prefs.borderPixels, overallMax!!, overallMin!!)
        }
    }

    // Output a GIF of the pattern to OutputStream `os`.
    //
    // Optional parameter `wgm` monitors the progress and allows the user to
    // cancel. `fps` is the target frames per second for the GIF.
    //
    // Note: The GIF header specifies the delay time between frames in terms of
    // hundredths of a second. This is an integer quantity, so only `fps` values
    // like 50, 33 1/3, 25, 20, ... are precisely achieveable.

    @Throws(IOException::class, JuggleExceptionInternal::class)
    fun writeGIF(os: OutputStream, wgm: WriteGIFMonitor?, fps: Double) {
        val pattern = state.pattern
        val iw = ImageIO.getImageWritersByFormatName("gif").next()
        val ios: ImageOutputStream = MemoryCacheImageOutputStream(os)
        iw.setOutput(ios)
        iw.prepareWriteSequence(null)

        val image = BufferedImage(state.prefs.width, state.prefs.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        // antialiased rendering creates too many distinct color values for
        // GIF to handle well
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

        // reset prop assignments to generate an identical GIF every time
        state.update(propForPath = state.initialPropForPath())

        // our own local versions of these three fps-related quantities
        val gifNumFrames = (0.5 + (pattern.loopEndTime - pattern.loopStartTime) * state.prefs.slowdown * fps).toInt()
        val gifSimIntervalSecs = (pattern.loopEndTime - pattern.loopStartTime) / gifNumFrames
        val gifRealIntervalMillis = (1000.0 * gifSimIntervalSecs * state.prefs.slowdown).toLong().toDouble()

        val totalframes = pattern.periodWithProps * gifNumFrames
        var framecount = 0

        // delay time is embedded in GIF header in terms of hundredths of a second
        val delayTime = (0.5 + gifRealIntervalMillis / 10).toInt().toString()
        val iwp = iw.defaultWriteParam
        var metadata: IIOMetadata? = null

        repeat (pattern.periodWithProps) {
            var time = pattern.loopStartTime

            repeat (gifNumFrames) {
                drawFrame(time, g, drawAxes = false, drawBackground = true)

                // after the second frame all subsequent frames have identical metadata
                if (framecount < 2) {
                    metadata = iw.getDefaultImageMetadata(ImageTypeSpecifier(image), iwp)
                    configureGIFMetadata(metadata, delayTime, framecount)
                }

                val ii = IIOImage(image, null, metadata)
                iw.writeToSequence(ii, null as ImageWriteParam?)

                time += gifSimIntervalSecs
                ++framecount

                if (wgm != null) {
                    wgm.update(framecount, totalframes)
                    if (wgm.isCanceled) {
                        ios.close()
                        os.close()
                        return
                    }
                }
            }

            state.advancePropForPath()
        }

        g.dispose()
        iw.endWriteSequence()
        ios.close()
        os.close()
    }

    interface WriteGIFMonitor {
        // callback method invoked when a processing step is completed
        fun update(step: Int, stepsTotal: Int)

        // callback method should return true when user wants to cancel
        val isCanceled: Boolean
    }

    companion object {
        private const val STEREO_SEPARATION_RADIANS: Double = 0.10

        // Helper method for writeGIF() above.

        private fun configureGIFMetadata(meta: IIOMetadata, delayTime: String?, imageIndex: Int) {
            val metaFormat = meta.getNativeMetadataFormatName()
            require("javax_imageio_gif_image_1.0" == metaFormat) {
                "Unfamiliar gif metadata format: $metaFormat"
            }
            val root = meta.getAsTree(metaFormat)

            // find the GraphicControlExtension node
            var child = root.firstChild
            while (child != null) {
                if ("GraphicControlExtension" == child.nodeName) break
                child = child.nextSibling
            }

            val gce = child as IIOMetadataNode
            gce.setAttribute("userInputFlag", "FALSE")
            gce.setAttribute("delayTime", delayTime)

            // only the first node needs the ApplicationExtensions node
            if (imageIndex == 0) {
                val aes = IIOMetadataNode("ApplicationExtensions")
                val ae = IIOMetadataNode("ApplicationExtension")
                ae.setAttribute("applicationID", "NETSCAPE")
                ae.setAttribute("authenticationCode", "2.0")
                val uo = byteArrayOf(
                        // last two bytes is an unsigned short (little endian) that
                        // indicates the the number of times to loop. 0 means loop forever.
                        0x1, 0x0, 0x0
                    )
                ae.userObject = uo
                aes.appendChild(ae)
                root.appendChild(aes)
            }

            try {
                meta.setFromTree(metaFormat, root)
            } catch (e: IIOInvalidTreeException) {
                // shouldn't happen
                throw Error(e)
            }
        }
    }
}
