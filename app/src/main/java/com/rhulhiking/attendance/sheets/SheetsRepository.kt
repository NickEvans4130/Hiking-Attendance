package com.rhulhiking.attendance.sheets

import com.rhulhiking.attendance.settings.ColumnConfig

/**
 * Raw contents of the ID, name and status columns of a hike tab, index-aligned.
 * Index i is sheet row [firstRow] + i. Lists may differ in length (trailing blanks are omitted).
 */
data class TabColumns(
    val ids: List<String?>,
    val names: List<String?>,
    val statuses: List<String?>,
    val firstRow: Int = 1,
)

/**
 * Access to the attendance spreadsheet. Implementations must only read the ID, name and status
 * columns and only write the status column (plus whole walk-in rows via [appendWalkIn]).
 * All methods throw [SheetsException].
 */
interface SheetsRepository {
    suspend fun listTabs(spreadsheetId: String): List<String>

    suspend fun readColumns(spreadsheetId: String, tab: String, columns: ColumnConfig): TabColumns

    /** Sets the status cell of each 1-based [rows] to [value]. */
    suspend fun writeStatus(spreadsheetId: String, tab: String, statusColumn: String, rows: List<Int>, value: String)

    /** Appends a row containing only the timestamp, student ID and present value. */
    suspend fun appendWalkIn(spreadsheetId: String, tab: String, columns: ColumnConfig, timestamp: String, studentId: String)
}
