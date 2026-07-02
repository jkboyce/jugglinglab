//
// JlFunc.ios.kt
//
// Some useful functions for iOS.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("RedundantNullableReturnType")

package org.jugglinglab.util

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.skia.Image
import platform.AVFAudio.AVAudioPlayer
import platform.Foundation.*
import platform.LinkPresentation.LPLinkMetadata
import platform.UIKit.*
import platform.darwin.NSObject
import kotlin.math.min

//------------------------------------------------------------------------------
// Helpers for converting numbers to/from strings
//------------------------------------------------------------------------------

actual fun jlParseFiniteDouble(input: String): Double {
    val formatter = NSNumberFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier("en_US")
    }
    val number = formatter.numberFromString(input)
        ?: throw NumberFormatException("Invalid double format: $input")
    val value = number.doubleValue
    if (!value.isFinite()) {
        throw NumberFormatException("Not a finite value")
    }
    return value
}

actual fun jlToStringRounded(value: Double, digits: Int): String {
    val formatter = NSNumberFormatter().apply {
        locale = NSLocale.localeWithLocaleIdentifier("en_US")
        minimumFractionDigits = 0u
        maximumFractionDigits = min(10, digits).toULong()
        numberStyle = NSNumberFormatterDecimalStyle
    }
    var result = formatter.stringFromNumber(NSNumber(value)) ?: "0"
    if (result == "-0") {
        result = "0"
    }
    return result
}

//------------------------------------------------------------------------------
// Helpers for execution context information
//------------------------------------------------------------------------------

actual val jlCurrentPlatform: String by lazy {
    "iOS ${UIDevice.currentDevice.systemVersion}"
}

actual val jlAboutBoxPlatform: String by lazy {
    val device = UIDevice.currentDevice
    "${device.model} running ${device.systemName} ${device.systemVersion}"
}

actual val jlCurrentVersion: String by lazy {
    val version =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String
            ?: Constants.VERSION
    val bundleId = NSBundle.mainBundle.bundleIdentifier
    if (bundleId?.endsWith("debug") ?: false) {
        "$version-debug"
    } else {
        version
    }
}

actual val jlIsDesktop: Boolean = false
actual val jlIsMobile: Boolean = true
actual val jlIsWeb: Boolean = false

actual val jlIsAndroid: Boolean = false
actual val jlIsIos: Boolean = true

actual fun jlCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual fun jlExitProcess(status: Int) {
    platform.posix.exit(status)
}

actual val jlFileSystem: okio.FileSystem = okio.FileSystem.SYSTEM

actual val jlMaxMemoryBytes: Long = 100 * 1024 * 1024

@androidx.compose.runtime.Composable
actual fun jlIsLandscape(): Boolean {
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val containerSize = windowInfo.containerSize
    return containerSize.width > containerSize.height
}

//------------------------------------------------------------------------------
// Helpers for loading resources (UI strings, error messages, images, ...)
//------------------------------------------------------------------------------

actual fun jlGetStringResource(key: StringResource, vararg args: Any?): String {
    val message = jlRunBlocking { getString(key) }
    return if (args.isEmpty()) {
        message
    } else {
        jlFormatString(message, *args)
    }
}

actual fun jlLoadComposeImageFromUrl(urlString: String): ImageBitmap {
    throw JuggleExceptionUser("Not implemented")
}

actual fun jlBytesToImageBitmap(bytes: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

actual fun <T> jlRunBlocking(block: suspend () -> T): T {
    return runBlocking { block() }
}

//------------------------------------------------------------------------------
// Helpers for sharing
//------------------------------------------------------------------------------

private fun getTopViewController(): UIViewController? {
    var keyWindow: UIWindow? = null

    // First try the first window of the first window scene (typically the main app window)
    for (scene in UIApplication.sharedApplication.connectedScenes) {
        val windowScene = scene as? UIWindowScene ?: continue
        keyWindow = windowScene.windows.firstOrNull() as? UIWindow
        if (keyWindow != null) break
    }

    // Fallback: try finding the key window
    if (keyWindow == null) {
        for (scene in UIApplication.sharedApplication.connectedScenes) {
            val windowScene = scene as? UIWindowScene ?: continue
            for (window in windowScene.windows) {
                val uiWindow = window as? UIWindow ?: continue
                if (uiWindow.isKeyWindow()) {
                    keyWindow = uiWindow
                    break
                }
            }
            if (keyWindow != null) break
        }
    }

    // Walk up the presented view controller chain to find the topmost one
    var topVC = keyWindow?.rootViewController
    while (topVC?.presentedViewController != null) {
        topVC = topVC.presentedViewController
    }
    return topVC
}

private fun presentOnMainThread(builder: () -> UIViewController?) {
    platform.darwin.dispatch_async(platform.darwin.dispatch_get_main_queue()) {
        val topVC = getTopViewController()
        if (topVC == null) {
            println("iOS share: getTopViewController() returned null, cannot present")
            return@dispatch_async
        }
        val vc = builder()
        if (vc == null) {
            println("iOS share: builder returned null, nothing to present")
            return@dispatch_async
        }

        // Configure popoverPresentationController on iPad to prevent crashes
        if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) {
            vc.popoverPresentationController?.apply {
                sourceView = topVC.view
                sourceRect = topVC.view.bounds
                permittedArrowDirections = 0UL
            }
        }

        topVC.presentViewController(vc, animated = true, completion = null)
    }
}

// Provide custom preview metadata for URL sharing. This keeps iOS from
// requesting the web page in the background and scraping its title.

private class ShareUrlActivityItemSource(
    private val urlString: String,
    private val subject: String?
) : NSObject(), UIActivityItemSourceProtocol {

    override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any {
        return NSURL.URLWithString(urlString) ?: urlString
    }

    @ObjCSignatureOverride
    override fun activityViewController(
        activityViewController: UIActivityViewController,
        itemForActivityType: UIActivityType
    ): Any? {
        return NSURL.URLWithString(urlString) ?: urlString
    }

    @ObjCSignatureOverride
    override fun activityViewController(
        activityViewController: UIActivityViewController,
        subjectForActivityType: UIActivityType
    ): String {
        return subject ?: ""
    }

    override fun activityViewControllerLinkMetadata(activityViewController: UIActivityViewController): objcnames.classes.LPLinkMetadata? {
        val nsUrl = NSURL.URLWithString(urlString) ?: return null
        val metadata = LPLinkMetadata()
        metadata.originalURL = nsUrl
        metadata.URL = nsUrl
        metadata.title = subject ?: "Sharing link"
        return metadata as objcnames.classes.LPLinkMetadata?
    }
}

actual fun jlShareUrl(url: String, subject: String?, htmlText: String?) {
    presentOnMainThread {
        val shareSource = ShareUrlActivityItemSource(url, subject)
        val activityViewController = UIActivityViewController(
            activityItems = listOf(shareSource),
            applicationActivities = null
        )
        activityViewController
    }
}

private class ShareFileActivityItemSource(
    private val fileURL: NSURL,
    private val subject: String?
) : NSObject(), UIActivityItemSourceProtocol {

    override fun activityViewControllerPlaceholderItem(activityViewController: UIActivityViewController): Any {
        return fileURL
    }

    @ObjCSignatureOverride
    override fun activityViewController(
        activityViewController: UIActivityViewController,
        itemForActivityType: UIActivityType
    ): Any? {
        return fileURL
    }

    @ObjCSignatureOverride
    override fun activityViewController(
        activityViewController: UIActivityViewController,
        subjectForActivityType: UIActivityType
    ): String {
        return subject ?: ""
    }
}

actual fun jlShareFile(
    content: String,
    filename: String,
    mimeType: String,
    subject: String?,
    bodyText: String?,
    htmlText: String?
) {
    // Write file before dispatching to main thread
    val tempDir = NSTemporaryDirectory().trimEnd('/')
    val filePath = "$tempDir/$filename"
    val fileURL = NSURL.fileURLWithPath(filePath)

    try {
        val nsString = NSString.create(string = content)
        nsString.writeToURL(
            fileURL,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null
        )
    } catch (e: Throwable) {
        throw JuggleExceptionInternal("iOS share: failed to write file $filePath: ${e.message}")
    }

    // Verify the file was actually written
    if (!NSFileManager.defaultManager.fileExistsAtPath(filePath)) {
        throw JuggleExceptionInternal("iOS share: file does not exist after write: $filePath")
    }

    presentOnMainThread {
        val fileSource = ShareFileActivityItemSource(fileURL, subject)
        val items = mutableListOf<Any>()
        items.add(fileSource)
        if (bodyText != null) {
            items.add(bodyText)
        }
        val activityViewController = UIActivityViewController(
            activityItems = items,
            applicationActivities = null
        )
        activityViewController
    }
}

actual suspend fun jlGzipCompress(input: ByteArray): ByteArray {
    val buffer = okio.Buffer()
    val sink = okio.GzipSink(buffer)
    try {
        sink.write(okio.Buffer().apply { write(input) }, input.size.toLong())
    } finally {
        sink.close()
    }
    return buffer.readByteArray()
}

actual suspend fun jlGzipDecompress(input: ByteArray): ByteArray {
    val source = okio.GzipSource(okio.Buffer().apply { write(input) })
    val result = okio.Buffer()
    result.writeAll(source)
    return result.readByteArray()
}

//------------------------------------------------------------------------------
// Helpers for playing audio
//------------------------------------------------------------------------------

// Native AVFoundation Audio Player Pool

@OptIn(ExperimentalForeignApi::class)
private fun createAudioPlayer(resourcePath: String): AVAudioPlayer? {
    return try {
        val bytes = runBlocking {
            Res.readBytes(resourcePath)
        }
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        AVAudioPlayer(data = nsData, error = null).apply {
            prepareToPlay()
        }
    } catch (e: Exception) {
        println("Error loading sound $resourcePath: ${e.message}")
        null
    }
}

private val catchPlayerPool by lazy {
    List(5) { createAudioPlayer("files/sounds/catch.wav") }
}
private var catchIndex = 0

private val bouncePlayerPool by lazy {
    List(5) { createAudioPlayer("files/sounds/bounce.wav") }
}
private var bounceIndex = 0

actual fun jlPlayCatchSound(volume: Float) {
    val player = catchPlayerPool[catchIndex]
    catchIndex = (catchIndex + 1) % catchPlayerPool.size
    player?.let {
        it.volume = volume
        if (it.playing) it.stop()
        it.currentTime = 0.0
        it.play()
    }
}

actual fun jlPlayBounceSound(volume: Float) {
    val player = bouncePlayerPool[bounceIndex]
    bounceIndex = (bounceIndex + 1) % bouncePlayerPool.size
    player?.let {
        it.volume = volume
        if (it.playing) it.stop()
        it.currentTime = 0.0
        it.play()
    }
}

//------------------------------------------------------------------------------
// Helpers for back navigation
//------------------------------------------------------------------------------

@androidx.compose.runtime.Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No-op on iOS
}

actual fun Modifier.backGestureHandler(enabled: Boolean, onBack: () -> Unit): Modifier =
    if (enabled) {
        this.pointerInput(Unit) {
            val edgeWidth = 30.dp.toPx()
            val swipeThreshold = 80.dp.toPx()
            awaitEachGesture {
                var down: PointerInputChange? = null
                while (down == null) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val firstDown = event.changes.firstOrNull { it.changedToDown() }
                    if (firstDown != null) {
                        down = firstDown
                    }
                }

                if (down.position.x <= edgeWidth) {
                    var dragAmount = 0f
                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId }
                        if (change == null || change.changedToUp() || !change.pressed) {
                            if (dragAmount >= swipeThreshold) {
                                onBack()
                            }
                            break
                        }
                        val dragDelta = change.position.x - change.previousPosition.x
                        if (dragDelta > 0f || dragAmount > 0f) {
                            dragAmount += dragDelta
                            change.consume()
                        }
                        if (dragAmount < 0f) {
                            dragAmount = 0f
                        }
                    }
                }
            }
        }
    } else {
        this
    }
