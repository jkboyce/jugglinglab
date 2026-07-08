//
// JlFunc.android.kt
//
// Some useful functions.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.util

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.Modifier
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale
import kotlin.math.min

//------------------------------------------------------------------------------
// Helpers for converting numbers to/from strings
//------------------------------------------------------------------------------

private val nf: NumberFormat by lazy {
    // use US-style number formatting for interoperability of JML files across
    // Locales
    NumberFormat.getInstance(Locale.US)
}

@Throws(NumberFormatException::class)
actual fun jlParseFiniteDouble(input: String): Double {
    try {
        val x = nf.parse(input)?.toDouble() ?: throw NumberFormatException()
        if (x.isFinite()) {
            return x
        }
        throw NumberFormatException("not a finite value")
    } catch (_: ParseException) {
        throw NumberFormatException()
    }
}

actual fun jlToStringRounded(value: Double, digits: Int): String {
    val fmt = "###.##########".take(if (digits <= 0) 3 else 4 + min(10, digits))
    val formatter = DecimalFormat(fmt, DecimalFormatSymbols(Locale.US))
    var result = formatter.format(value)
    if (result == "-0") {
        // strange quirk
        result = "0"
    }
    return result
}

//------------------------------------------------------------------------------
// Helpers for execution context information
//------------------------------------------------------------------------------

actual val jlCurrentPlatform: String by lazy {
    "Android ${Build.VERSION.SDK_INT}"
}

actual val jlAboutBoxPlatform: String by lazy {
    val javaVersion = System.getProperty("java.version")
    val javaVmName = System.getProperty("java.vm.name")
    val javaVmVersion = System.getProperty("java.vm.version")
    "Java version $javaVersion\n$javaVmName ($javaVmVersion)"
}

actual val jlCurrentVersion: String by lazy {
    val context = AndroidContext.get()
    if (context == null) {
        Constants.VERSION
    } else {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: Constants.VERSION
        } catch (_: Exception) {
            Constants.VERSION
        }
    }
}

actual val jlIsDesktop: Boolean = false
actual val jlIsMobile: Boolean = true
actual val jlIsWeb: Boolean = false

actual val jlIsAndroid: Boolean = true
actual val jlIsIos: Boolean = false

actual val jlIsMobileWeb: Boolean = false

actual fun jlCurrentTimeMillis(): Long = System.currentTimeMillis()

actual fun jlExitProcess(status: Int) {
    kotlin.system.exitProcess(status)
}

actual val jlFileSystem: okio.FileSystem = okio.FileSystem.SYSTEM

actual val jlMaxMemoryBytes: Long = 100L * 1024 * 1024

@androidx.compose.runtime.Composable
actual fun jlIsLandscape(): Boolean {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    return configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
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

// Load an image from a URL string.
//
// In the event of a problem, throw a JuggleExceptionUser with a relevant message.

@Throws(JuggleExceptionUser::class)
actual fun jlLoadComposeImageFromUrl(urlString: String): ImageBitmap {
    throw JuggleExceptionUser("Not implemented")
}

actual fun jlBytesToImageBitmap(bytes: ByteArray): ImageBitmap {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    return bitmap.asImageBitmap()
}

actual fun <T> jlRunBlocking(block: suspend () -> T): T {
    return runBlocking { block() }
}

//------------------------------------------------------------------------------
// Helpers for sharing
//------------------------------------------------------------------------------

// Singleton holder for the application Context, set by MainActivity.
// Required so that platform functions like jlShareUrl can access a Context
// without it needing to be threaded through the Compose call hierarchy.
//
// We use a WeakReference to satisfy the lint "static field leak" check.
// In practice this reference is never cleared while the process is alive,
// because we always store applicationContext (a process-scoped singleton).

object AndroidContext {
    private var contextRef: java.lang.ref.WeakReference<android.content.Context>? = null

    fun set(ctx: android.content.Context) {
        contextRef = java.lang.ref.WeakReference(ctx.applicationContext)
    }

    fun get(): android.content.Context? = contextRef?.get()
}

actual fun jlPreCopyShareUrl(): Boolean = false
actual fun jlCancelPreCopyShareUrl() {}

@SuppressLint("ObsoleteSdkInt")
actual fun jlShareUrl(url: String, subject: String?, htmlText: String?) {
    val context = AndroidContext.get() ?: return
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, url)
        if (subject != null) {
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)  // email subject
        }
        if (htmlText != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            putExtra(android.content.Intent.EXTRA_HTML_TEXT, htmlText)  // rich email body
        }
    }
    val chooser = android.content.Intent.createChooser(sendIntent, "Share pattern")
    chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

@SuppressLint("ObsoleteSdkInt")
actual fun jlShareFile(
    content: String,
    filename: String,
    mimeType: String,
    subject: String?,
    bodyText: String?,
    htmlText: String?
) {
    val context = AndroidContext.get() ?: return

    // Write content to a file in the app's cache directory.
    val shareDir = java.io.File(context.cacheDir, "shares")
    shareDir.mkdirs()
    val file = java.io.File(shareDir, filename)
    file.writeText(content)

    // Obtain a content:// URI via FileProvider (required since API 24).
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        if (subject != null) {
            putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
        }
        if (bodyText != null) {
            putExtra(android.content.Intent.EXTRA_TEXT, bodyText)
        }
        if (htmlText != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            putExtra(android.content.Intent.EXTRA_HTML_TEXT, htmlText)
        }
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = android.content.Intent.createChooser(sendIntent, "Export file")
    chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}

actual suspend fun jlGzipCompress(input: ByteArray): ByteArray {
    val buffer = okio.Buffer()
    okio.GzipSink(buffer).use { sink ->
        sink.write(okio.Buffer().apply { write(input) }, input.size.toLong())
    }
    return buffer.readByteArray()
}

actual suspend fun jlGzipDecompress(input: ByteArray): ByteArray {
    val source = okio.GzipSource(okio.Buffer().apply { write(input) })
    val result = okio.Buffer()
    result.writeAll(source)
    return result.readByteArray()
}

actual suspend fun jlPickAndReadJmlFile(): String? = null

//------------------------------------------------------------------------------
// Helpers for playing audio
//------------------------------------------------------------------------------

private val soundPool: android.media.SoundPool by lazy {
    android.media.SoundPool.Builder().setMaxStreams(5).build()
}

private val catchSoundId: Int by lazy {
    val context = AndroidContext.get() ?: return@lazy -1
    try {
        val file = java.io.File(context.cacheDir, "catch.wav")
        if (!file.exists()) {
            val bytes = runBlocking {
                Res.readBytes("files/sounds/catch.wav")
            }
            file.writeBytes(bytes)
        }
        soundPool.load(file.absolutePath, 1)
    } catch (_: Exception) {
        -1
    }
}

private val bounceSoundId: Int by lazy {
    val context = AndroidContext.get() ?: return@lazy -1
    try {
        val file = java.io.File(context.cacheDir, "bounce.wav")
        if (!file.exists()) {
            val bytes = runBlocking {
                Res.readBytes("files/sounds/bounce.wav")
            }
            file.writeBytes(bytes)
        }
        soundPool.load(file.absolutePath, 1)
    } catch (_: Exception) {
        -1
    }
}

actual fun jlPlayCatchSound(volume: Float) {
    if (catchSoundId != -1) {
        soundPool.play(catchSoundId, volume, volume, 0, 0, 1f)
    }
}

actual fun jlPlayBounceSound(volume: Float) {
    if (bounceSoundId != -1) {
        soundPool.play(bounceSoundId, volume, volume, 0, 0, 1f)
    }
}

//------------------------------------------------------------------------------
// Helpers for back navigation
//------------------------------------------------------------------------------

@androidx.compose.runtime.Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}

actual fun Modifier.backGestureHandler(enabled: Boolean, onBack: () -> Unit): Modifier {
    // No-op on Android; back is initiated by back button
    return this
}
