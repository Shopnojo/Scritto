package com.internship.scritto.documents

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfEditOpsTest {

    private fun ops(vararg items: JSONObject) = JSONArray(items.toList())

    @Test
    fun parsesEachOperationKind() {
        val parsed = PdfEditOps.parse(
            ops(
                JSONObject().put("op", "replace_text").put("page", 2).put("find", "Draft").put("replace", "Final"),
                JSONObject().put("op", "add_text").put("page", 1).put("text", "Approved").put("x_percent", 80),
                JSONObject().put("op", "highlight").put("page", 3).put("find", "risk")
            )
        )

        assertEquals(
            listOf(
                PdfEditOp.ReplaceText(2, "Draft", "Final"),
                PdfEditOp.AddText(1, "Approved", xPercent = 80, yPercent = 10, size = 12),
                PdfEditOp.Highlight(3, "risk")
            ),
            parsed
        )
    }

    @Test
    fun replaceMayDeleteTextByLeavingReplacementEmpty() {
        val parsed = PdfEditOps.parse(
            ops(JSONObject().put("op", "replace_text").put("page", 1).put("find", "old").put("replace", ""))
        )

        assertEquals(listOf(PdfEditOp.ReplaceText(1, "old", "")), parsed)
    }

    @Test
    fun clampsPositionsAndSizes() {
        val parsed = PdfEditOps.parse(
            ops(
                JSONObject().put("op", "add_text").put("page", 1).put("text", "x")
                    .put("x_percent", 250).put("y_percent", -5).put("size", 200)
            )
        )

        assertEquals(PdfEditOp.AddText(1, "x", xPercent = 100, yPercent = 0, size = 40), parsed.single())
    }

    @Test
    fun rejectsEmptyOrOversizedRequests() {
        assertThrows(IllegalArgumentException::class.java) { PdfEditOps.parse(JSONArray()) }

        val tooMany = JSONArray()
        repeat(PdfEditOps.MAX_OPERATIONS + 1) {
            tooMany.put(JSONObject().put("op", "highlight").put("page", 1).put("find", "a"))
        }
        assertThrows(IllegalArgumentException::class.java) { PdfEditOps.parse(tooMany) }
    }

    @Test
    fun rejectsUnknownOperationsAndMissingText() {
        assertThrows(IllegalArgumentException::class.java) {
            PdfEditOps.parse(ops(JSONObject().put("op", "delete_page").put("page", 1)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PdfEditOps.parse(ops(JSONObject().put("op", "highlight").put("page", 1).put("find", "  ")))
        }
    }
}
