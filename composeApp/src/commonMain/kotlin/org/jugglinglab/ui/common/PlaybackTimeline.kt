// Beat inspection controls shared with the native Juggling Lab animation state.
package org.jugglinglab.ui.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import org.jugglinglab.core.PatternAnimationState
import org.jugglinglab.core.AnimationPrefs
import org.jugglinglab.util.jlToStringRounded
import org.jugglinglab.notation.SiteswapPattern
import kotlin.math.floor
import kotlin.math.max

@Composable
fun PlaybackTimeline(state: PatternAnimationState) {
    val parsedBeats = remember(state.pattern) {
        try {
            state.pattern.basePatternConfig?.let { SiteswapPattern().inspectThrows(it, finite = false).beats } ?: 1
        } catch (_: Throwable) { 1 }
    }
    val preview = state.previewWindow
    val start = preview?.start ?: state.pattern.loopStartTime
    val end = preview?.end ?: state.pattern.loopEndTime
    val step = preview?.secondsPerBeat ?: ((end - start) / max(1, parsedBeats))
    val beat = floor((state.time - start).coerceAtLeast(0.0) / step + 0.00001).toInt()
    val lastBeat = preview?.let { it.beats - 1 } ?: parsedBeats
    fun seek(t: Double) {
        state.update(time = t.coerceIn(start, end), isPaused = true, message = "", propForPath = state.pattern.initialPropForPath)
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = { seek(start) }) { Text("Restart") }
            TextButton(onClick = { seek(start + max(0, beat - 1) * step) }) { Text("− Beat") }
            TextButton(onClick = {
                if (state.time >= end - 0.00001) state.update(time = start, propForPath = state.pattern.initialPropForPath)
                state.togglePlayback()
            }) { Text(if (state.isPaused) "Play" else "Pause") }
            TextButton(onClick = { seek(start + (beat + 1) * step) }) { Text("+ Beat") }
            Text("Beat ${beat + 1} / ${lastBeat + 1}", style = MaterialTheme.typography.labelSmall)
        }
        var speedMenuOpen by remember { mutableStateOf(false) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Playback speed", style = MaterialTheme.typography.labelSmall)
            Box {
                TextButton(onClick = { speedMenuOpen = true }) {
                    val multiplier = AnimationPrefs.SLOWDOWN_DEF / state.prefs.slowdown
                    Text("${jlToStringRounded(multiplier, 2)}× ▾")
                }
                DropdownMenu(expanded = speedMenuOpen, onDismissRequest = { speedMenuOpen = false }) {
                    for (multiplier in listOf(0.25, 0.5, 0.75, 1.0, 1.5, 2.0)) {
                        DropdownMenuItem(text = { Text("${jlToStringRounded(multiplier, 2)}×") }, onClick = {
                            state.update(prefs = state.prefs.copy(slowdown = AnimationPrefs.SLOWDOWN_DEF / multiplier))
                            speedMenuOpen = false
                        })
                    }
                }
            }
        }
        Slider(value = state.time.coerceIn(start, end).toFloat(), onValueChange = { seek(it.toDouble()) },
            valueRange = start.toFloat()..end.toFloat(), modifier = Modifier.fillMaxWidth())
        if (preview != null) {
            val atEnd = state.time >= end - 0.00001
            val message = preview.conflict?.message ?: "Finite sequence · final catches at beat ${preview.beats}"
            Text((if (atEnd) "Stopped · " else "Preview · ") + message,
                style = MaterialTheme.typography.bodySmall,
                color = if (preview.conflict == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        }
    }
}
