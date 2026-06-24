//
// SharingHelpers.kt
//
// Helpers for sharing and exporting patterns and pattern lists.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlPatternList
import org.jugglinglab.jml.JmlPatternList.PatternRecord
import org.jugglinglab.util.jlShareFile
import org.jugglinglab.util.jlShareUrl
import org.jugglinglab.util.jlSanitizeFilename
import org.jugglinglab.util.buildShareUrl
import org.jugglinglab.util.JuggleExceptionInternal
import org.jugglinglab.util.JuggleExceptionUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.Path
import org.jetbrains.compose.resources.getString

private const val JML_MIME_TYPE = "application/octet-stream"

//------------------------------------------------------------------------------
// Functions for sharing and exporting
//------------------------------------------------------------------------------

private fun String.truncate(limit: Int): String {
    return if (this.length > limit) this.take(limit) + "..." else this
}

private suspend fun sharePattern(
    pattern: JmlPattern,
    prefs: AnimationPrefs,
    onError: (Throwable) -> Unit
) {
    val url = buildShareUrl(pattern, prefs)
    if (url.encodeToByteArray().size > 2000) {
        onError(JuggleExceptionUser(getString(Res.string.error_mobile_pattern_too_long)))
    } else {
        val title = pattern.title?.takeIf { it.isNotBlank() }
            ?: getString(Res.string.gui_pattern).lowercase()
        val subject = getString(Res.string.gui_mobile_share_subject, title).truncate(50)
        val htmlText = getString(Res.string.gui_mobile_share_html, url, title)
        jlShareUrl(
            url = url,
            subject = subject,
            htmlText = htmlText
        )
    }
}

private suspend fun exportPattern(
    pattern: JmlPattern
) {
    val title = pattern.title?.takeIf { it.isNotBlank() }
        ?: getString(Res.string.gui_pattern).lowercase()
    val subject = getString(Res.string.gui_mobile_share_subject, title).truncate(50)
    val truncatedTitle = title.take(40)
    jlShareFile(
        content = pattern.toString(),
        filename = jlSanitizeFilename("$truncatedTitle.jml"),
        mimeType = JML_MIME_TYPE,
        subject = subject
    )
}

private suspend fun exportPatternList(
    list: JmlPatternList,
    path: Path?
) {
    val listHeading = path?.name?.removeSuffix(".jml")
        ?: list.title?.takeIf { it.isNotBlank() }
        ?: getString(Res.string.gui_pattern_list)
    val sb = StringBuilder()
    list.writeJml(sb)
    val subject = getString(Res.string.gui_mobile_share_subject, listHeading).truncate(50)
    val truncatedHeading = listHeading.take(40)
    jlShareFile(
        content = sb.toString(),
        filename = jlSanitizeFilename("$truncatedHeading.jml"),
        mimeType = JML_MIME_TYPE,
        subject = subject
    )
}

//------------------------------------------------------------------------------
// Helpers to launch and handle error reporting
//------------------------------------------------------------------------------

fun CoroutineScope.shareCurrentPatternHelper(
    pattern: JmlPattern,
    prefs: AnimationPrefs,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit
) {
    launch(Dispatchers.Default) {
        onBusyChange(true)
        try {
            sharePattern(pattern, prefs, onError)
        } catch (e: Throwable) {
            val message = getString(Res.string.error_mobile_sharing, e.message ?: "")
            onError(JuggleExceptionInternal(message))
        } finally {
            onBusyChange(false)
        }
    }
}

fun CoroutineScope.exportCurrentPatternHelper(
    pattern: JmlPattern,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit
) {
    launch(Dispatchers.Default) {
        onBusyChange(true)
        try {
            exportPattern(pattern)
        } catch (e: Throwable) {
            val message = getString(Res.string.error_mobile_exporting, e.message ?: "")
            onError(JuggleExceptionInternal(message))
        } finally {
            onBusyChange(false)
        }
    }
}

fun CoroutineScope.exportListHelper(
    list: JmlPatternList,
    path: Path?,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit
) {
    launch(Dispatchers.Default) {
        onBusyChange(true)
        try {
            exportPatternList(list, path)
        } catch (e: Throwable) {
            val message = getString(Res.string.error_mobile_exporting, e.message ?: "")
            onError(JuggleExceptionInternal(message))
        } finally {
            onBusyChange(false)
        }
    }
}

fun CoroutineScope.sharePatternHelper(
    list: JmlPatternList,
    record: PatternRecord,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit
) {
    launch(Dispatchers.Default) {
        onBusyChange(true)
        try {
            val index = list.model.indexOf(record)
            if (index >= 0) {
                val pattern = list.getPatternForLine(index)
                val prefs = list.getAnimationPrefsForLine(index) ?: AnimationPrefs()
                if (pattern != null) {
                    sharePattern(pattern, prefs, onError)
                }
            }
        } catch (e: Throwable) {
            val message = getString(Res.string.error_mobile_sharing, e.message ?: "")
            onError(JuggleExceptionInternal(message))
        } finally {
            onBusyChange(false)
        }
    }
}

fun CoroutineScope.exportPatternHelper(
    list: JmlPatternList,
    record: PatternRecord,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit
) {
    launch(Dispatchers.Default) {
        onBusyChange(true)
        try {
            val index = list.model.indexOf(record)
            if (index >= 0) {
                val pattern = list.getPatternForLine(index)
                if (pattern != null) {
                    exportPattern(pattern)
                }
            }
        } catch (e: Throwable) {
            val message = getString(Res.string.error_mobile_exporting, e.message ?: "")
            onError(JuggleExceptionInternal(message))
        } finally {
            onBusyChange(false)
        }
    }
}
