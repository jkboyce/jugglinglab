//
// PatternListHelpers.kt
//
// Helpers for sharing and exporting from pattern lists.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.mobile

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.core.AnimationPrefs
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

fun CoroutineScope.exportListHelper(
    list: JmlPatternList,
    path: Path?,
    onBusyChange: (Boolean) -> Unit,
    onError: (Throwable) -> Unit
) {
    launch(Dispatchers.Default) {
        onBusyChange(true)
        try {
            val listHeading = path?.name?.removeSuffix(".jml")
                ?: list.title?.takeIf { it.isNotBlank() }
                ?: getString(Res.string.gui_pattern_list)
            val sb = StringBuilder()
            list.writeJml(sb)
            jlShareFile(
                content = sb.toString(),
                filename = jlSanitizeFilename("$listHeading.jml"),
                mimeType = "application/xml",
                subject = getString(Res.string.gui_mobile_share_subject, listHeading)
            )
        } catch (e: Throwable) {
            onError(
                JuggleExceptionInternal(
                    getString(
                        Res.string.error_mobile_exporting,
                        e.message ?: ""
                    )
                )
            )
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
                    val url = buildShareUrl(pattern = pattern, prefs = prefs)
                    if (url.encodeToByteArray().size > 2000) {
                        onError(JuggleExceptionUser(getString(Res.string.error_mobile_pattern_too_long)))
                    } else {
                        val title = pattern.title?.takeIf { it.isNotBlank() }
                            ?: getString(Res.string.gui_pattern).lowercase()
                        val subject = getString(Res.string.gui_mobile_share_subject, title)
                        val htmlText = getString(Res.string.gui_mobile_share_html, url, title)
                        jlShareUrl(url, subject = subject, htmlText = htmlText)
                    }
                }
            }
        } catch (e: Throwable) {
            onError(
                JuggleExceptionInternal(
                    getString(
                        Res.string.error_mobile_sharing,
                        e.message ?: ""
                    )
                )
            )
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
                    val title = pattern.title?.takeIf { it.isNotBlank() }
                        ?: getString(Res.string.gui_pattern).lowercase()
                    jlShareFile(
                        content = pattern.toString(),
                        filename = jlSanitizeFilename("$title.jml"),
                        mimeType = "application/xml",
                        subject = getString(Res.string.gui_mobile_share_subject, title)
                    )
                }
            }
        } catch (e: Throwable) {
            onError(
                JuggleExceptionInternal(
                    getString(
                        Res.string.error_mobile_exporting,
                        e.message ?: ""
                    )
                )
            )
        } finally {
            onBusyChange(false)
        }
    }
}
