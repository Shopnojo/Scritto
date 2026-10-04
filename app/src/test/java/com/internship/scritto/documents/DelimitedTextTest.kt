package com.internship.scritto.documents

import org.junit.Assert.assertEquals
import org.junit.Test

class DelimitedTextTest {

    @Test
    fun keepsBlankRowsSoRowPositionsMatchTheFile() {
        val rows = DelimitedText.parse("a,b\n\nc")

        assertEquals(listOf(listOf("a", "b"), emptyList<String>(), listOf("c")), rows)
        assertEquals("a,b\n\nc", DelimitedText.serialize(rows))
    }

    @Test
    fun readsQuotedCommasQuotesAndLineBreaks() {
        val rows = DelimitedText.parse("\"x, y\",\"say \"\"hi\"\"\"\n\"two\nlines\",3")

        assertEquals(
            listOf(listOf("x, y", "say \"hi\""), listOf("two\nlines", "3")),
            rows
        )
        assertEquals(
            "\"x, y\",\"say \"\"hi\"\"\"\n\"two\nlines\",3",
            DelimitedText.serialize(rows)
        )
    }

    @Test
    fun trailingNewlineDoesNotAddAnEmptyRow() {
        assertEquals(listOf(listOf("a")), DelimitedText.parse("a\n"))
        assertEquals(emptyList<List<String>>(), DelimitedText.parse(""))
    }
}
