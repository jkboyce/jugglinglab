// Display-only separation of props held by the same hand.
// Copyright 2026 Juggling Lab contributors. Licensed under the project's GPL.
package org.jugglinglab.renderer

import androidx.compose.ui.geometry.Offset
import org.jugglinglab.jml.JmlEvent
import org.jugglinglab.jml.JmlPattern
import kotlin.math.min
import kotlin.math.abs

internal object HeldPropLayout {
    data class Hand(val juggler: Int, val hand: Int, val paths: List<Int>)

    fun hands(pattern: JmlPattern, time: Double): List<Hand> = buildList {
        for (j in 1..pattern.numberOfJugglers) {
            for (h in listOf(JmlEvent.RIGHT_HAND, JmlEvent.LEFT_HAND)) {
                val paths = (1..pattern.numberOfPaths).filter { pattern.layout.isHandHoldingPath(j, h, time, it) }
                if (paths.size > 1) add(Hand(j, h, paths))
            }
        }
    }

    data class Placement(val juggler: Int, val hand: Int, val offsets: Map<Int, Offset>)

    /** A deterministic, simulation-time motion track; seeking backwards needs no frame history. */
    class Motion(private val pattern: JmlPattern) {
        private data class Carry(val path: Int, val juggler: Int, val hand: Int, val start: Double, var end: Double, val approachStart: Double?)
        private data class Boundary(
            val time: Double, val before: Map<Int, Offset>, val after: Map<Int, Offset>,
            val incoming: Set<Int>, val outgoing: Set<Int>, val release: Map<Int, Offset>, val lead: Double, val settle: Double
        )
        private class Track(val juggler: Int, val hand: Int, private val carries: List<Carry>) {
            private fun layout(time: Double): Map<Int, Offset> {
                val held = carries.filter { it.start <= time && time < it.end }
                    .sortedWith(compareByDescending<Carry> { it.start }.thenBy { it.path })
                return held.mapIndexed { i, c -> c.path to offset(i, held.size, 1f, 1f) }.toMap()
            }
            private val times = carries.flatMap { listOf(it.start, it.end) }.sorted().fold(mutableListOf<Double>()) { a, t ->
                if (a.isEmpty() || abs(t - a.last()) > 0.000001) a.add(t)
                a
            }
            private val boundaries = times.mapIndexed { i, t ->
                val before = layout(t - 0.0000001)
                val after = layout(t + 0.0000001)
                val approach = carries.filter { abs(it.start - t) < 0.000001 }.mapNotNull {
                    it.approachStart?.let { start -> (t - start) * 0.45 }
                }.minOrNull() ?: 0.14
                val outgoing = before.keys - after.keys
                val residents = before.keys.filter { it !in outgoing }
                val release = before.keys.associateWith { path ->
                    if (path in outgoing) Offset.Zero else offset(residents.indexOf(path) + 1, residents.size + 1, 1f, 1f)
                }
                Boundary(t, before, after, after.keys - before.keys, outgoing, release,
                    min(approach, min(0.14, if (i > 0) (t - times[i-1]) * 0.4 else 0.14)),
                    min(0.14, if (i < times.lastIndex) (times[i+1] - t) * 0.4 else 0.14))
            }
            private fun blend(a: Offset, b: Offset, fraction: Double): Offset {
                val f = fraction.coerceIn(0.0, 1.0).toFloat()
                val eased = f * f * (3 - 2 * f)
                return a + (b - a) * eased
            }
            fun at(time: Double): Placement {
                for (b in boundaries) {
                    if (time >= b.time - b.lead && time <= b.time) {
                        val f = (time - b.time + b.lead) / b.lead
                        val offsets = (b.before.keys + b.after.keys).associateWith { path ->
                            val initial = b.before[path] ?: Offset.Zero
                            // Catch at the real hand: incoming props stay there while residents move aside.
                            // Extra props in a simultaneous multiplex ease into their visible slots in flight.
                            val target = if (path in b.outgoing) Offset.Zero
                                else if (b.incoming.isNotEmpty()) b.after[path] ?: Offset.Zero else b.release[path] ?: initial
                            blend(initial, target, f)
                        }
                        return Placement(juggler, hand, offsets)
                    }
                    if (b.incoming.isEmpty() && time > b.time && time < b.time + b.settle) {
                        val offsets = b.after.keys.associateWith { path ->
                            blend(b.release[path] ?: Offset.Zero, b.after.getValue(path), (time - b.time) / b.settle)
                        }
                        return Placement(juggler, hand, offsets)
                    }
                }
                val layout = boundaries.lastOrNull { it.time <= time }?.after ?: emptyMap()
                return Placement(juggler, hand, layout)
            }
        }
        private val tracks: List<Track>
        init {
            val carries = mutableListOf<Carry>()
            pattern.layout.pathLinks.forEachIndexed { index, links ->
                var previous: Carry? = null
                var approachStart: Double? = null
                for (link in links) {
                    if (!link.isInHand) { previous = null; approachStart = link.startEvent.t; continue }
                    val old = previous
                    if (old != null && old.juggler == link.holdingJuggler && old.hand == link.holdingHand &&
                        abs(old.end - link.startEvent.t) < 0.000001) {
                        old.end = link.endEvent.t
                    } else {
                        previous = Carry(index + 1, link.holdingJuggler, link.holdingHand, link.startEvent.t, link.endEvent.t, approachStart)
                        carries.add(previous!!)
                        approachStart = null
                    }
                }
            }
            tracks = carries.groupBy { it.juggler to it.hand }.map { (hand, spans) -> Track(hand.first, hand.second, spans) }
        }
        fun frame(time: Double): List<Placement> = tracks.map { track ->
            val placement = track.at(time)
            placement.copy(offsets = placement.offsets.filter { (path, _) ->
                val link = pattern.layout.pathLinks[path - 1].firstOrNull {
                    time >= it.startEvent.t && time < it.endEvent.t
                }
                link != null && if (link.isInHand) {
                    link.holdingJuggler == placement.juggler && link.holdingHand == placement.hand
                } else {
                    link.endEvent.juggler == placement.juggler && link.endEvent.hand == placement.hand
                }
            })
        }
    }

    // The first prop remains at the physical hand. Others sit in a compact fan.
    // Screen-space offsets remain readable when the camera is rotated or stereo.
    fun offset(index: Int, count: Int, diameter: Float, direction: Float): Offset {
        if (count < 2 || index == 0) return Offset.Zero
        val columns = min(4, count)
        val col = index % columns
        val row = index / columns
        val spacing = diameter * 1.08f
        return Offset(direction * col * spacing, -(row * spacing + col * diameter * 0.3f))
    }
}
