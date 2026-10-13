package com.rhulhiking.attendance.attendance

import com.rhulhiking.attendance.settings.ColumnConfig
import com.rhulhiking.attendance.sheets.SheetsRepository
import com.rhulhiking.attendance.util.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** A hike tab in a spreadsheet. */
data class HikeTarget(val spreadsheetId: String, val tab: String)

sealed interface CheckInOutcome {
    val studentId: String

    /** Status written. [matchCount] > 1 means duplicate sign-ups were all marked. */
    data class CheckedIn(override val studentId: String, val displayName: String, val matchCount: Int) : CheckInOutcome
    data class AlreadyHere(override val studentId: String, val displayName: String, val matchCount: Int) : CheckInOutcome
    data class NotFound(override val studentId: String) : CheckInOutcome
    data class WalkInAdded(override val studentId: String) : CheckInOutcome
    data class InvalidId(override val studentId: String) : CheckInOutcome
}

data class CheckInBatch(val outcomes: List<CheckInOutcome>, val roster: Roster)

/**
 * Attendance rules on top of [SheetsRepository]. Every write is preceded by a fresh read of the
 * ID and status columns: row numbers are never reused across calls because the sign-up form can
 * append rows at any time. Throws SheetsException on failure.
 */
@Singleton
class CheckInService @Inject constructor(
    private val sheets: SheetsRepository,
    private val clock: Clock,
) {
    suspend fun listTabs(spreadsheetId: String): List<String> = sheets.listTabs(spreadsheetId)

    suspend fun fetchRoster(target: HikeTarget, columns: ColumnConfig): Roster =
        Roster.from(sheets.readColumns(target.spreadsheetId, target.tab, columns))

    suspend fun checkIn(target: HikeTarget, columns: ColumnConfig, rawId: String): CheckInBatch =
        checkInAll(target, columns, listOf(rawId))

    /** One read, one batched write, for any number of IDs (used by the offline queue flush). */
    suspend fun checkInAll(target: HikeTarget, columns: ColumnConfig, rawIds: List<String>): CheckInBatch {
        val roster = fetchRoster(target, columns)
        val rowsToWrite = linkedSetOf<Int>()
        val pending = rawIds.map { raw ->
            val id = StudentIds.normalize(raw)
            when (val m = RowMatcher.match(roster, id, columns.presentValue, rowsToWrite)) {
                MatchResult.InvalidId -> CheckInOutcome.InvalidId(raw)
                is MatchResult.NotFound -> CheckInOutcome.NotFound(m.studentId)
                is MatchResult.AlreadyHere -> CheckInOutcome.AlreadyHere(m.studentId, m.displayName, m.matchCount)
                is MatchResult.NeedsWrite -> {
                    rowsToWrite += m.rowsToWrite
                    CheckInOutcome.CheckedIn(m.studentId, m.displayName, m.matchCount)
                }
            }
        }
        if (rowsToWrite.isNotEmpty()) {
            sheets.writeStatus(target.spreadsheetId, target.tab, columns.statusColumn, rowsToWrite.toList(), columns.presentValue)
        }
        return CheckInBatch(pending, roster.withStatus(rowsToWrite, columns.presentValue))
    }

    /**
     * Appends a walk-in row (timestamp, ID, present value only). Re-checks first: if the student
     * signed up in the meantime, they're checked in normally instead of getting a second row.
     */
    suspend fun addWalkIn(target: HikeTarget, columns: ColumnConfig, rawId: String): CheckInBatch {
        val first = checkIn(target, columns, rawId)
        val outcome = first.outcomes.single()
        if (outcome !is CheckInOutcome.NotFound) return first

        sheets.appendWalkIn(target.spreadsheetId, target.tab, columns, timestamp(), outcome.studentId)
        val roster = runCatching { fetchRoster(target, columns) }.getOrElse { first.roster }
        return CheckInBatch(listOf(CheckInOutcome.WalkInAdded(outcome.studentId)), roster)
    }

    private fun timestamp(): String =
        TIMESTAMP.format(Instant.ofEpochMilli(clock.nowMillis()).atZone(ZoneId.systemDefault()))

    private companion object {
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
