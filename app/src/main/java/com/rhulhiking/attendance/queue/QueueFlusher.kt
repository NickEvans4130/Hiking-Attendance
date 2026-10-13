package com.rhulhiking.attendance.queue

import com.rhulhiking.attendance.attendance.CheckInOutcome
import com.rhulhiking.attendance.attendance.CheckInService
import com.rhulhiking.attendance.attendance.HikeTarget
import com.rhulhiking.attendance.settings.ColumnConfig
import com.rhulhiking.attendance.sheets.SheetsException
import javax.inject.Inject

data class FlushResult(val synced: Int, val needsAttention: Int, val status: Status) {
    enum class Status {
        /** Everything that could be synced was. */
        Done,
        /** Network or Sheets temporarily unavailable; try again later. */
        Retry,
        /** The user must open the app and re-grant Google access. */
        NeedsAuth,
    }
}

/**
 * Replays queued check-ins with the same matching rules as a live tap: one fresh read and one
 * batched write per hike tab. Entries that are synced (or were already HERE) are deleted.
 * Entries that can't be resolved automatically, such as someone not on the tab, are kept as
 * "needs attention" for a committee member to decide on.
 */
class QueueFlusher @Inject constructor(
    private val queue: PendingQueue,
    private val service: CheckInService,
) {
    suspend fun flush(columns: ColumnConfig): FlushResult {
        var synced = 0
        var attention = 0
        val groups = queue.pending().groupBy { HikeTarget(it.spreadsheetId, it.tab) }

        for ((target, entries) in groups) {
            try {
                val batch = service.checkInAll(target, columns, entries.map { it.studentId })
                val done = mutableListOf<Long>()
                val notFound = mutableListOf<Long>()
                val invalid = mutableListOf<Long>()
                entries.zip(batch.outcomes).forEach { (entry, outcome) ->
                    when (outcome) {
                        is CheckInOutcome.CheckedIn, is CheckInOutcome.AlreadyHere, is CheckInOutcome.WalkInAdded -> done += entry.id
                        is CheckInOutcome.NotFound -> notFound += entry.id
                        is CheckInOutcome.InvalidId -> invalid += entry.id
                    }
                }
                queue.remove(done)
                queue.markNeedsAttention(notFound, NOT_SIGNED_UP)
                queue.markNeedsAttention(invalid, "Unreadable student number")
                synced += done.size
                attention += notFound.size + invalid.size
            } catch (e: SheetsException.AuthRequired) {
                return FlushResult(synced, attention, FlushResult.Status.NeedsAuth)
            } catch (e: SheetsException) {
                if (e.isTransient) return FlushResult(synced, attention, FlushResult.Status.Retry)
                // Permanent for this tab (deleted tab, lost access...): park it, carry on with others.
                queue.markNeedsAttention(entries.map { it.id }, e.message ?: "Couldn't sync")
                attention += entries.size
            }
        }
        return FlushResult(synced, attention, FlushResult.Status.Done)
    }

    companion object {
        const val NOT_SIGNED_UP = "Not signed up for this hike"
    }
}
