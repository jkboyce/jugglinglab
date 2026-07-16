//
// ShareUtils.kt
//
// Utilities for sharing juggling patterns via URL.
//
// The share URL format is:
//   https://jugglinglab.org/anim?pattern=<siteswap pattern>;setting2=...
// or:
//   https://jugglinglab.org/anim?jml=<base64-encoded gzip-compressed XML>;setting2=...
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.util

import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.core.AnimationPrefs.Companion.FPS_DEF
import org.jugglinglab.core.AnimationPrefs.Companion.HEIGHT_DEF
import org.jugglinglab.core.AnimationPrefs.Companion.MOUSEPAUSE_DEF
import org.jugglinglab.core.AnimationPrefs.Companion.VIEW_DEF
import org.jugglinglab.core.AnimationPrefs.Companion.WIDTH_DEF
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.notation.SiteswapPattern
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.text.iterator

private const val SHARE_BASE_URL = "https://jugglinglab.org/anim"

// Build a shareable URL for the given pattern. If the pattern is in JML format,
// gzip-compress before base64 encoding to keep the URL short.

@OptIn(ExperimentalEncodingApi::class)
suspend fun buildShareUrl(pattern: JmlPattern, prefs: AnimationPrefs): String {
    // disable desktop-specific prefs
    val prefsConfig = prefs.copy(
        width = WIDTH_DEF,
        height = HEIGHT_DEF,
        fps = FPS_DEF,
        defaultView = VIEW_DEF,
        mousePause = MOUSEPAUSE_DEF
    ).toString()

    val patternConfig = if (pattern.hasBasePattern &&
        pattern.basePatternNotation.equals("siteswap", ignoreCase = true) &&
        !pattern.isBasePatternEdited
    ) {
        val bpConfig = pattern.basePatternConfig!!
        if (prefsConfig.isEmpty() && !bpConfig.contains(';')) {
            bpConfig.substringAfter("pattern=")
        } else {
            bpConfig
        }
    } else {
        val xml = pattern.toString()
        val compressed = jlGzipCompress(xml.encodeToByteArray())
        val encoded = Base64.encode(compressed)
        "jml=$encoded"
    }

    val fullConfig = if (prefsConfig.isNotEmpty()) {
        "$patternConfig;$prefsConfig"
    } else {
        patternConfig
    }

    return "$SHARE_BASE_URL?${urlEncode(fullConfig)}"
}

// Decode a share URL back to a JmlPattern. Any exceptions during decoding
// will be thrown back to the caller.

@OptIn(ExperimentalEncodingApi::class)
suspend fun decodeShareUrl(url: String): Pair<JmlPattern, AnimationPrefs?> {
    var config = urlDecode(url.substringAfter("?"))
    if ('=' !in config) {
        config = "pattern=$config"  // simplified form of URL
    }
    val pl = ParameterList(config)
    val jmlData = pl.removeParameter("jml")

    var prefs: AnimationPrefs? = AnimationPrefs.fromParameters(pl)
    if (prefs == AnimationPrefs()) prefs = null

    val pattern = if (jmlData.isNullOrBlank()) {
        val sspattern = SiteswapPattern().fromParameters(pl)
        sspattern.asJmlPattern()
    } else {
        val xml = jlGzipDecompress(Base64.decode(jmlData)).decodeToString()
        JmlPattern.fromJmlString(xml)
    }

    return Pair(pattern, prefs)
}

private const val ALLOWED_CHARS =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~=;&"

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
