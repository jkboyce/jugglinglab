//
// BuiltinPatternListsExtendedTest.kt
//
// Extended tests for built-in pattern list packaging and pattern creation.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import org.jugglinglab.composeapp.generated.resources.Res
import org.jugglinglab.jml.JmlParser
import org.jugglinglab.jml.JmlPatternList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuiltinPatternListsExtendedTest {
    @Test
    fun testBuiltinListsCounts() {
        assertEquals(4, BuiltinPatternLists.basic.size)
        assertEquals(17, BuiltinPatternLists.other.size)
    }

    @Test
    fun testAllBuiltinFilesCanBeReadAndParsed() = runTest {
        val allEntries = BuiltinPatternLists.basic + BuiltinPatternLists.other

        for ((filename, displayName) in allEntries) {
            assertTrue(filename.endsWith(".jml"), "Filename must end with .jml: $filename")
            assertTrue(displayName.isNotBlank(), "Display name must not be blank: $filename")

            val bytes = Res.readBytes("files/$filename")
            assertTrue(bytes.isNotEmpty(), "Resource bytes must not be empty for $filename")

            val parser = JmlParser()
            parser.parse(bytes.decodeToString())
            assertEquals(JmlParser.JML_LIST, parser.fileType, "File $filename must be JML_LIST")
            assertNotNull(parser.tree, "Parsed XML tree must not be null for $filename")
        }
    }

    @Test
    fun testAllBuiltinPatternLinesCanBeCreated() = runTest {
        val allEntries = BuiltinPatternLists.basic + BuiltinPatternLists.other

        for ((filename, _) in allEntries) {
            val bytes = Res.readBytes("files/$filename")
            val parser = JmlParser()
            parser.parse(bytes.decodeToString())
            val list = JmlPatternList(parser.tree)
            for (i in 0 until list.size) {
                val rec = list.getLine(i)
                if (rec?.notation != null) {
                    val pat = list.getPatternForLine(i)
                    assertNotNull(pat, "Pattern at line $i in $filename should not be null")
                }
            }
        }
    }
}
