package com.rhulhiking.attendance.queue

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** The offline check-in queue. An interface so the flush logic can be tested without Room. */
interface PendingQueue {
    /** Adds [studentId] unless it's already queued for this tab. Returns true if added. */
    suspend fun enqueue(spreadsheetId: String, tab: String, studentId: String, createdAt: Long): Boolean
    suspend fun pending(): List<PendingCheckIn>
    suspend fun remove(ids: List<Long>)
    suspend fun markNeedsAttention(ids: List<Long>, note: String)
    fun observePendingCount(spreadsheetId: String, tab: String): Flow<Int>
    fun observeNeedsAttention(spreadsheetId: String, tab: String): Flow<List<PendingCheckIn>>
}

class RoomPendingQueue @Inject constructor(private val dao: PendingCheckInDao) : PendingQueue {
    override suspend fun enqueue(spreadsheetId: String, tab: String, studentId: String, createdAt: Long): Boolean {
        if (dao.exists(spreadsheetId, tab, studentId)) return false
        dao.insert(PendingCheckIn(spreadsheetId = spreadsheetId, tab = tab, studentId = studentId, createdAt = createdAt))
        return true
    }

    override suspend fun pending() = dao.pending()
    override suspend fun remove(ids: List<Long>) { if (ids.isNotEmpty()) dao.delete(ids) }
    override suspend fun markNeedsAttention(ids: List<Long>, note: String) {
        if (ids.isNotEmpty()) dao.markNeedsAttention(ids, note)
    }
    override fun observePendingCount(spreadsheetId: String, tab: String) = dao.observePendingCount(spreadsheetId, tab)
    override fun observeNeedsAttention(spreadsheetId: String, tab: String) = dao.observeNeedsAttention(spreadsheetId, tab)
}
