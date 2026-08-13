//
// HandSiteswapTest.kt
//
// Unit tests for HandSiteswap.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.notation

import org.jugglinglab.composeapp.generated.resources.*
import org.jugglinglab.util.JuggleExceptionUser
import org.jugglinglab.util.jlGetStringResource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HandSiteswapTest {
    @Suppress("SameParameterValue")
    private fun assertHssResult(
        objectPattern: String,
        handPattern: String,
        hold: Boolean,
        dwellMax: Boolean,
        handspec: String?,
        expectedPattern: String,
        expectedDwellBeats: DoubleArray
    ) {
        val result = processHandSiteswap(
            objectPatternString = objectPattern,
            handPatternString = handPattern,
            hold = hold,
            dwellmax = dwellMax,
            handspec = handspec,
            dwell = 1.0
        )
        assertEquals(expectedPattern, result.convertedPattern)
        assertContentEquals(expectedDwellBeats, result.dwellBeatsArray)
    }

    @Test
    fun `HSS two handed snake dwellmax false`() {
        // Source: patterns/hss_TwoHandedPatterns.jml (3; hss=114; dwellmax=false)
        assertHssResult(
            objectPattern = "3",
            handPattern = "114",
            hold = false,
            dwellMax = false,
            handspec = null,
            expectedPattern = "<(0,3 )!><(0,3 )!><(0,3 )!><(3 ,0)!><(3 ,0)!><(3 ,0)!>",
            expectedDwellBeats = doubleArrayOf(0.3, 0.3, 0.3, 0.3, 0.3, 0.3)
        )
    }

    @Test
    fun `HSS two handed 3 with handspec`() {
        // Source: patterns/hss_TwoHandedPatterns.jml (3; hss=2; handspec=(1,)(,2))
        assertHssResult(
            objectPattern = "3",
            handPattern = "2",
            hold = false,
            dwellMax = true,
            handspec = "(1,)(,2)",
            expectedPattern = "<(3p2 ,0)!|(0,0)!><(0,0)!|(0,3p1 )!>",
            expectedDwellBeats = doubleArrayOf(1.3, 1.3)
        )
    }

    @Test
    fun `HSS two handed 42 with handspec`() {
        // Source: patterns/hss_TwoHandedPatterns.jml (42; hss=13; handspec=(1,)(,2))
        assertHssResult(
            objectPattern = "42",
            handPattern = "13",
            hold = false,
            dwellMax = true,
            handspec = "(1,)(,2)",
            expectedPattern = "<(4 ,0)!|(0,0)!><(2xp2 ,0)!|(0,0)!><(0,0)!|(0,4 )!><(0,0)!|(0,2xp1 )!>",
            expectedDwellBeats = doubleArrayOf(2.3, 0.30000000000000004, 2.3, 0.30000000000000004)
        )
    }

    @Test
    fun `HSS two handed 5 balls`() {
        // Source: patterns/hss_TwoHandedPatterns.jml (5; hss=1124)
        assertHssResult(
            objectPattern = "5",
            handPattern = "1124",
            hold = false,
            dwellMax = true,
            handspec = null,
            expectedPattern = "<(0,5x )!><(0,5x )!><(0,5 )!><(5 ,0)!>",
            expectedDwellBeats = doubleArrayOf(1.3, 0.30000000000000004, 0.30000000000000004, 3.3)
        )
    }

    @Test
    fun `HSS 2 jugglers symmetric parsnip`() {
        // Source: patterns/hss_2JugglersSymmetric.jml (77722; hss=4)
        assertHssResult(
            objectPattern = "77722",
            handPattern = "4",
            hold = false,
            dwellMax = true,
            handspec = null,
            expectedPattern = "<(0,7p2 )!|(0,0)!><(0,0)!|(0,7xp1 )!><(7p2 ,0)!|(0,0)!><(0,0)!|(2x ,0)!><(0,2x )!|(0,0)!><(0,0)!|(0,7xp1 )!><(7p2 ,0)!|(0,0)!><(0,0)!|(7xp1 ,0)!><(0,2x )!|(0,0)!><(0,0)!|(0,2x )!><(7p2 ,0)!|(0,0)!><(0,0)!|(7xp1 ,0)!><(0,7p2 )!|(0,0)!><(0,0)!|(0,2x )!><(2x ,0)!|(0,0)!><(0,0)!|(7xp1 ,0)!><(0,7p2 )!|(0,0)!><(0,0)!|(0,7xp1 )!><(2x ,0)!|(0,0)!><(0,0)!|(2x ,0)!>",
            expectedDwellBeats = doubleArrayOf(
                1.6, 1.6, 3.3, 3.3, 3.3, 1.6, 1.6, 3.3, 3.3, 3.3,
                1.6, 1.6, 3.3, 3.3, 3.3, 1.6, 1.6, 3.3, 3.3, 3.3
            )
        )
    }

    @Test
    fun `HSS 2 jugglers symmetric tramline`() {
        // Source: patterns/hss_2JugglersSymmetric.jml (5; hss=22286; handspec=(2,1)(3,4))
        assertHssResult(
            objectPattern = "5",
            handPattern = "22286",
            hold = false,
            dwellMax = true,
            handspec = "(2,1)(3,4)",
            expectedPattern = "<(0,5p2 )!|(0,0)!><(5p2 ,0)!|(0,0)!><(0,5p2 )!|(0,0)!><(5p2 ,0)!|(0,0)!><(0,5p2 )!|(0,0)!><(0,0)!|(5p1 ,0)!><(0,0)!|(0,5p1 )!><(0,0)!|(5p1 ,0)!><(0,0)!|(0,5p1 )!><(0,0)!|(5p1 ,0)!>",
            expectedDwellBeats = doubleArrayOf(4.3, 4.3, 1.3, 1.3, 1.3, 4.6, 4.6, 1.6, 1.6, 1.3)
        )
    }

    @Test
    fun `HSS 2 jugglers asymmetric five count popcorn`() {
        // Source: patterns/hss_2JugglersAsymmetric.jml (7862678682; hss=4)
        assertHssResult(
            objectPattern = "7862678682",
            handPattern = "4",
            hold = false,
            dwellMax = true,
            handspec = null,
            expectedPattern = "<(0,7p2 )!|(0,0)!><(0,0)!|(0,8 )!><(6x ,0)!|(0,0)!><(0,0)!|(2x ,0)!><(0,6x )!|(0,0)!><(0,0)!|(0,7xp1 )!><(8 ,0)!|(0,0)!><(0,0)!|(6x ,0)!><(0,8 )!|(0,0)!><(0,0)!|(0,2x )!><(7p2 ,0)!|(0,0)!><(0,0)!|(8 ,0)!><(0,6x )!|(0,0)!><(0,0)!|(0,2x )!><(6x ,0)!|(0,0)!><(0,0)!|(7xp1 ,0)!><(0,8 )!|(0,0)!><(0,0)!|(0,6x )!><(8 ,0)!|(0,0)!><(0,0)!|(2x ,0)!>",
            expectedDwellBeats = doubleArrayOf(
                3.3, 1.6, 3.3, 3.3, 3.3, 1.6, 3.3, 3.3, 3.3, 3.3,
                3.3, 1.6, 3.3, 3.3, 3.3, 1.6, 3.3, 3.3, 3.3, 3.3
            )
        )
    }

    @Test
    fun `HSS 2 jugglers polyrhythm two count vs one count`() {
        // Source: patterns/hss_2JugglersAsymmetric.jml (7b06; hss=8404; handspec=(4,1)(3,2))
        assertHssResult(
            objectPattern = "7b06",
            handPattern = "8404",
            hold = false,
            dwellMax = true,
            handspec = "(4,1)(3,2)",
            expectedPattern = "<(0,7p2 )!|(0,0)!><(0,0)!|(0,bp1 )!><(0,0)!|(0,0)!><(0,0)!|(6x ,0)!><(7xp2 ,0)!|(0,0)!><(0,0)!|(0,bxp1 )!><(0,0)!|(0,0)!><(0,0)!|(6x ,0)!>",
            expectedDwellBeats = doubleArrayOf(7.3, 3.3, -0.7, 3.3, 7.3, 3.3, -0.7, 3.3)
        )
    }

    @Test
    fun `HSS 3 jugglers symmetric Martin one count triangle`() {
        // Source: patterns/hss_3JugglersSymmetric.jml (aabb3; hss=6; handspec=(4,1)(2,5)(6,3))
        assertHssResult(
            objectPattern = "aabb3",
            handPattern = "6",
            hold = false,
            dwellMax = true,
            handspec = "(4,1)(2,5)(6,3)",
            expectedPattern = "<(0,ap2 )!|(0,0)!|(0,0)!><(0,0)!|(ap3 ,0)!|(0,0)!><(0,0)!|(0,0)!|(0,bp2 )!><(bp3 ,0)!|(0,0)!|(0,0)!><(0,0)!|(0,3 )!|(0,0)!><(0,0)!|(0,0)!|(ap1 ,0)!><(0,ap2 )!|(0,0)!|(0,0)!><(0,0)!|(bp1 ,0)!|(0,0)!><(0,0)!|(0,0)!|(0,bp2 )!><(3 ,0)!|(0,0)!|(0,0)!><(0,0)!|(0,ap3 )!|(0,0)!><(0,0)!|(0,0)!|(ap1 ,0)!><(0,bp3 )!|(0,0)!|(0,0)!><(0,0)!|(bp1 ,0)!|(0,0)!><(0,0)!|(0,0)!|(0,3 )!><(ap2 ,0)!|(0,0)!|(0,0)!><(0,0)!|(0,ap3 )!|(0,0)!><(0,0)!|(0,0)!|(bp2 ,0)!><(0,bp3 )!|(0,0)!|(0,0)!><(0,0)!|(3 ,0)!|(0,0)!><(0,0)!|(0,0)!|(0,ap1 )!><(ap2 ,0)!|(0,0)!|(0,0)!><(0,0)!|(0,bp1 )!|(0,0)!><(0,0)!|(0,0)!|(bp2 ,0)!><(0,3 )!|(0,0)!|(0,0)!><(0,0)!|(ap3 ,0)!|(0,0)!><(0,0)!|(0,0)!|(0,ap1 )!><(bp3 ,0)!|(0,0)!|(0,0)!><(0,0)!|(0,bp1 )!|(0,0)!><(0,0)!|(0,0)!|(3 ,0)!>",
            expectedDwellBeats = doubleArrayOf(
                5.3, 5.3, 2.3, 5.3, 5.3, 5.6, 5.3, 2.3, 5.3, 5.3,
                5.6, 5.3, 2.3, 5.3, 5.3, 5.6, 5.3, 2.3, 5.3, 5.3,
                5.6, 5.3, 2.3, 5.3, 5.3, 5.6, 5.3, 2.5999999999999996, 5.3, 5.3
            )
        )
    }

    @Test
    fun `HSS 3 jugglers polyrhythm one count feeding three count`() {
        // Source: patterns/hss_3JugglersAsymmetric.jml (dh0c0c; hss=c80808; handspec=(5,1)(4,2)(6,3))
        assertHssResult(
            objectPattern = "dh0c0c",
            handPattern = "c80808",
            hold = false,
            dwellMax = true,
            handspec = "(5,1)(4,2)(6,3)",
            expectedPattern = "<(0,dp2 )!|(0,0)!|(0,0)!><(0,0)!|(0,hp1 )!|(0,0)!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(0,0)!|(0,cx )!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(cx ,0)!|(0,0)!><(dp3 ,0)!|(0,0)!|(0,0)!><(0,0)!|(0,0)!|(hp1 ,0)!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(0,cx )!|(0,0)!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(0,0)!|(0,cx )!><(0,dxp2 )!|(0,0)!|(0,0)!><(0,0)!|(hxp1 ,0)!|(0,0)!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(0,0)!|(cx ,0)!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(0,cx )!|(0,0)!><(dxp3 ,0)!|(0,0)!|(0,0)!><(0,0)!|(0,0)!|(0,hxp1 )!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(cx ,0)!|(0,0)!><(0,0)!|(0,0)!|(0,0)!><(0,0)!|(0,0)!|(cx ,0)!>",
            expectedDwellBeats = doubleArrayOf(
                11.3, 7.3, -0.7, 7.3, -0.7, 7.3, 11.3, 7.3, -0.7, 7.3,
                -0.7, 7.3, 11.3, 7.3, -0.7, 7.3, -0.7, 7.3, 11.3, 7.3,
                -0.7, 7.3, -0.7, 7.3
            )
        )
    }

    @Test
    fun `HSS object pattern syntax error at pos`() {
        val exception = assertFailsWith<JuggleExceptionUser> {
            processHandSiteswap(
                objectPatternString = "(2,2)",
                handPatternString = "4",
                hold = false,
                dwellmax = false,
                handspec = null,
                dwell = 1.0
            )
        }
        assertEquals(
            jlGetStringResource(Res.string.error_hss_object_syntax_error_at_pos, 1),
            exception.message
        )
    }
}
