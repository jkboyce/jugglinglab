package org.jugglinglab.notation

import kotlin.test.*

class SequencePreviewTest {
    @Test fun finiteInputDoesNotNeedAnIntegerAverage() {
        val p = SiteswapPattern().inspectThrows("345", finite = true)
        assertEquals(3, p.beats)
        assertTrue(p.beatsPerSecond > 2.0)
        assertEquals(3.0, SiteswapPattern().inspectThrows("pattern=345;bps=3").beatsPerSecond)
        assertEquals(listOf(3, 5, 7), p.throws.filter { !it.isZero }.map { it.targetIndex })
        assertEquals(3, SiteswapPattern().inspectThrows("531", finite = true).beats)
        assertEquals(6, SiteswapPattern().inspectThrows("531", finite = false).beats)
    }
    @Test fun nativeSyncMultiplexAndPassingArePreserved() {
        for (s in listOf("(4,4)", "(4x,4x)", "(4,2x)*", "24[54]", "(4,2)(2x,[44x])",
            "<3p|3p>", "<3p 3|3p 3>", "<(4x,4xp)|(4x,4xp)>",
            "<(2p3,4x)|(2xp3,4p1)|(2xp2,4xp2)>", "<R|L><4xp|3><3|4xp>")) {
            assertNull(SequencePreviewBuilder.plan(s, finite = false).conflict, s)
        }
    }
    @Test fun identifiesFirstConflictAndItsHand() {
        val p = SequencePreviewBuilder.plan("32", finite = false)
        assertEquals(3, p.conflict?.beat)
        assertEquals(1, p.conflict?.juggler)
        assertEquals(1, p.conflict?.hand)
        assertTrue(p.throws.all { it.index < p.stopBeat })
        assertEquals(3, SequencePreviewBuilder.plan("3330", finite = true).conflict?.beat)
        assertEquals(4, SequencePreviewBuilder.plan("41", finite = false).conflict?.beat)
    }
    @Test fun restBeatsAndShortSyncBeatsHaveNativeTiming() {
        assertEquals(4, SiteswapPattern().inspectThrows("(4,4)(4,4)").beats)
        assertEquals(2, SiteswapPattern().inspectThrows("(4,4)!(4,4)!").beats)
        assertEquals(4, SiteswapPattern().inspectThrows("(4,2x)*").beats)
    }
    @Test fun previewLayoutsUseTheNativeJmlEngine() {
        for (s in listOf("345", "531", "(4,4)", "(4,2x)*", "24[54]", "(4,2)(2x,[44x])", "<3p|3p>", "0", "2")) {
            val p = SequencePreviewBuilder.build(s, finite = true)
            p.pattern.assertValid()
            p.pattern.layout
            assertTrue(p.window.end < p.pattern.loopEndTime, s)
            assertTrue(p.window.start < p.window.end, s)
        }
        val failed = SequencePreviewBuilder.build("32", finite = false)
        failed.pattern.assertValid()
        failed.pattern.layout
        assertEquals(3, failed.window.conflict?.beat)
    }
    @Test fun playbackBoundsSurviveTimingEditsAndReplay() {
        val preview = SequencePreviewBuilder.build("345", finite = true)
        val state = org.jugglinglab.core.PatternAnimationState(preview.pattern, org.jugglinglab.core.AnimationPrefs())
        state.previewWindow = preview.window
        state.update(pattern = preview.pattern.withScaledTime(2.0))
        assertEquals(preview.window.end * 2.0, state.previewWindow!!.end)
        state.update(time = state.previewWindow!!.end, isPaused = true)
        state.togglePlayback()
        assertEquals(state.previewWindow!!.start, state.time)
        assertFalse(state.isPaused)
    }
    @Test fun ordinaryValidationStillRejectsInvalidLoops() {
        assertFails { SiteswapPattern().fromString("32") }
    }
}
