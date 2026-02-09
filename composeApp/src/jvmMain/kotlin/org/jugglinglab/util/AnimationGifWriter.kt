//
// AnimationGifWriter.kt
//
// Class for writing animated GIFs to a file. It does the processing in a
// background thread. If parameter `parent` is specified, a progress indicator
// is shown in front of that component.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.util

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.ui.AnimationView
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import androidx.compose.runtime.snapshots.Snapshot
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skiko.toBufferedImage
import java.awt.Component
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
import kotlin.math.roundToInt
import kotlin.math.max

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

        // (integer) inter-frame delay time, in hundredths of a second
        val frameDurationHundredths = max((100.0 / gifState.prefs.fps).roundToInt(), 1)
        val frameDurationString = frameDurationHundredths.toString()

        // adjust inter-frame sim time so that an exact number of frames fit
        // within the animation loop
        val loopDuration = pattern.loopEndTime - pattern.loopStartTime
        val gifLoopFrames = (
                loopDuration * gifState.prefs.slowdown *
                        (100.0 / frameDurationHundredths.toDouble())
                ).roundToInt()
        val totalFrames = gifLoopFrames * pattern.periodWithProps

        // Java GIF encoder
        val ios: ImageOutputStream = MemoryCacheImageOutputStream(os)
        val iw = ImageIO.getImageWritersByFormatName("gif").next().apply {
            setOutput(ios)
            prepareWriteSequence(null)
        }
        val iwp = iw.defaultWriteParam
        var metadata: IIOMetadata? = null

        // render the animation frames offscreen
        ImageComposeScene(
            width = gifState.prefs.width,
            height = gifState.prefs.height,
            density = Density(1f)
        ).use { scene ->
            // start AnimationView paused so it doesn't start its internal timer
            gifState.update(isPaused = true, message = "")

            scene.setContent {
                AnimationView(state = gifState, isAntiAlias = false)
            }

            // need to convert Skia Image into bitmap with color type BGRA_8888
            // that AWT expects; Skia images by default have color type RGBA_8888
            val bitmap = Bitmap().apply {
                allocPixels(
                    ImageInfo(
                        gifState.prefs.width,
                        gifState.prefs.height,
                        ColorType.BGRA_8888,
                        ColorAlphaType.PREMUL
                    )
                )
            }

            for (currentFrame in 0..<totalFrames) {
                val frameInLoop = currentFrame % gifLoopFrames
                if (frameInLoop == 0 && currentFrame > 0) {
                    gifState.advancePropForPath()
                }
                val time = pattern.loopStartTime + (frameInLoop.toDouble() / gifLoopFrames) * loopDuration
                gifState.update(time = time)

                // the next line is critical as it ensures the state update above
                // propagates to the composition engine before we render
                Snapshot.sendApplyNotifications()

                // render and process the image
                val image = scene.render()
                image.readPixels(bitmap)
                val bufferedImage = bitmap.toBufferedImage()

                // after the second frame all subsequent frames have identical metadata
                if (currentFrame < 2) {
                    metadata = iw.getDefaultImageMetadata(ImageTypeSpecifier(bufferedImage), iwp)
                    configureGifMetadata(metadata, frameDurationString, currentFrame)
                }

                val ii = IIOImage(bufferedImage, null, metadata)
                iw.writeToSequence(ii, null as ImageWriteParam?)

                if (wgm != null) {
                    wgm.update(currentFrame + 1, totalFrames)
                    if (wgm.isCanceled) {
                        ios.close()
                        os.close()
                        return
                    }
                }
            }
        }

        iw.endWriteSequence()
        ios.close()
        os.close()
    }

    // Helper for writeGif()

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
