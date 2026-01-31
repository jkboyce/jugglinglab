//
// AnimationGifWriter.kt
//
// Utility class for writing GIFs on a thread separate from the EDT.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.util

import jugglinglab.composeapp.generated.resources.*
import jugglinglab.core.PatternAnimationState
import jugglinglab.renderer.FrameDrawer
import java.awt.Component
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
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
import javax.swing.ProgressMonitor
import javax.swing.SwingUtilities

class AnimationGifWriter(
    val gifState: PatternAnimationState,
    val file: File,
    val parent: Component? = null,
    val cleanup: Runnable? = null
) : Thread() {
    init {
        setPriority(MIN_PRIORITY)
        start()
    }

    override fun run() {
        try {
            val wgm = if (parent == null) {
                null
            } else {
                val pm = ProgressMonitor(
                    parent, jlGetStringResource(Res.string.gui_saving_animated_gif), "", 0, 1
                ).apply {
                    millisToPopup = 200
                }

                object : WriteGifMonitor {
                    override fun update(step: Int, stepsTotal: Int) {
                        SwingUtilities.invokeLater {
                            pm.setMaximum(stepsTotal)
                            pm.setProgress(step)
                        }
                    }

                    override val isCanceled: Boolean
                        get() = (pm.isCanceled() || interrupted())
                }
            }

            writeGif(FileOutputStream(file), wgm)
        } catch (_: IOException) {
            val message = jlGetStringResource(Res.string.error_writing_file, file.toString())
            jlHandleUserException(parent, message)
        } catch (jei: JuggleExceptionInternal) {
            jlHandleFatalException(jei)
        } catch (e: Throwable) {
            jlHandleFatalException(JuggleExceptionInternal(e, gifState.pattern))
        } finally {
            if (cleanup != null) {
                SwingUtilities.invokeLater(cleanup)
            }
        }
    }

    // Output a GIF of the pattern to OutputStream `os`.
    //
    // Optional parameter `wgm` monitors the progress and allows the user to
    // cancel. `fps` is the target frames per second for the GIF.
    //
    // Note: The GIF header specifies the delay time between frames in terms of
    // hundredths of a second. This is an integer quantity, so only `fps` values
    // like 50, 33 1/3, 25, 20, ... are precisely achievable.

    @Throws(IOException::class, JuggleExceptionInternal::class)
    private fun writeGif(os: OutputStream, wgm: WriteGifMonitor?) {
        val pattern = gifState.pattern
        val fps = gifState.prefs.fps
        // reset prop assignments to generate an identical GIF every time
        gifState.update(propForPath = pattern.initialPropForPath)

        // quantities to help with looping through the animation
        val gifNumFrames = (0.5 + (pattern.loopEndTime - pattern.loopStartTime) * gifState.prefs.slowdown * fps).toInt()
        val gifSimIntervalSecs = (pattern.loopEndTime - pattern.loopStartTime) / gifNumFrames
        val gifRealIntervalMillis = (1000.0 * gifSimIntervalSecs * gifState.prefs.slowdown).toLong().toDouble()
        // delay time is embedded in GIF header in hundredths of a second
        val delayTime = (0.5 + gifRealIntervalMillis / 10).toInt().toString()
        val totalFrames = pattern.periodWithProps * gifNumFrames
        var currentFrame = 0

        // Java GIF encoder
        val ios: ImageOutputStream = MemoryCacheImageOutputStream(os)
        val iw = ImageIO.getImageWritersByFormatName("gif").next().apply {
            setOutput(ios)
            prepareWriteSequence(null)
        }
        val iwp = iw.defaultWriteParam
        var metadata: IIOMetadata? = null

        // BufferedImage that we'll be drawing into
        val image = BufferedImage(gifState.prefs.width, gifState.prefs.height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        // antialiased rendering creates too many distinct color values for
        // GIF to handle well
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF)

        // FrameDrawer that does the actual drawing
        val drawer = FrameDrawer(gifState)

        repeat(pattern.periodWithProps) {
            var time = pattern.loopStartTime

            repeat(gifNumFrames) {
                drawer.drawFrame(time, g, drawAxes = false, drawBackground = true)

                // after the second frame all subsequent frames have identical metadata
                if (currentFrame < 2) {
                    metadata = iw.getDefaultImageMetadata(ImageTypeSpecifier(image), iwp)
                    configureGifMetadata(metadata, delayTime, currentFrame)
                }

                val ii = IIOImage(image, null, metadata)
                iw.writeToSequence(ii, null as ImageWriteParam?)

                time += gifSimIntervalSecs
                ++currentFrame

                if (wgm != null) {
                    wgm.update(currentFrame, totalFrames)
                    if (wgm.isCanceled) {
                        ios.close()
                        os.close()
                        return
                    }
                }
            }

            gifState.advancePropForPath()
        }

        g.dispose()
        iw.endWriteSequence()
        ios.close()
        os.close()
    }

    // Helper for writeGIF()

    companion object {
        private fun configureGifMetadata(meta: IIOMetadata, delayTime: String?, imageIndex: Int) {
            val metaFormat = meta.getNativeMetadataFormatName()
            require(metaFormat == "javax_imageio_gif_image_1.0") {
                "Unfamiliar gif metadata format: $metaFormat"
            }
            val root = meta.getAsTree(metaFormat)

            // find the GraphicControlExtension node
            var child = root.firstChild
            while (child != null) {
                if (child.nodeName == "GraphicControlExtension") break
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

interface WriteGifMonitor {
    // callback method invoked when a processing step is completed
    fun update(step: Int, stepsTotal: Int)

    // callback method should return true when user wants to cancel
    val isCanceled: Boolean
}
