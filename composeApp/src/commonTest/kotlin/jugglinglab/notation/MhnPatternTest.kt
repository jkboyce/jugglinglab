//
// MhnPatternTest.kt
//
// Unit tests for MhnPattern.
//
// Copyright 2002-2025 Jack Boyce and the Juggling Lab contributors
//

package jugglinglab.notation

import kotlin.test.Test
import kotlin.test.assertEquals

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

}
