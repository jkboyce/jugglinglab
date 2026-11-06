package org.jugglinglab.renderer

import androidx.compose.ui.geometry.Offset
import org.jugglinglab.notation.SequencePreviewBuilder
import kotlin.math.hypot
import kotlin.test.*

class HeldPropLayoutTest {
    @Test fun heldInventoryIsGroupedByTheActualHand() {
        val preview = SequencePreviewBuilder.build("55555", finite = true)
        val hands = HeldPropLayout.hands(preview.pattern, preview.window.start - 0.01)
        assertEquals(listOf(2, 3), hands.map { it.paths.size }.sorted())
        assertEquals(5, hands.flatMap { it.paths }.distinct().size)
    }
    @Test fun airborneBallsAreNotAddedToTheHeldFan() {
        val preview = SequencePreviewBuilder.build("55555", finite = true)
        val time = preview.window.start + 0.1
        val held = HeldPropLayout.hands(preview.pattern, time).flatMap { it.paths }
        assertEquals(4, held.size)
        assertEquals(Offset.Zero, HeldPropLayout.offset(0, 1, 20f, 1f))
    }
    @Test fun everyBallInAHandRemainsIndividuallyVisible() {
        for (count in 2..16) for (direction in listOf(-1f, 1f)) {
            val offsets = (0 until count).map { HeldPropLayout.offset(it, count, 20f, direction) }
            assertEquals(Offset.Zero, offsets.first())
            for (i in offsets.indices) for (j in 0 until i) {
                assertTrue(hypot((offsets[i].x - offsets[j].x).toDouble(),
                    (offsets[i].y - offsets[j].y).toDouble()) >= 20.0,
                    "Held balls $i and $j overlap for inventory $count")
            }
        }
    }
}

class HeldPropCatchMotionTest {
    private fun offsets(motion: HeldPropLayout.Motion, time: Double): Map<Int, Offset> =
        motion.frame(time).flatMap { it.offsets.entries }.associate { it.key to it.value }

    @Test fun catchesAndThrowsHaveNoDisplayPositionJump() {
        for (notation in listOf("357", "55555", "51", "(4,2x)*", "24[54]", "(4,2)(2x,[44x])", "<3p|3p>")) {
            val preview = SequencePreviewBuilder.build(notation, finite = false)
            val motion = HeldPropLayout.Motion(preview.pattern)
            val events = preview.pattern.layout.pathLinks.flatten().flatMap { listOf(it.startEvent.t, it.endEvent.t) }
                .filter { it > preview.window.start && it < preview.window.end }.distinct()
            for (t in events) {
                val before = offsets(motion, t - 0.0000001)
                val after = offsets(motion, t + 0.0000001)
                for (path in before.keys + after.keys) {
                    val delta = (before[path] ?: Offset.Zero) - (after[path] ?: Offset.Zero)
                    assertTrue(delta.getDistance() < 0.001f, "$notation path $path jumps at $t")
                }
            }
        }
    }
    @Test fun incomingBallStaysAtHandWhileResidentMovesAside() {
        val preview = SequencePreviewBuilder.build("357", finite = false)
        val motion = HeldPropLayout.Motion(preview.pattern)
        val firstFlight = preview.pattern.layout.pathLinks.first().first { !it.isInHand && it.startEvent.t >= preview.window.start - 0.000001 }
        val catch = firstFlight.endEvent.t
        val incoming = offsets(motion, catch)[1] ?: Offset.Zero
        assertEquals(Offset.Zero, incoming)
        val resident = (2..preview.pattern.numberOfPaths).first { path ->
            preview.pattern.layout.isHandHoldingPath(firstFlight.endEvent.juggler, firstFlight.endEvent.hand, catch - 0.15, path) &&
                preview.pattern.layout.isHandHoldingPath(firstFlight.endEvent.juggler, firstFlight.endEvent.hand, catch + 0.01, path)
        }
        val early = offsets(motion, catch - 0.15)[resident] ?: Offset.Zero
        val middle = offsets(motion, catch - 0.02)[resident] ?: Offset.Zero
        val landed = offsets(motion, catch + 0.001)[resident] ?: Offset.Zero
        assertTrue(middle.x > early.x, "Resident must make room before the catch")
        assertTrue(landed.x >= middle.x)
    }
    @Test fun residentBallsLeaveTheReleasePointClear() {
        val preview = SequencePreviewBuilder.build("357", finite = false)
        val motion = HeldPropLayout.Motion(preview.pattern)
        val first = preview.pattern.layout.pathLinks.first().first { !it.isInHand && it.startEvent.t >= preview.window.start - 0.000001 }
        val source = motion.frame(first.startEvent.t + 0.0000001).first {
            it.juggler == first.startEvent.juggler && it.hand == first.startEvent.hand
        }
        assertTrue(source.offsets.isNotEmpty())
        assertTrue(source.offsets.values.all { it.getDistance() > 0.5f }, "Residents must make room for the departing ball")
    }
    @Test fun scrubbingProducesTheSamePlacementInEitherDirection() {
        val preview = SequencePreviewBuilder.build("357", finite = false)
        val motion = HeldPropLayout.Motion(preview.pattern)
        val time = preview.window.start + 0.3
        val original = motion.frame(time)
        motion.frame(preview.window.end)
        motion.frame(preview.window.start)
        assertEquals(original, motion.frame(time))
    }
    @Test fun initialWaitingInventoryDoesNotHideTheLater3555555Conflict() {
        val loop = SequencePreviewBuilder.plan("3555555", finite = false)
        assertEquals(10, loop.conflict?.beat)
        assertEquals(2, loop.conflict?.arrivals)
        assertEquals(1, loop.conflict?.capacity)
        assertNull(SequencePreviewBuilder.plan("3555555", finite = true).conflict)
    }
    @Test fun repeating357WarnsAtBeatSevenWhileFinite357CanFinish() {
        assertEquals(6, SequencePreviewBuilder.plan("357", finite = false).conflict?.beat)
        assertNull(SequencePreviewBuilder.plan("357", finite = true).conflict)
    }
}
