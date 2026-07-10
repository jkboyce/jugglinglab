//
// JlFunc.wasmJs.kt
//
// Some useful functions for WASM/Browser.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.jugglinglab.util

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.Constants
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.skia.Image
import okio.fakefilesystem.FakeFileSystem
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.parser.Parser
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.js.Promise
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.await

//------------------------------------------------------------------------------
// Helpers for converting numbers to/from strings
//------------------------------------------------------------------------------

actual fun jlParseFiniteDouble(input: String): Double {
    val value = input.trim().toDoubleOrNull() ?: throw NumberFormatException("not a double")
    if (value.isFinite()) return value
    throw NumberFormatException("not a finite value")
}

actual fun jlToStringRounded(value: Double, digits: Int): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value < 0) "-Infinity" else "Infinity"
    val multiplier = 10.0.pow(digits.toDouble())
    val rounded = kotlin.math.round(value * multiplier) / multiplier
    var s = rounded.toString()
    if (s.contains('.')) {
        s = s.trimEnd('0')
        if (s.endsWith('.')) {
            s = s.dropLast(1)
        }
    }
    if (s == "-0") s = "0"
    return s
}

//------------------------------------------------------------------------------
// Helpers for execution context information
//------------------------------------------------------------------------------

actual val jlCurrentPlatform: String = "Web/Wasm"
actual val jlAboutBoxPlatform: String = "WebAssembly (wasmJs)"
actual val jlCurrentVersion: String = Constants.VERSION

actual val jlIsDesktop: Boolean = false
actual val jlIsMobile: Boolean = false
actual val jlIsWeb: Boolean = true

actual val jlIsAndroid: Boolean = false
actual val jlIsIos: Boolean = false

actual val jlIsMobileWeb: Boolean = js(
    """(
        // 1. Modern Client Hints (Chromium-based browsers)
        (navigator.userAgentData && navigator.userAgentData.mobile) ||

        // 2. CSS Media Queries (Touch-first devices)
        (window.matchMedia && window.matchMedia("(pointer: coarse)").matches) ||

        // 3. Classic User-Agent Fallback (Safari, Firefox, etc.)
        /Mobi|Android|iPhone|iPad|iPod/i.test(navigator.userAgent) ||

        false
    )"""
)

@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

actual fun jlCurrentTimeMillis(): Long = jsDateNow().toLong()

actual fun jlExitProcess(status: Int) {
    println("jlExitProcess called with status $status")
}

private val fakeFileSystem = FakeFileSystem()
actual val jlFileSystem: okio.FileSystem = fakeFileSystem

actual val jlMaxMemoryBytes: Long = 100L * 1024 * 1024

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
    val message = wasmStringResources[key.key] ?: key.key
    return if (args.isEmpty()) {
        message
    } else {
        jlFormatString(message, *args)
    }
}

private val wasmStringResources: MutableMap<String, String> = mutableMapOf()

suspend fun prewarmWasmStringResources() {
    val lang = kotlinx.browser.window.navigator.language.lowercase()
    val localeFolder = when {
        lang.startsWith("es") -> "values-es"
        lang.startsWith("fr") -> "values-fr"
        lang.startsWith("he") -> "values-he"
        lang.startsWith("pt") -> "values-pt"
        lang.startsWith("tok") -> "values-tok"
        else -> "values"
    }

    val foldersToLoad = if (localeFolder != "values") {
        listOf("values", localeFolder)
    } else {
        listOf("values")
    }

    for (folder in foldersToLoad) {
        try {
            val guiXmlBytes = Res.readBytes("files/strings/$folder/gui_strings.xml")
            val guiXml = guiXmlBytes.decodeToString()
            parseAndPopulateStrings(guiXml)
        } catch (_: Exception) {
            // ignore if file doesn't exist
        }

        try {
            val errorXmlBytes = Res.readBytes("files/strings/$folder/error_strings.xml")
            val errorXml = errorXmlBytes.decodeToString()
            parseAndPopulateStrings(errorXml)
        } catch (_: Exception) {
            // ignore if file doesn't exist
        }
    }
}

private fun unescapeXmlString(str: String): String {
    val sb = StringBuilder()
    var i = 0
    while (i < str.length) {
        val c = str[i]
        if (c == '\\' && i + 1 < str.length) {
            when (val next = str[i + 1]) {
                'n' -> sb.append('\n')
                't' -> sb.append('\t')
                '\'' -> sb.append('\'')
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                else -> {
                    sb.append(c)
                    sb.append(next)
                }
            }
            i += 2
        } else {
            sb.append(c)
            i++
        }
    }
    return sb.toString()
}

private fun parseAndPopulateStrings(xml: String) {
    try {
        val doc = Ksoup.parse(html = xml, parser = Parser.xmlParser())
        val stringElements = doc.getElementsByTag("string")
        for (element in stringElements) {
            val key = element.attr("name")
            val value = unescapeXmlString(element.wholeText())
            if (key.isNotEmpty()) {
                wasmStringResources[key] = value
            }
        }
    } catch (e: Exception) {
        println("Error parsing XML: ${e.message}")
    }
}

actual fun jlLoadComposeImageFromUrl(urlString: String): ImageBitmap {
    throw JuggleExceptionUser("Not implemented")
}

actual fun jlBytesToImageBitmap(bytes: ByteArray): ImageBitmap {
    return Image.makeFromEncoded(bytes).toComposeImageBitmap()
}

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
actual fun <T> jlRunBlocking(block: suspend () -> T): T {
    var completed = false
    var result: Result<T>? = null
    block.startCoroutine(object : Continuation<T> {
        override val context = EmptyCoroutineContext
        override fun resumeWith(value: Result<T>) {
            completed = true
            result = value
        }
    })
    if (completed) {
        return result!!.getOrThrow()
    }
    error("Coroutine suspended in jlRunBlocking")
}

//------------------------------------------------------------------------------
// Helpers for sharing
//------------------------------------------------------------------------------

// In the web app we share a URL by copying it to the system clipboard. To avoid
// certain security restrictions on iOS, we have to initiate this copy
// immediately during the UI interaction – even though the URL isn't yet known.
// So we initiate the copy and complete the copy as two steps.

@JsFun(
    """() => {
        if (typeof window !== 'undefined') {
            window.pendingResolve = null;
            window.pendingReject = null;
            window.clipboardCopyCancelled = false;
            if (navigator.clipboard && window.isSecureContext && typeof ClipboardItem !== 'undefined') {
                const promise = new Promise((resolve, reject) => {
                    window.pendingResolve = resolve;
                    window.pendingReject = reject;
                });
                const item = new ClipboardItem({
                    "text/plain": promise.then(url => {
                        if (!url) throw new Error("Cancelled");
                        return new Blob([url], { type: "text/plain" });
                    })
                });
                navigator.clipboard.write([item]).then(() => {
                    alert('Pattern URL copied to clipboard');
                }).catch(err => {
                    if (window.clipboardCopyCancelled) {
                        window.clipboardCopyCancelled = false;
                    } else if (err && err.message !== "Cancelled") {
                        console.error('Error copying to clipboard: ', err);
                        alert('Problem copying to clipboard');
                    }
                });
                return true;
            }
        }
        return false;
    }"""
)
private external fun jsStartClipboardCopy(): Boolean

@JsFun(
    """(url) => {
        if (typeof window !== 'undefined' && window.pendingResolve) {
            window.pendingResolve(url);
            window.pendingResolve = null;
            window.pendingReject = null;
            return true;
        }
        return false;
    }"""
)
private external fun jsResolveClipboardCopy(url: String): Boolean

@JsFun(
    """() => {
        if (typeof window !== 'undefined') {
            window.clipboardCopyCancelled = true;
            if (window.pendingReject) {
                window.pendingReject(new Error("Cancelled"));
                window.pendingResolve = null;
                window.pendingReject = null;
            }
        }
    }"""
)
private external fun jsCancelClipboardCopy()

@JsFun(
    """(url) => {
        try {
            if (navigator.clipboard && window.isSecureContext) {
                navigator.clipboard.writeText(url).then(() => {
                    alert('Pattern URL copied to clipboard');
                }).catch(err => {
                    console.error('Error 1 copying to clipboard: ', err);
                    alert('Problem copying to clipboard.');
                });
            } else {
                const textArea = document.createElement('textarea');
                textArea.value = url;
                textArea.style.top = '0';
                textArea.style.left = '0';
                textArea.style.position = 'fixed';
                document.body.appendChild(textArea);
                textArea.focus();
                textArea.select();
                try {
                    const successful = document.execCommand('copy');
                    if (successful) {
                        alert('Pattern URL copied to clipboard');
                    } else {
                        alert('Problem copying to clipboard');
                    }
                } catch (err) {
                    console.error('Error 2 copying to clipboard: ', err);
                    alert('Problem copying to clipboard');
                }
                document.body.removeChild(textArea);
            }
        } catch (e) {
            console.error('Error 3 copying to clipboard: ', e);
            alert('Problem copying to clipboard');
        }
    }"""
)
private external fun jsCopyTextToClipboard(url: String)

actual fun jlPreCopyShareUrl(): Boolean {
    return jsStartClipboardCopy()
}

actual fun jlCancelPreCopyShareUrl() {
    jsCancelClipboardCopy()
}

actual fun jlShareUrl(url: String, subject: String?, htmlText: String?) {
    if (!jsResolveClipboardCopy(url)) {
        jsCopyTextToClipboard(url)
    }
}

@JsFun(
    """(content, filename, mimeType) => {
        const blob = new Blob([content], {type: mimeType});
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }"""
)
private external fun jsDownloadFile(content: String, filename: String, mimeType: String)

actual fun jlShareFile(
    content: String,
    filename: String,
    mimeType: String,
    subject: String?,
    bodyText: String?,
    htmlText: String?
) {
    jsDownloadFile(content, filename, mimeType)
}

@JsFun(
    """(int8Array) => {
        const response = new Response(int8Array);
        const stream = response.body.pipeThrough(new CompressionStream('gzip'));
        return new Response(stream).arrayBuffer().then(buf => new Int8Array(buf));
    }"""
)
private external fun jsGzipCompress(int8Array: Int8Array): Promise<Int8Array>

actual suspend fun jlGzipCompress(input: ByteArray): ByteArray {
    val jsArray = Int8Array(input.size)
    for (i in input.indices) {
        jsArray[i] = input[i]
    }
    val compressedPromise = jsGzipCompress(jsArray)
    val compressedJsArray = compressedPromise.await<Int8Array>()
    val result = ByteArray(compressedJsArray.length)
    for (i in 0 until compressedJsArray.length) {
        result[i] = compressedJsArray[i]
    }
    return result
}

@JsFun(
    """(int8Array) => {
        const response = new Response(int8Array);
        const stream = response.body.pipeThrough(new DecompressionStream('gzip'));
        return new Response(stream).arrayBuffer().then(buf => new Int8Array(buf));
    }"""
)
private external fun jsGzipDecompress(int8Array: Int8Array): Promise<Int8Array>

actual suspend fun jlGzipDecompress(input: ByteArray): ByteArray {
    val jsArray = Int8Array(input.size)
    for (i in input.indices) {
        jsArray[i] = input[i]
    }
    val decompressedPromise = jsGzipDecompress(jsArray)
    val decompressedJsArray = decompressedPromise.await<Int8Array>()
    val result = ByteArray(decompressedJsArray.length)
    for (i in 0 until decompressedJsArray.length) {
        result[i] = decompressedJsArray[i]
    }
    return result
}

@JsFun(
    """() => {
        return new Promise((resolve) => {
            const input = document.createElement('input');
            input.type = 'file';
            input.accept = '.jml';
            input.onchange = (e) => {
                const file = e.target.files[0];
                if (!file) {
                    resolve(null);
                    return;
                }
                const reader = new FileReader();
                reader.onload = (readerEvent) => {
                    resolve(readerEvent.target.result);
                };
                reader.onerror = () => {
                    resolve(null);
                };
                reader.readAsText(file);
            };
            input.click();
        });
    }"""
)
private external fun jsPickAndReadJmlFile(): Promise<JsAny?>

actual suspend fun jlPickAndReadJmlFile(): String? {
    val promise = jsPickAndReadJmlFile()
    val jsAny = promise.await<JsAny?>() ?: return null
    return jsAny.toString()
}

//------------------------------------------------------------------------------
// Helpers for playing audio
//------------------------------------------------------------------------------

private var catchAudioUrl: String? = null
private var bounceAudioUrl: String? = null

@JsFun(
    """(url, volume) => {
        const audio = new Audio(url);
        audio.volume = volume;
        audio.play().catch(e => console.log('Audio playback failed', e));
    }"""
)
private external fun jsPlaySoundFromUrl(url: String, volume: Float)

@JsFun(
    """(int8Array, mimeType) => {
        const blob = new Blob([int8Array], {type: mimeType});
        return URL.createObjectURL(blob);
    }"""
)
private external fun jsCreateBlobUrl(int8Array: Int8Array, mimeType: String): String

actual fun jlPlayCatchSound(volume: Float) {
    val url = catchAudioUrl
    if (url != null) {
        jsPlaySoundFromUrl(url, volume)
    } else {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val bytes = Res.readBytes("files/sounds/catch.wav")
                val jsArray = Int8Array(bytes.size)
                for (i in bytes.indices) {
                    jsArray[i] = bytes[i]
                }
                catchAudioUrl = jsCreateBlobUrl(jsArray, "audio/wav")
                catchAudioUrl?.let { jsPlaySoundFromUrl(it, volume) }
            } catch (e: Exception) {
                println("Error playing catch sound: ${e.message}")
            }
        }
    }
}

actual fun jlPlayBounceSound(volume: Float) {
    val url = bounceAudioUrl
    if (url != null) {
        jsPlaySoundFromUrl(url, volume)
    } else {
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val bytes = Res.readBytes("files/sounds/bounce.wav")
                val jsArray = Int8Array(bytes.size)
                for (i in bytes.indices) {
                    jsArray[i] = bytes[i]
                }
                bounceAudioUrl = jsCreateBlobUrl(jsArray, "audio/wav")
                bounceAudioUrl?.let { jsPlaySoundFromUrl(it, volume) }
            } catch (e: Exception) {
                println("Error playing bounce sound: ${e.message}")
            }
        }
    }
}

//------------------------------------------------------------------------------
// Helpers for back navigation
//------------------------------------------------------------------------------

@JsFun(
    """(enabled) => {
        if (typeof window !== 'undefined') {
            window.composeBackHandlerEnabled = enabled;
        }
    }"""
)
private external fun jsUpdateBackHandlerEnabled(enabled: Boolean)

@JsFun(
    """() => {
        if (typeof window !== 'undefined') {
            if (window.history) {
                const doPush = () => {
                    if (!window.composeBackHandlerPushed) {
                        window.composeBackHandlerPushed = true;
                        window.history.pushState({ active: true }, "");
                    }
                };

                if (window.composeBackHandlerPushed) {
                    doPush();
                } else {
                    const onInteraction = () => {
                        doPush();
                        cleanup();
                    };
                    const cleanup = () => {
                        window.removeEventListener('mousedown', onInteraction);
                        window.removeEventListener('keydown', onInteraction);
                        window.removeEventListener('touchstart', onInteraction);
                        window.removeEventListener('pointerdown', onInteraction);
                    };
                    window.composeInteractionCleanup = cleanup;
                    window.addEventListener('mousedown', onInteraction);
                    window.addEventListener('keydown', onInteraction);
                    window.addEventListener('touchstart', onInteraction);
                    window.addEventListener('pointerdown', onInteraction);
                }
            }
        }
    }"""
)
private external fun jsPushState()

@JsFun(
    """() => {
        if (typeof window !== 'undefined' && window.history) {
            window.history.replaceState({ active: false }, "");
        }
    }"""
)
private external fun jsReplaceInitialState()

@JsFun(
    """(onBack) => {
        if (typeof window !== 'undefined') {
            window.onpopstate = (event) => {
                const state = event.state;
                if (state && state.active) {
                    return;
                }
                if (window.composeBackHandlerEnabled) {
                    onBack();
                    if (window.history) {
                        window.history.forward();
                    }
                } else {
                    if (window.confirm("Are you sure you want to quit the application?")) {
                        if (window.history) {
                            window.history.back();
                        }
                    } else {
                        if (window.history) {
                            window.history.forward();
                        }
                    }
                }
            };
        }
    }"""
)
private external fun jsSetupPopStateListener(onBack: () -> Unit)

@JsFun(
    """() => {
        if (typeof window !== 'undefined') {
            window.onpopstate = null;
            if (window.composeInteractionCleanup) {
                window.composeInteractionCleanup();
                window.composeInteractionCleanup = null;
            }
            window.composeBackHandlerPushed = false;
        }
    }"""
)
private external fun jsTeardownPopStateListener()

@androidx.compose.runtime.Composable
actual fun BackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack = androidx.compose.runtime.rememberUpdatedState(onBack)

    androidx.compose.runtime.SideEffect {
        jsUpdateBackHandlerEnabled(enabled)
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        jsReplaceInitialState()
        jsPushState()
        jsSetupPopStateListener {
            currentOnBack.value()
        }
        onDispose {
            jsTeardownPopStateListener()
        }
    }
}

actual fun Modifier.backGestureHandler(enabled: Boolean, onBack: () -> Unit): Modifier {
    return this
}
