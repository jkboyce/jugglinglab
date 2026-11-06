// Finite and invalid-pattern inspection using Juggling Lab's native JML layout/rendering.
// Copyright 2026 Siteswap Lab contributors. Licensed under the project's GPL.
package org.jugglinglab.notation

import org.jugglinglab.jml.*
import org.jugglinglab.util.ParameterList
import org.jugglinglab.util.Permutation
import kotlin.math.max

data class PreviewConflict(val beat: Int, val juggler: Int, val hand: Int, val arrivals: Int, val capacity: Int) {
    val message: String get() = "Beat ${beat + 1}, juggler $juggler, ${if (hand == 0) "right" else "left"} hand: $arrivals arriving ball(s), $capacity scheduled catch slot(s)."
}
data class PreviewWindow(val start: Double, val end: Double, val beats: Int, val secondsPerBeat: Double, val conflict: PreviewConflict? = null)
data class SequencePreview(val pattern: JmlPattern, val window: PreviewWindow)
data class PreviewPlan(val inspection: SiteswapThrowInspection, val throws: List<MhnThrow>, val stopBeat: Int, val conflict: PreviewConflict?)

object SequencePreviewBuilder {
    private data class Site(val beat: Int, val juggler: Int, val hand: Int)
    private data class Ball(val segments: MutableList<MhnThrow> = mutableListOf())

    fun plan(config: String, finite: Boolean): PreviewPlan {
        val parsed = SiteswapPattern().inspectThrows(config, finite)
        val n = parsed.beats
        val positive = parsed.throws.filter { !it.isZero }
        val capacities = positive.groupingBy { Site(it.index, it.juggler, it.hand) }.eachCount()
        val maxThrow = positive.maxOfOrNull { it.throwValue } ?: 0
        val horizon = if (finite) n else 2 * n + maxThrow
        val throws = mutableListOf<MhnThrow>()
        var offset = 0
        while (offset < horizon) {
            for (t in positive) if (t.index + offset < horizon) {
                throws.add(t.copy(index = t.index + offset, targetIndex = t.targetIndex + offset))
            }
            if (finite) break
            offset += n
        }
        val landings = throws.groupBy { Site(it.targetIndex, it.targetJuggler, it.targetHand) }
        var conflict: PreviewConflict? = null
        for ((site, incoming) in landings) {
            val capacity = if (!finite || site.beat < n) {
                capacities[site.copy(beat = site.beat % n)] ?: 0
            } else {
                // A final multiplex may land together even without a later throw.
                max(1, incoming.groupingBy { Site(it.index, it.juggler, it.hand) }.eachCount().values.maxOrNull() ?: 1)
            }
            if (incoming.size > capacity && (conflict == null || site.beat < conflict.beat)) {
                conflict = PreviewConflict(site.beat, site.juggler, site.hand, incoming.size, capacity)
            }
        }
        val stop = conflict?.beat ?: max(n, positive.maxOfOrNull { it.targetIndex } ?: n)
        return PreviewPlan(parsed, throws.filter { it.index < (conflict?.beat ?: n) }, stop, conflict)
    }

    fun build(config: String, finite: Boolean): SequencePreview {
        val plan = plan(config, finite)
        val pl = ParameterList(if ('=' in config) config else "pattern=$config")
        val bps = plan.inspection.beatsPerSecond
        require(bps in 0.25..10.0) { "Preview speed must be between 0.25 and 10 beats per second." }
        val balls = mutableListOf<Ball>()
        val arrivals = mutableMapOf<Site, MutableList<Ball>>()
        for (t in plan.throws.sortedWith(compareBy({ it.index }, { it.juggler }, { it.hand }, { it.slot }))) {
            val source = Site(t.index, t.juggler, t.hand)
            val queue = arrivals[source]
            val ball = if (!queue.isNullOrEmpty()) queue.removeAt(0) else Ball().also { balls.add(it) }
            ball.segments.add(t)
            arrivals.getOrPut(Site(t.targetIndex, t.targetJuggler, t.targetHand)) { mutableListOf() }.add(ball)
        }
        val maxEnd = max(plan.stopBeat, plan.throws.maxOfOrNull { it.targetIndex } ?: plan.stopBeat)
        val lead = 0.6
        val loopSeconds = (maxEnd + lead + 9.0) / bps
        val record = PatternBuilder(
            title = "Sequence: ${pl.getParameter("pattern")}",
            basePatternNotation = "siteswap", basePatternConfig = config,
            numberOfJugglers = plan.inspection.jugglers, numberOfPaths = balls.size,
            props = if (balls.isEmpty()) mutableListOf() else mutableListOf(JmlProp(pl.getParameter("prop") ?: "ball", null)),
            propAssignment = MutableList(balls.size) { 1 },
            symmetries = mutableListOf(JmlSymmetry(JmlSymmetry.TYPE_DELAY, plan.inspection.jugglers, balls.size,
                Permutation(plan.inspection.jugglers, true), Permutation(balls.size, false), loopSeconds))
        )
        data class EventKey(val tick: Long, val juggler: Int, val hand: Int)
        val events = mutableMapOf<EventKey, JmlEvent>()
        fun add(beat: Double, j: Int, h: Int, path: Int?, type: Int, throwMod: String? = null) {
            val seconds = beat / bps
            val k = EventKey((seconds * 1000000).toLong(), j, h)
            val existing = events[k]
            val x = (if (h == 0) 1 else -1) * (if (type == JmlTransition.TRANS_THROW) 12.0 else 30.0)
            val ev = existing ?: JmlEvent(x = x, y = 0.0, z = 0.0, t = seconds, juggler = j,
                hand = if (h == 0) JmlEvent.RIGHT_HAND else JmlEvent.LEFT_HAND)
            if (path == null) { events[k] = ev; return }
            val transition = JmlTransition(type, path,
                if (type == JmlTransition.TRANS_THROW) if (throwMod?.startsWith("B") == true) "bounce" else "toss" else null,
                if (throwMod?.startsWith("B") == true) "bounces=${throwMod.takeWhile { it == 'B' }.length}" else null)
            val old = ev.transitions.find { it.path == path }
            // The same ball can have a hold and a throw on a boundary; throw wins.
            events[k] = if (old == null) ev.withTransition(transition) else if (type == JmlTransition.TRANS_THROW) ev.withoutTransition(old).withTransition(transition) else ev
        }
        balls.forEachIndexed { i, ball ->
            val path = i + 1
            val first = ball.segments.first()
            add(0.0, first.juggler, first.hand, path, JmlTransition.TRANS_HOLDING)
            for (s in ball.segments) {
                if (s.mod == "H") {
                    add(s.index + lead, s.juggler, s.hand, path, JmlTransition.TRANS_HOLDING)
                } else {
                    add(s.index + lead, s.juggler, s.hand, path, JmlTransition.TRANS_THROW, s.mod)
                    add(s.targetIndex + lead - minOf(plan.inspection.dwellBeats, s.throwValue - 0.1), s.targetJuggler, s.targetHand, path, JmlTransition.TRANS_CATCH)
                }
            }
            val last = ball.segments.last()
            add(maxEnd + lead + 1.0, last.targetJuggler, last.targetHand, path, JmlTransition.TRANS_HOLDING)
            if (last.targetJuggler != first.juggler || last.targetHand != first.hand) {
                // Invisible closing tail only satisfies JML's periodic path representation.
                // Playback is clamped before this tail; the entered sequence never repeats.
                add(maxEnd + lead + 3.0, last.targetJuggler, last.targetHand, path, JmlTransition.TRANS_THROW)
                add(maxEnd + lead + 6.0, first.juggler, first.hand, path, JmlTransition.TRANS_CATCH)
            }
            add(maxEnd + lead + 8.0, first.juggler, first.hand, path, JmlTransition.TRANS_HOLDING)
        }
        for (j in 1..plan.inspection.jugglers) for (h in 0..1) {
            add(0.0, j, h, null, JmlTransition.TRANS_HOLDING)
            add(maxEnd + lead + 8.0, j, h, null, JmlTransition.TRANS_HOLDING)
        }
        record.events = events.values.sorted().toMutableList()
        PreviewHandMotion().complete(record)
        val pattern = JmlPattern.fromPatternBuilder(record)
        pattern.layout
        return SequencePreview(pattern, PreviewWindow(lead / bps, (plan.stopBeat + lead) / bps,
            plan.stopBeat + 1, 1.0 / bps, plan.conflict))
    }
}

// Reuse the native hand-motion gap completion, so catches cannot pull an idle
// hand through a long spline all the way to the invisible closing tail.
private class PreviewHandMotion : MhnPattern() {
    override val notationName = "siteswap"
    override fun fromString(config: String): Pattern = error("Preview hand motion is built from JML events")
    fun complete(record: PatternBuilder) {
        numberOfJugglers = record.numberOfJugglers
        val incomplete = mutableMapOf<JmlEvent, Boolean>()
        addEventsForGapsToJml(record, incomplete)
        addLocationsForIncompleteEventsToJml(record, incomplete)
        record.fixHolds()
        record.selectPrimaryEvents()
    }
}
