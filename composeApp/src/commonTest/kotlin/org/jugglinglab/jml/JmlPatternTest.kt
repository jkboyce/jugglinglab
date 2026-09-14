//
// JmlPatternTest.kt
//
// Unit tests for JmlPattern.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.jml

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.notation.SiteswapPattern
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlGetStringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JmlPatternTest {
    @Test
    fun `events too close in time throws error with hand name`() {
        val pat = SiteswapPattern().fromString("(4,4)").asJmlPattern()
        val rightEvent = pat.events.first { it.hand == JmlEvent.RIGHT_HAND }
        val leftEvent = pat.events.first { it.hand == JmlEvent.LEFT_HAND }

        // Test right hand events too close in time
        val duplicateRight = rightEvent.copy(t = rightEvent.t + 0.0001, transitions = emptyList())
        val patBadRight = pat.copy(events = (pat.events + duplicateRight).sortedBy { it.t })
        val exRight = assertFailsWith<JuggleExceptionUser> {
            patBadRight.assertValid()
        }
        val expectedRight = jlGetStringResource(
            Res.string.error_events_too_close_in_time,
            jlGetStringResource(Res.string.error_right),
            1
        )
        assertEquals(expectedRight, exRight.message)
        assertEquals("Events for right hand of juggler 1 are too close in time", exRight.message)

        // Test left hand events too close in time
        val duplicateLeft = leftEvent.copy(t = leftEvent.t + 0.0001, transitions = emptyList())
        val patBadLeft = pat.copy(events = (pat.events + duplicateLeft).sortedBy { it.t })
        val exLeft = assertFailsWith<JuggleExceptionUser> {
            patBadLeft.assertValid()
        }
        val expectedLeft = jlGetStringResource(
            Res.string.error_events_too_close_in_time,
            jlGetStringResource(Res.string.error_left),
            1
        )
        assertEquals(expectedLeft, exLeft.message)
        assertEquals("Events for left hand of juggler 1 are too close in time", exLeft.message)
    }

    @Test
    fun `loopEvents excludes events belonging to next loop iteration`() {
        val patterns = listOf(
            "(4,4)",
            "(2,6B)([8xB2],2)*",
            "[42][44][11][33]3"
        )
        for (patternStr in patterns) {
            val pat = SiteswapPattern().fromString(patternStr).asJmlPattern()
            for (ei in pat.loopEvents) {
                assertTrue(
                    ei.event.t >= pat.loopStartTime,
                    "Pattern $patternStr has loop event before loopStartTime: ${ei.event.t} < ${pat.loopStartTime}"
                )
                assertTrue(
                    ei.event.t < pat.loopEndTime,
                    "Pattern $patternStr has loop event at or after loopEndTime: ${ei.event.t} >= ${pat.loopEndTime}"
                )
            }
        }

        // Specifically verify (4,4) has exactly 4 events (throws at t=0 and catches at t~0.2059)
        // and does not include the 2 throws from the next loop at t=loopEndTime (~0.5882)
        val patSync = SiteswapPattern().fromString("(4,4)").asJmlPattern()
        assertEquals(4, patSync.loopEvents.size)
    }
}
