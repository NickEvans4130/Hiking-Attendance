package com.rhulhiking.attendance.sheets

object SpreadsheetIds {
    private val FROM_URL = Regex("""/spreadsheets/(?:u/\d+/)?d/([a-zA-Z0-9_-]+)""")
    private val BARE_ID = Regex("""^[a-zA-Z0-9_-]{20,}$""")

    /** Extracts the spreadsheet ID from a full Google Sheets URL or a pasted bare ID. */
    fun parse(input: String): String? {
        val text = input.trim()
        if (text.isEmpty()) return null
        FROM_URL.find(text)?.let { return it.groupValues[1] }
        return text.takeIf { BARE_ID.matches(it) }
    }
}
