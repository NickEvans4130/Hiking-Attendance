package com.rhulhiking.attendance.fakes

import com.rhulhiking.attendance.queue.PendingCheckIn
import com.rhulhiking.attendance.queue.PendingQueue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryPendingQueue : PendingQueue {
    private val entries = MutableStateFlow<List<PendingCheckIn>>(emptyList())
    private var nextId = 1L
    val all: List<PendingCheckIn> get() = entries.value

    override suspend fun enqueue(spreadsheetId: String, tab: String, studentId: String, createdAt: Long): Boolean {
        if (entries.value.any { it.spreadsheetId == spreadsheetId && it.tab == tab && it.studentId == studentId }) return false
        entries.value += PendingCheckIn(nextId++, spreadsheetId, tab, studentId, createdAt)
        return true
    }

    /** Adds without the de-duplication check, to test the flusher's own handling. */
    fun addRaw(spreadsheetId: String, tab: String, studentId: String) {
        entries.value += PendingCheckIn(nextId++, spreadsheetId, tab, studentId, nextId)
    }

    override suspend fun pending() = entries.value.filter { it.state == PendingCheckIn.State.PENDING }.sortedBy { it.createdAt }
    override suspend fun remove(ids: List<Long>) { entries.value = entries.value.filterNot { it.id in ids } }
    override suspend fun markNeedsAttention(ids: List<Long>, note: String) {
        entries.value = entries.value.map { if (it.id in ids) it.copy(state = PendingCheckIn.State.NEEDS_ATTENTION, note = note) else it }
    }
    override fun observePendingCount(spreadsheetId: String, tab: String) =
        entries.map { l -> l.count { it.spreadsheetId == spreadsheetId && it.tab == tab && it.state == PendingCheckIn.State.PENDING } }
    override fun observeNeedsAttention(spreadsheetId: String, tab: String) =
        entries.map { l -> l.filter { it.spreadsheetId == spreadsheetId && it.tab == tab && it.state == PendingCheckIn.State.NEEDS_ATTENTION } }
}
