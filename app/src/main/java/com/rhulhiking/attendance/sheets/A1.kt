package com.rhulhiking.attendance.sheets

/** Builds A1-notation ranges. Tab names are always quoted, with internal quotes doubled. */
object A1 {
    private val COLUMN = Regex("^[A-Z]{1,3}$")

    fun quoteTab(tab: String): String = "'" + tab.replace("'", "''") + "'"

    /** Whole column, e.g. `'It''s a Hike'!B:B`. */
    fun column(tab: String, column: String): String {
        val col = requireColumn(column)
        return "${quoteTab(tab)}!$col:$col"
    }

    /** Single cell, e.g. `'Hike'!M12`. [row] is 1-based. */
    fun cell(tab: String, column: String, row: Int): String {
        require(row >= 1) { "Row must be >= 1, was $row" }
        return "${quoteTab(tab)}!${requireColumn(column)}$row"
    }

    /** Columns between two letters inclusive, e.g. `'Hike'!A:M`. */
    fun columns(tab: String, from: String, to: String): String =
        "${quoteTab(tab)}!${requireColumn(from)}:${requireColumn(to)}"

    fun isValidColumn(column: String): Boolean = COLUMN.matches(column.trim().uppercase())

    /** 0-based index: A -> 0, Z -> 25, AA -> 26. */
    fun columnIndex(column: String): Int =
        requireColumn(column).fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1

    fun columnLetters(index: Int): String {
        require(index >= 0)
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** First row number in a returned range such as `'Hike'!B1:B57`, or null for whole columns. */
    fun startRow(range: String): Int? {
        val cellPart = range.substringAfterLast('!')
        return Regex("^\\$?[A-Za-z]+\\$?(\\d+)").find(cellPart)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun requireColumn(column: String): String {
        val c = column.trim().uppercase()
        require(COLUMN.matches(c)) { "Invalid column '$column'" }
        return c
    }
}
