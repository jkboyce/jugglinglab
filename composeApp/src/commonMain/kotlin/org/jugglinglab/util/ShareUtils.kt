//
// ShareUtils.kt
//
// Utilities for sharing juggling patterns via URL.
//
// The share URL format is:
//   https://jugglinglab.org/anim?pattern=<siteswap pattern>;setting2=...
// or:
//   https://jugglinglab.org/anim?jml=<base64-encoded gzip-compressed XML>; setting2=...
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.util

import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.notation.SiteswapPattern
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import kotlin.text.iterator

private const val SHARE_BASE_URL = "https://jugglinglab.org/anim"

// Build a shareable URL for the given pattern.
// The XML is gzip-compressed before base64 encoding to keep the URL short.

@OptIn(ExperimentalEncodingApi::class)
fun buildShareUrl(pattern: JmlPattern, prefs: AnimationPrefs): String {
    val urlStr = if (pattern.hasBasePattern &&
        pattern.basePatternNotation.equals("siteswap", ignoreCase = true) &&
        !pattern.isBasePatternEdited
    ) {
        pattern.basePatternConfig!!
    } else {
        val xml = pattern.toString()
        val compressed = gzipCompress(xml.encodeToByteArray())
        val encoded = Base64.encode(compressed)
        "jml=$encoded"
    }

    val prefsConfig = prefs.toString()
    val fullConfig = if (prefsConfig.isNotEmpty()) {
        "$urlStr;$prefsConfig"
    } else {
        urlStr
    }

    return "$SHARE_BASE_URL?${urlEncode(fullConfig)}"
}

// Decode a share URL back to a JmlPattern, or return null on any error.

@OptIn(ExperimentalEncodingApi::class)
fun decodeShareUrl(url: String): Pair<JmlPattern?, AnimationPrefs?> {
    var pattern: JmlPattern? = null
    var prefs: AnimationPrefs? = null

    try {
        var settings = urlDecode(url.substringAfter("?"))
        if ('=' !in settings) {
            settings = "pattern=$settings"  // simplified form of URL
        }
        val pl = ParameterList(settings)
        val jmlData = pl.removeParameter("jml")

        prefs = AnimationPrefs.fromParameters(pl)
        if (prefs == AnimationPrefs()) prefs = null

        pattern = if (jmlData.isNullOrBlank()) {
            val sspattern = SiteswapPattern().fromParameters(pl)
            sspattern.asJmlPattern()
        } else {
            val xml = gzipDecompress(Base64.decode(jmlData)).decodeToString()
            JmlPattern.fromJmlString(xml)
        }
    } catch (_: Exception) {
    }

    return Pair(pattern, prefs)
}

private fun gzipCompress(input: ByteArray): ByteArray {
    val buffer = Buffer()
    val sink = GzipSink(buffer)
    try {
        sink.write(Buffer().apply { write(input) }, input.size.toLong())
    } finally {
        sink.close()
    }
    return buffer.readByteArray()
}

private fun gzipDecompress(input: ByteArray): ByteArray {
    val source = GzipSource(Buffer().apply { write(input) })
    val result = Buffer()
    result.writeAll(source)
    return result.readByteArray()
}

private const val ALLOWED_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~=;&"

private fun urlEncode(input: String): String = buildString(input.length * 2) {
    // We allow '=' and ';' to pass through unencoded so that ParameterList
    // can split the parameters correctly.
    for (char in input) {
        if (char in ALLOWED_CHARS) {
            append(char)
        } else {
            val bytes = char.toString().encodeToByteArray()
            for (b in bytes) {
                append('%')
                val hex = (b.toInt() and 0xFF).toString(16).uppercase()
                if (hex.length == 1) append('0')
                append(hex)
            }
        }
    }
}

private fun urlDecode(input: String): String {
    val bytes = ByteArray(input.length * 4)
    var byteCount = 0
    var i = 0
    while (i < input.length) {
        val char = input[i]
        if (char == '%' && i + 2 < input.length) {
            val hex = input.substring(i + 1, i + 3)
            try {
                bytes[byteCount++] = hex.toInt(16).toByte()
                i += 3
                continue
            } catch (_: NumberFormatException) {
                // Ignore and fall through to treat '%' as literal
            }
        }
        val charBytes = char.toString().encodeToByteArray()
        for (b in charBytes) {
            bytes[byteCount++] = b
        }
        i++
    }
    return bytes.copyOf(byteCount).decodeToString()
}
