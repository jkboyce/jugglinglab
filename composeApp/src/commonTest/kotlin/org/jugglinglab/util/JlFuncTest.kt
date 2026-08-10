//
// JlFuncTest.kt
//
// Unit tests for common functions in JlFunc.kt.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.util

import kotlin.test.Test
import kotlin.test.assertEquals

class JlFuncTest {
    @Test
    fun `sanitize forbidden characters in filenames`() {
        // <0|0> title should sanitize forbidden characters <, >, |
        assertEquals("_0_0_.jml", jlSanitizeFilename("<0|0>.jml"))
        assertEquals("_0_0_.gif", jlSanitizeFilename("<0|0>.gif"))
        assertEquals("_0_0_.txt", jlSanitizeFilename("<0|0>.txt"))

        // Other forbidden characters : * ? " < > |
        assertEquals("a_b_c_d_e_f_g.jml", jlSanitizeFilename("a:b*c?d\"e<f>g.jml"))

        // Windows reserved names
        assertEquals("CON_pattern.jml", jlSanitizeFilename("CON.jml"))
        assertEquals("aux_pattern.txt", jlSanitizeFilename("aux.txt"))

        // Trimming dots and spaces
        assertEquals("pattern.jml", jlSanitizeFilename("  .pattern. .jml  "))

        // Empty filename fallback
        assertEquals("Pattern.jml", jlSanitizeFilename(".jml"))
        assertEquals("Pattern", jlSanitizeFilename(""))
    }
}
