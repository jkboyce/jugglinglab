//
// BuiltinPatternListsTest.kt
//
// Tests for built-in pattern list packaging.
//
// Copyright 2026 Jack Boyce and the Juggling Lab contributors
//

package org.jugglinglab.ui.common

import org.jugglinglab.composeapp.generated.resources.Res
import org.jugglinglab.jml.JmlParser
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuiltinPatternListsTest {
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
}
