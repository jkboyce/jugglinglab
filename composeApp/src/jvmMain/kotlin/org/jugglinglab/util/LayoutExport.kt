//
// LayoutExport.kt
//
// Extracts a JmlPattern's resolved physical layout -- juggler, hand, and prop
// positions over time -- as plain data, for the "tolayout" command line mode
// (see JlCommandLine.doTolayout).
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.util

import org.jugglinglab.jml.JmlEvent
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlTransition
import org.jugglinglab.layout.LaidoutPattern
import org.jugglinglab.layout.PathLink
import kotlinx.serialization.Serializable

@Serializable
data class Coord3(val x: Double, val y: Double, val z: Double)

@Serializable
data class LayoutJugglerSeries(
    val juggler: Int,
    // Global position and facing angle (degrees, rotation around the
    // vertical z axis -- see LaidoutPattern.getJugglerAngle), one entry
    // per sampleTimes.
    val positions: List<Coord3>,
    val angles: List<Double>
)

@Serializable
data class LayoutHandSeries(
    val juggler: Int,
    val hand: String, // "left" or "right"
    // Global and juggler-local coordinates (see
    // LaidoutPattern.convertGlobalToLocal), one entry per sampleTimes.
    val globalPositions: List<Coord3>,
    val localPositions: List<Coord3>
)

@Serializable
data class LayoutPropSeries(
    val path: Int, // 1-based, matches JML's own <throw path="N"> numbering
    val positions: List<Coord3>,
    // One entry per sampleTimes, describing this prop's state at that
    // instant (see classifyPropState):
    //   "air"                     -- in flight
    //   "<juggler>:<hand> held"   -- continuously held, no event right now
    //   "<juggler>:<hand> catching[:soft|:grab]"  -- exact catch instant
    //   "<juggler>:<hand> throwing[:<type>]"      -- exact throw instant
    // The catch/throw type suffix is omitted for the ordinary case (a
    // plain catch, or a "toss" throw) and included otherwise.
    val states: List<String>
)

@Serializable
data class LayoutExport(
    val schemaVersion: Int = 1,
    val title: String?,
    val numberOfJugglers: Int,
    val numberOfPaths: Int,
    val loopStartTime: Double,
    val loopEndTime: Double,
    // Number of loops needed for every prop to return to its own starting
    // path -- the order of the delay symmetry's path permutation (LCM of
    // its cycle lengths; see Permutation.maxOrder). 1 for a pattern whose
    // props don't change paths cycle to cycle.
    val pathPermOrder: Int,
    // Number of loops needed for the pattern to return to its original
    // configuration counting only distinguishable PROPS, not path indices
    // (see JmlPattern.periodWithProps).
    val propPermOrder: Int,
    // Shared time axis (seconds, pattern-loop-relative): a uniform grid at
    // the requested sample rate, plus every exact catch/throw time in range
    // spliced in, deduplicated and sorted. Every *Series list is index-aligned
    // with this array.
    val sampleTimes: List<Double>,
    val jugglers: List<LayoutJugglerSeries>,
    val hands: List<LayoutHandSeries>,
    val props: List<LayoutPropSeries>
)

// Round to a fixed precision before dedup/sort.

private const val TIME_ROUNDING_SCALE = 1e9 // 9 decimal places

private fun roundTime(t: Double): Double {
    return kotlin.math.round(t * TIME_ROUNDING_SCALE) / TIME_ROUNDING_SCALE
}

private fun handName(handCode: Int): String =
    if (handCode == JmlEvent.LEFT_HAND) "left" else "right"

// Suffix for a catch at `event` landing on `path`.

private fun catchSuffix(event: JmlEvent, path: Int): String {
    val tr = event.transitions.firstOrNull { it.path == path && it.isThrowOrCatch }
    return when (tr?.type) {
        JmlTransition.TRANS_SOFTCATCH -> ":soft"
        JmlTransition.TRANS_GRABCATCH -> ":grab"
        else -> "" // TRANS_CATCH (ordinary) or not found
    }
}

// Suffix for the throw type that `pl` (a flight PathLink) represents.

private fun throwSuffix(pl: PathLink): String {
    val type = pl.throwType ?: return ""
    return if (type.equals("toss", ignoreCase = true)) "" else ":$type"
}

// Classify path-slot `slot`'s state at local time `t`.

private fun classifyPropState(pathLinks: List<PathLink>, slot: Int, t: Double): String {
    for (pl in pathLinks) {
        if (roundTime(pl.startEvent.t) != t) continue
        if (!pl.isInHand) {
            val hand = handName(pl.startEvent.hand)
            return "${pl.startEvent.juggler}:$hand throwing${throwSuffix(pl)}"
        }
        val tr = pl.startEvent.event.transitions.firstOrNull { it.path == slot }
        if (tr != null && tr.type != JmlTransition.TRANS_HOLDING) {
            val hand = handName(pl.startEvent.hand)
            return "${pl.startEvent.juggler}:$hand catching${catchSuffix(pl.startEvent.event, slot)}"
        }
    }
    for (pl in pathLinks) {
        if (t >= roundTime(pl.startEvent.t) && t <= roundTime(pl.endEvent.t)) {
            return if (pl.isInHand) {
                "${pl.holdingJuggler}:${handName(pl.holdingHand)} held"
            } else {
                "air"
            }
        }
    }
    throw IllegalStateException("no segment found for path $slot at t=$t")
}

// Build one loop's worth of local sample times: a uniform grid at `fps`
// samples/second over [loopStart, loopEnd] (inclusive of both ends), with
// every path's exact catch/throw time within the loop merged in.
//
// This is deliberately scoped to a SINGLE loop, not the full pathPermOrder-loop
// closed duration. Hand and juggler motion physically repeats every loop
// regardless, and prop identity across multiple loops is reconstructed by
// walking the delay symmetry's path permutation.

private fun buildLocalSampleTimes(
    layout: LaidoutPattern,
    loopStart: Double,
    loopEnd: Double,
    fps: Double
): List<Double> {
    val dt = 1.0 / fps

    val times = sortedSetOf<Double>()
    var t = loopStart
    while (t < loopEnd) {
        times.add(roundTime(t))
        t += dt
    }
    times.add(roundTime(loopEnd)) // exact final instant, even if dt doesn't divide evenly

    for (pathLinks in layout.pathLinks) {
        for (pl in pathLinks) {
            val t0 = pl.startEvent.t
            val t1 = pl.endEvent.t
            if (t0 in loopStart..loopEnd) times.add(roundTime(t0))
            if (t1 in loopStart..loopEnd) times.add(roundTime(t1))
        }
    }

    return times.toList()
}

fun buildLayoutExport(pat: JmlPattern, fps: Double): LayoutExport {
    val layout = pat.layout
    val loopStart = pat.loopStartTime
    val loopEnd = pat.loopEndTime
    val loopLength = loopEnd - loopStart
    val perm = pat.pathPermutation
    val order = perm?.maxOrder ?: 1

    val localTimes = buildLocalSampleTimes(layout, loopStart, loopEnd, fps)

    // `order` copies of localTimes, one per repeat, offset by that
    // repeat's own start time -- dropping each repeat's own first entry
    // (after the first) since it's numerically identical to the previous
    // repeat's last entry (both are the shared loop-boundary instant).
    val sampleTimes = (0 until order).flatMap { r ->
        val times = if (r == 0) localTimes else localTimes.drop(1)
        times.map { it + r * loopLength }
    }

    val jugglers = (1..pat.numberOfJugglers).map { j ->
        val positions = mutableListOf<Coord3>()
        val angles = mutableListOf<Double>()
        for (st in sampleTimes) {
            val c = Coordinate()
            layout.getJugglerPosition(j, st, c)
            positions.add(Coord3(c.x, c.y, c.z))
            angles.add(layout.getJugglerAngle(j, st))
        }
        LayoutJugglerSeries(j, positions, angles)
    }

    val hands = (1..pat.numberOfJugglers).flatMap { j ->
        listOf(JmlEvent.LEFT_HAND to "left", JmlEvent.RIGHT_HAND to "right").map { (handCode, handName) ->
            val globalPositions = mutableListOf<Coord3>()
            val localPositions = mutableListOf<Coord3>()
            for (st in sampleTimes) {
                val global = Coordinate()
                layout.getHandCoordinate(j, handCode, st, global)
                globalPositions.add(Coord3(global.x, global.y, global.z))
                val local = layout.convertGlobalToLocal(global, j, st)
                localPositions.add(Coord3(local.x, local.y, local.z))
            }
            LayoutHandSeries(j, handName, globalPositions, localPositions)
        }
    }

    // Prop identity moves between path "slots" across repeats, per the
    // delay symmetry's path permutation.
    val props = (1..pat.numberOfPaths).map { p ->
        val positions = mutableListOf<Coord3>()
        val states = mutableListOf<String>()
        for (r in 0 until order) {
            val slot = perm?.map(p, -r) ?: p
            val slotLinks = layout.pathLinks[slot - 1]
            // Same per-repeat seam-dropping as sampleTimes above, to stay
            // index-aligned with it.
            val times = if (r == 0) localTimes else localTimes.drop(1)
            for (t in times) {
                val c = Coordinate()
                layout.getPathCoordinate(slot, t, c)
                positions.add(Coord3(c.x, c.y, c.z))
                states.add(classifyPropState(slotLinks, slot, t))
            }
        }
        LayoutPropSeries(p, positions, states)
    }

    return LayoutExport(
        title = pat.title,
        numberOfJugglers = pat.numberOfJugglers,
        numberOfPaths = pat.numberOfPaths,
        loopStartTime = loopStart,
        loopEndTime = loopEnd,
        pathPermOrder = order,
        propPermOrder = pat.periodWithProps,
        sampleTimes = sampleTimes,
        jugglers = jugglers,
        hands = hands,
        props = props
    )
}
