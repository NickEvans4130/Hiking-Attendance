package com.rhulhiking.attendance.settings

import com.rhulhiking.attendance.sheets.A1

data class ColumnConfig(
    val idColumn: String = "B",
    val nameColumn: String = "C",
    val statusColumn: String = "M",
    val presentValue: String = "HERE",
    /** Walk-in rows put their timestamp here. The sign-up form always uses column A. */
    val timestampColumn: String = "A",
) {
    /** Null when valid, otherwise a message explaining what's wrong. */
    fun validate(): String? {
        val cols = listOf("ID" to idColumn, "Name" to nameColumn, "Status" to statusColumn)
        cols.firstOrNull { !A1.isValidColumn(it.second) }?.let { return "${it.first} column must be letters like B or AA" }
        val status = statusColumn.uppercase()
        // The status column is the only one we write; it must never overlap the ID or name.
        if (status == idColumn.uppercase() || status == nameColumn.uppercase() || status == timestampColumn.uppercase()) {
            return "Status column must be different from the ID, name and timestamp columns"
        }
        if (idColumn.equals(nameColumn, ignoreCase = true)) return "ID and name columns must be different"
        if (presentValue.isBlank()) return "Present value can't be blank"
        return null
    }

    fun normalized() = copy(
        idColumn = idColumn.trim().uppercase(),
        nameColumn = nameColumn.trim().uppercase(),
        statusColumn = statusColumn.trim().uppercase(),
        presentValue = presentValue.trim(),
    )
}

data class AppSettings(
    val spreadsheetId: String? = null,
    val accountEmail: String? = null,
    val columns: ColumnConfig = ColumnConfig(),
    val walkInsEnabled: Boolean = true,
    val hiddenTabs: Set<String> = DEFAULT_HIDDEN_TABS,
    val rememberHiddenTabs: Boolean = true,
) {
    companion object {
        /** Known non-hike tabs in the club spreadsheet. */
        val DEFAULT_HIDDEN_TABS = setOf(
            "Hike Calendar",
            "Event Calendar",
            "Members",
            "Club Growth",
            "Feedback responces",
        )
    }
}
