//
// MhnPatternTest.kt
//
// Unit tests for MhnPattern.
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import org.jugglinglab.jml.JmlEvent
import org.jugglinglab.jml.JmlPattern
import org.jugglinglab.jml.JmlTransition
import org.jugglinglab.util.JuggleExceptionUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MhnPatternTest {
    // utility function for testing JML creation.

    private fun trimmedJml(input: String): String {
        var result = input
        val startIndex = result.indexOf("<setup")
        if (startIndex >= 0) {
            result = result.substring(startIndex)
        }
        val endIndex = result.indexOf("</pattern")
        if (endIndex >= 0) {
            result = result.substring(0, endIndex)
        }
        return result
    }

    @Test
    fun `jml creation 1`() {
        val pattern = SiteswapPattern().fromString(
            "pattern=242334;bps=5;dwell=1;hands=(25,-15)(25,-15).(25)(0).(25,65)(25,65).(0)(15).(-25,65)(12.5,20).(15)(25)."
        )
        val expected = "<setup jugglers=\"1\" paths=\"3\" props=\"1,1,1\"/>\n" +
            "<symmetry type=\"delay\" pperm=\"(1,3,2)\" delay=\"1.2\"/>\n" +
            "<event x=\"25\" y=\"0\" z=\"-15\" t=\"0\" hand=\"1:right\">\n" +
            "<holding path=\"1\"/>\n" +
            "</event>\n" +
            "<event x=\"-25\" y=\"0\" z=\"0\" t=\"0\" hand=\"1:left\">\n" +
            "<catch path=\"2\"/>\n" +
            "</event>\n" +
            "<event x=\"25\" y=\"0\" z=\"-15\" t=\"0.2\" hand=\"1:right\">\n" +
            "<holding path=\"1\"/>\n" +
            "</event>\n" +
            "<event x=\"-25\" y=\"0\" z=\"0\" t=\"0.2\" hand=\"1:left\">\n" +
            "<throw path=\"2\" type=\"toss\"/>\n" +
            "</event>\n" +
            "<event x=\"25\" y=\"0\" z=\"65\" t=\"0.4\" hand=\"1:right\">\n" +
            "<holding path=\"1\"/>\n" +
            "</event>\n" +
            "<event x=\"0\" y=\"0\" z=\"0\" t=\"0.4\" hand=\"1:left\">\n" +
            "<catch path=\"3\"/>\n" +
            "</event>\n" +
            "<event x=\"25\" y=\"0\" z=\"65\" t=\"0.6\" hand=\"1:right\">\n" +
            "<holding path=\"1\"/>\n" +
            "</event>\n" +
            "<event x=\"0\" y=\"0\" z=\"0\" t=\"0.6\" hand=\"1:left\">\n" +
            "<throw path=\"3\" type=\"toss\"/>\n" +
            "</event>\n" +
            "<event x=\"-25\" y=\"0\" z=\"65\" t=\"0.8\" hand=\"1:right\">\n" +
            "<throw path=\"1\" type=\"toss\"/>\n" +
            "</event>\n" +
            "<event x=\"-15\" y=\"0\" z=\"0\" t=\"0.8\" hand=\"1:left\">\n" +
            "<catch path=\"2\"/>\n" +
            "</event>\n" +
            "<event x=\"12.5\" y=\"0\" z=\"20\" t=\"1\" hand=\"1:right\">\n" +
            "<catch path=\"3\"/>\n" +
            "</event>\n" +
            "<event x=\"-15\" y=\"0\" z=\"0\" t=\"1\" hand=\"1:left\">\n" +
            "<throw path=\"2\" type=\"toss\"/>\n" +
            "</event>\n"

        assertEquals(expected, trimmedJml(pattern.asJmlPattern().toString()))
    }

    @Test
    fun `bps limits`() {
        // verify bps = 200 is allowed and does not throw
        SiteswapPattern().fromString("pattern=3;bps=200").asJmlPattern()

        // verify bps = 201 is rejected with JuggleExceptionUser
        assertFailsWith<JuggleExceptionUser> {
            SiteswapPattern().fromString("pattern=3;bps=201").asJmlPattern()
        }

        // verify bps = 0.05 is allowed and does not throw
        SiteswapPattern().fromString("pattern=3;bps=0.05").asJmlPattern()

        // verify bps = 0.049 is rejected with JuggleExceptionUser
        assertFailsWith<JuggleExceptionUser> {
            SiteswapPattern().fromString("pattern=3;bps=0.049").asJmlPattern()
        }

        // verify bps = 0 (and negative values) default and do not throw
        SiteswapPattern().fromString("pattern=3;bps=0").asJmlPattern()
        SiteswapPattern().fromString("pattern=3;bps=-1").asJmlPattern()

        // verify bps with only spaces or empty string defaults and does not throw
        SiteswapPattern().fromString("pattern=3;bps=   ").asJmlPattern()
        SiteswapPattern().fromString("pattern=3;bps=").asJmlPattern()

        // verify bps with surrounding whitespace is parsed properly
        SiteswapPattern().fromString("pattern=3;bps=  4.5  ").asJmlPattern()
    }

    @Test
    fun `test passing pattern multiplex invalid symmetry`() {
        assertFailsWith<JuggleExceptionUser> {
            val pattern = SiteswapPattern().fromString("<(4p,4p)|(4p,0p)><(4,0)|(4,4)>")
            pattern.asJmlPattern()
        }
    }

    @Test
    fun `step 12 reorder transitions`() {
        val throw1 = JmlTransition(type = JmlTransition.TRANS_THROW, path = 1)
        val hold2 = JmlTransition(type = JmlTransition.TRANS_HOLDING, path = 2)
        val catch3 = JmlTransition(type = JmlTransition.TRANS_CATCH, path = 3)
        val hold4 = JmlTransition(type = JmlTransition.TRANS_HOLDING, path = 4)
        val throw5 = JmlTransition(type = JmlTransition.TRANS_THROW, path = 5)

        val ev1 = JmlEvent(t = 0.0, transitions = listOf(throw1, hold2, catch3, hold4, throw5))
        val ev2 = JmlEvent(t = 1.0, transitions = listOf(hold2, hold4))
        val ev3 = JmlEvent(t = 2.0, transitions = listOf(throw1, catch3))
        val ev4 = JmlEvent(t = 3.0, transitions = emptyList())

        val pat = JmlPattern(numberOfJugglers = 1, numberOfPaths = 5, events = listOf(ev1, ev2, ev3, ev4))
        val helper = object : MhnPattern() {
            override val notationName: String = "test"
            override fun fromString(config: String): Pattern = this
            fun testReorder(p: JmlPattern) = reorderTransitions(p)
        }

        val reordered = helper.testReorder(pat)
        assertEquals(
            listOf(hold2, hold4, throw1, catch3, throw5),
            reordered.events[0].transitions
        )
        assertEquals(listOf(hold2, hold4), reordered.events[1].transitions)
        assertEquals(listOf(throw1, catch3), reordered.events[2].transitions)
        assertEquals(emptyList(), reordered.events[3].transitions)
    }

    @Test
    fun `step 12 multiplex throw path ordering matches previous event for hand`() {
        val pattern = SiteswapPattern().fromString("[42][62][21][33][11]").asJmlPattern()
        // Event for [11] is in right hand at t ~ 0.99
        // Previous event for right hand touched path 2 (holding) then path 1 (catch)
        val event11 = pattern.events.first { ev ->
            ev.hand == JmlEvent.RIGHT_HAND && ev.transitions.count { it.type == JmlTransition.TRANS_THROW } == 2
        }
        val throwPaths = event11.transitions.filter { it.type == JmlTransition.TRANS_THROW }.map { it.path }
        assertEquals(listOf(2, 1), throwPaths)

        val patLoop = SiteswapPattern().fromString("[b9753]0020[22]0[222]0[2222]0").asJmlPattern()
        val ev0Throws = patLoop.events[0].transitions.filter { it.type == JmlTransition.TRANS_THROW }.map { it.path }
        val ev0PrevPaths = patLoop.prevForHandFromEvent(patLoop.events[0]).event.transitions.map { it.path }
        assertEquals(ev0PrevPaths, ev0Throws)
    }

    @Test
    fun `equal-valued throws in multiplex have first throw higher than subsequent throws`() {
        val pat = SiteswapPattern().fromString("[42][52][33]42")
        pat.asJmlPattern() // calculates throw and catch times

        // At beat index 2, [33] is thrown:
        val first3 = pat.th[0][0][2][0]!!
        val second3 = pat.th[0][0][2][1]!!

        val firstAirtime = first3.target!!.catchTime - first3.throwTime
        val secondAirtime = second3.target!!.catchTime - second3.throwTime

        // The first 3 must have greater airtime (higher throw) than the second 3
        assertEquals(true, firstAirtime > secondAirtime, "first 3 airtime ($firstAirtime) should be > second 3 airtime ($secondAirtime)")
        assertEquals(1, first3.target!!.catchNum)
        assertEquals(0, second3.target!!.catchNum)
    }

    @Test
    fun `step 12 holding transitions precede non-holding in generated patterns`() {
        val patterns = listOf(
            "24[504]",
            "([42],4x)*",
            "pattern=242334;bps=5;dwell=1;hands=(25,-15)(25,-15).(25)(0).(25,65)(25,65).(0)(15).(-25,65)(12.5,20).(15)(25)."
        )
        for (patStr in patterns) {
            val jml = SiteswapPattern().fromString(patStr).asJmlPattern()
            for (ev in jml.events) {
                var seenNonHolding = false
                for (tr in ev.transitions) {
                    if (tr.type == JmlTransition.TRANS_HOLDING) {
                        assertEquals(false, seenNonHolding, "Found <holding> transition after non-<holding> transition in event $ev")
                    } else {
                        seenNonHolding = true
                    }
                }
            }
        }
    }
}
