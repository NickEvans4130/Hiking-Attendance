package com.rhulhiking.attendance.attendance

import com.rhulhiking.attendance.sheets.TabColumns

/** One sign-up row. [row] is the 1-based sheet row number. */
data class RosterEntry(
    val row: Int,
    val studentId: String,
    val name: String,
    val status: String,
)

/** A person on the list (duplicate rows for one ID are merged). */
data class Attendee(val studentId: String, val displayName: String, val here: Boolean)

data class RosterSummary(val hereCount: Int, val total: Int, val attendees: List<Attendee>)

class Roster(val entries: List<RosterEntry>, val hasHeader: Boolean) {

    fun summary(presentValue: String): RosterSummary {
        val attendees = entries.groupBy { it.studentId }.map { (id, rows) ->
            Attendee(
                studentId = id,
                displayName = displayName(rows, id),
                here = rows.any { isPresent(it.status, presentValue) },
            )
        }.sortedWith(compareBy<Attendee> { it.here }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })
        return RosterSummary(attendees.count { it.here }, attendees.size, attendees)
    }

    /** Copy with [rows] set to [status], used to reflect a successful write without re-fetching. */
    fun withStatus(rows: Collection<Int>, status: String): Roster =
        Roster(entries.map { if (it.row in rows) it.copy(status = status) else it }, hasHeader)

    companion object {
        /**
         * Builds a roster from the ID/name/status columns. Row 1 is treated as a header when its ID
         * cell isn't numeric. Rows without a usable ID are skipped.
         */
        fun from(columns: TabColumns): Roster {
            val hasHeader = detectHeader(columns.ids)
            val entries = columns.ids.indices.mapNotNull { i ->
                if (hasHeader && i == 0) return@mapNotNull null
                val id = StudentIds.normalize(columns.ids[i])
                if (id.isEmpty()) return@mapNotNull null
                RosterEntry(
                    row = columns.firstRow + i,
                    studentId = id,
                    name = columns.names.getOrNull(i)?.trim().orEmpty(),
                    status = columns.statuses.getOrNull(i)?.trim().orEmpty(),
                )
            }
            return Roster(entries, hasHeader)
        }

        fun detectHeader(ids: List<String?>): Boolean {
            val first = ids.firstOrNull() ?: return false
            return first.isNotBlank() && !StudentIds.isNumeric(first)
        }

        fun isPresent(status: String, presentValue: String): Boolean =
            status.trim().equals(presentValue.trim(), ignoreCase = true)

        fun displayName(rows: List<RosterEntry>, id: String): String =
            rows.firstOrNull { it.name.isNotBlank() }?.name ?: id
    }
}
