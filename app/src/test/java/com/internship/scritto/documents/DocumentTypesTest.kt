package com.internship.scritto.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTypesTest {
    @Test
    fun detectsPreviewAndEditableKinds() {
        assertEquals(DocumentKind.CODE, DocumentTypes.describe("main.kt", "content://x").kind)
        assertEquals(DocumentKind.PDF, DocumentTypes.describe("report.pdf", "content://x").kind)
        assertTrue(DocumentTypes.describe("report.pdf", "content://x").capabilities.canEdit)
        assertTrue(!DocumentTypes.describe("main.kt", "content://x").capabilities.canEdit)
    }

    @Test
    fun keepsPreviewOnlyFilesReadOnly() {
        assertTrue(!DocumentTypes.describe("notes.md", "content://x").capabilities.canEdit)
        assertTrue(!DocumentTypes.describe("script.py", "content://x").capabilities.canConvert)
    }

    @Test
    fun alignsBraceBasedCodeWithoutChangingLineCount() {
        val source = "fun main() {\nprintln(1)\n}"
        val aligned = CodeFormatter.alignForDisplay(source, "Kotlin")
        assertEquals(source.lines().size, aligned.lines().size)
        assertTrue(aligned.lines()[1].startsWith("    "))
    }

    @Test
    fun alignsPythonBlocksWithoutMovingReturnOrBreakStatements() {
        val source = "def greet():\nprint('hi')\nreturn True"
        val aligned = CodeFormatter.alignForDisplay(source, "Python")
        assertEquals("    print('hi')", aligned.lines()[1])
        assertEquals("    return True", aligned.lines()[2])
    }

    @Test
    fun alignsRubyBlocksWithEndAndElse() {
        val source = "def greet\nputs 'hi'\nelse\nputs 'fallback'\nend"
        val aligned = CodeFormatter.alignForDisplay(source, "Ruby")
        assertEquals("    puts 'hi'", aligned.lines()[1])
        assertEquals("else", aligned.lines()[2])
        assertEquals("    puts 'fallback'", aligned.lines()[3])
        assertEquals("end", aligned.lines()[4])
    }
}
