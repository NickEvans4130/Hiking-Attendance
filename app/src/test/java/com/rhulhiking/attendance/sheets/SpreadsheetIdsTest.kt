package com.rhulhiking.attendance.sheets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpreadsheetIdsTest {
    private val id = "1AbC-dEf_GhIjKlMnOpQrStUvWxYz0123456789abcd"

    @Test fun `edit url`() = assertEquals(id, SpreadsheetIds.parse("https://docs.google.com/spreadsheets/d/$id/edit#gid=0"))
    @Test fun `url with query and user index`() =
        assertEquals(id, SpreadsheetIds.parse("https://docs.google.com/spreadsheets/u/1/d/$id/edit?usp=sharing"))
    @Test fun `url without trailing path`() = assertEquals(id, SpreadsheetIds.parse("docs.google.com/spreadsheets/d/$id"))
    @Test fun `bare id with whitespace`() = assertEquals(id, SpreadsheetIds.parse("  $id \n"))
    @Test fun `garbage rejected`() {
        assertNull(SpreadsheetIds.parse(""))
        assertNull(SpreadsheetIds.parse("hello"))
        assertNull(SpreadsheetIds.parse("https://example.com/foo"))
        assertNull(SpreadsheetIds.parse("https://docs.google.com/document/d/$id/edit trailing words"))
    }
}
