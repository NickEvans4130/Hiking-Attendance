package com.rhulhiking.attendance.fakes

import com.rhulhiking.attendance.settings.ColumnConfig
import com.rhulhiking.attendance.sheets.A1
import com.rhulhiking.attendance.sheets.SheetsException
import com.rhulhiking.attendance.sheets.SheetsRepository
import com.rhulhiking.attendance.sheets.TabColumns

/** In-memory spreadsheet. Each tab is a list of rows; each row is a list of cells (index 0 = column A). */
class FakeSheetsRepository : SheetsRepository {
    val tabs = linkedMapOf<String, MutableList<MutableList<String?>>>()
    var reads = 0
    val statusWrites = mutableListOf<Triple<String, List<Int>, String>>()
    val appends = mutableListOf<Pair<String, List<String?>>>()

    /** Thrown by every call while set. */
    var failWith: SheetsException? = null

    /** Runs after each read and before the caller writes, e.g. to simulate the form appending a row. */
    var afterRead: (() -> Unit)? = null

    fun addRow(tab: String, vararg cells: Pair<String, String?>) {
        val row = MutableList<String?>(13) { null }
        cells.forEach { (col, v) -> row[A1.columnIndex(col)] = v }
        tabs.getOrPut(tab) { mutableListOf() }.add(row)
    }

    /** A form-style row: timestamp in A, ID in B, name in C, status in M. Fills D-L with junk. */
    fun signUp(tab: String, id: String?, name: String?, status: String? = "Signed Up") {
        val row = MutableList<String?>(13) { "private-data" }
        row[0] = "45000.5"; row[1] = id; row[2] = name; row[12] = status
        tabs.getOrPut(tab) { mutableListOf() }.add(row)
    }

    fun cell(tab: String, row: Int, col: String): String? = tabs.getValue(tab)[row - 1].getOrNull(A1.columnIndex(col))

    private fun check() { failWith?.let { throw it } }

    override suspend fun listTabs(spreadsheetId: String): List<String> { check(); return tabs.keys.toList() }

    override suspend fun readColumns(spreadsheetId: String, tab: String, columns: ColumnConfig): TabColumns {
        check()
        reads++
        val rows = tabs[tab] ?: throw SheetsException.NotFound()
        fun col(c: String) = rows.map { it.getOrNull(A1.columnIndex(c)) }
        return TabColumns(col(columns.idColumn), col(columns.nameColumn), col(columns.statusColumn))
            .also { afterRead?.invoke() }
    }

    override suspend fun writeStatus(spreadsheetId: String, tab: String, statusColumn: String, rows: List<Int>, value: String) {
        check()
        statusWrites += Triple(tab, rows, value)
        val data = tabs[tab] ?: throw SheetsException.NotFound()
        val idx = A1.columnIndex(statusColumn)
        rows.forEach { r ->
            val row = data[r - 1]
            while (row.size <= idx) row.add(null)
            row[idx] = value
        }
    }

    override suspend fun appendWalkIn(spreadsheetId: String, tab: String, columns: ColumnConfig, timestamp: String, studentId: String) {
        check()
        val row = MutableList<String?>(13) { null }
        row[A1.columnIndex(columns.timestampColumn)] = timestamp
        row[A1.columnIndex(columns.idColumn)] = studentId
        row[A1.columnIndex(columns.statusColumn)] = columns.presentValue
        appends += tab to row.toList()
        (tabs[tab] ?: throw SheetsException.NotFound()).add(row)
    }
}
