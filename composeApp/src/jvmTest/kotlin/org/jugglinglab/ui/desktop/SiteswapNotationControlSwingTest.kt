//
// SiteswapNotationControlSwingTest.kt
//
// Copyright 2002-2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiteswapNotationControlSwingTest {
    @Test
    fun testBpsHandlingWithSpaces() {
        val control = SiteswapNotationControlSwing()

        // Empty by default -> bps should not be set in parameterList
        assertNull(control.parameterList.getParameter("bps"))

        // Spaces only should appear as no input
        control.tf3.text = "   "
        assertNull(control.parameterList.getParameter("bps"))

        // Value with whitespace should be trimmed
        control.tf3.text = "  4.5  "
        assertEquals("4.5", control.parameterList.getParameter("bps"))

        // Invalid number should still be passed through to produce the user error
        control.tf3.text = "abc"
        assertEquals("abc", control.parameterList.getParameter("bps"))
    }
}
