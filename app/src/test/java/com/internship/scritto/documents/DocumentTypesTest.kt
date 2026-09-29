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
    fun alignsBraceBasedCodeWithoutChangingLineCount() {
        val source = "fun main() {\nprintln(1)\n}"
        val aligned = CodeFormatter.alignForDisplay(source, "Kotlin")
        assertEquals(source.lines().size, aligned.lines().size)
        assertTrue(aligned.lines()[1].startsWith("    "))
    }
}
