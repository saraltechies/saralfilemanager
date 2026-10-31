package com.saraltechies.saralfilemanager.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FileNamesTest {

    @Test fun acceptsOrdinaryNames() {
        assertNull(FileNames.problem("Report 2026.pdf"))
        assertNull(FileNames.problem(".nomedia"))
        assertNull(FileNames.problem("फ़ोटो.jpg"))
    }

    @Test fun rejectsUnusableNames() {
        assertEquals(FileNames.Problem.EMPTY, FileNames.problem("  "))
        assertEquals(FileNames.Problem.RESERVED, FileNames.problem(".."))
        assertEquals(FileNames.Problem.INVALID_CHARACTER, FileNames.problem("a/b"))
        assertEquals(FileNames.Problem.TOO_LONG, FileNames.problem("x".repeat(256)))
    }

    @Test fun uniqueKeepsAFreeName() {
        assertEquals("a.txt", FileNames.unique("a.txt", isDirectory = false) { false })
    }

    @Test fun uniqueNumbersBeforeTheExtension() {
        val taken = setOf("a.txt", "a (1).txt")
        assertEquals("a (2).txt", FileNames.unique("a.txt", isDirectory = false) { it in taken })
    }

    @Test fun foldersAndDotFilesKeepTheirWholeName() {
        assertEquals("v1.2 (1)", FileNames.unique("v1.2", isDirectory = true) { it == "v1.2" })
        assertEquals(".config (1)", FileNames.unique(".config", isDirectory = false) { it == ".config" })
    }
}
