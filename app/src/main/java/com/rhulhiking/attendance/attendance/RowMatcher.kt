package com.rhulhiking.attendance.attendance

sealed interface MatchResult {
    data object InvalidId : MatchResult
    data class NotFound(val studentId: String) : MatchResult
    data class AlreadyHere(val studentId: String, val displayName: String, val matchCount: Int) : MatchResult
    /** [rowsToWrite] are the matching rows whose status isn't yet the present value. */
    data class NeedsWrite(
        val studentId: String,
        val displayName: String,
        val rowsToWrite: List<Int>,
        val matchCount: Int,
    ) : MatchResult
}

object RowMatcher {
    /**
     * Matches a normalised card ID against the roster. Every matching row is considered, so
     * duplicate sign-ups all get marked present. [alreadyWritten] holds rows that are being
     * written in the same batch; they count as present.
     */
    fun match(
        roster: Roster,
        studentId: String,
        presentValue: String,
        alreadyWritten: Set<Int> = emptySet(),
    ): MatchResult {
        if (studentId.isEmpty()) return MatchResult.InvalidId
        val rows = roster.entries.filter { it.studentId == studentId }
        if (rows.isEmpty()) return MatchResult.NotFound(studentId)

        val name = Roster.displayName(rows, studentId)
        val toWrite = rows
            .filterNot { Roster.isPresent(it.status, presentValue) || it.row in alreadyWritten }
            .map { it.row }
        return if (toWrite.isEmpty()) MatchResult.AlreadyHere(studentId, name, rows.size)
        else MatchResult.NeedsWrite(studentId, name, toWrite, rows.size)
    }
}
