package com.rhulhiking.attendance.queue

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingCheckInDao {
    @Insert
    suspend fun insert(entry: PendingCheckIn): Long

    @Query("SELECT * FROM pending_check_ins WHERE state = 'PENDING' ORDER BY createdAt, id")
    suspend fun pending(): List<PendingCheckIn>

    @Query("SELECT EXISTS(SELECT 1 FROM pending_check_ins WHERE spreadsheetId = :spreadsheetId AND tab = :tab AND studentId = :studentId)")
    suspend fun exists(spreadsheetId: String, tab: String, studentId: String): Boolean

    @Query("SELECT COUNT(*) FROM pending_check_ins WHERE spreadsheetId = :spreadsheetId AND tab = :tab AND state = 'PENDING'")
    fun observePendingCount(spreadsheetId: String, tab: String): Flow<Int>

    @Query("SELECT * FROM pending_check_ins WHERE spreadsheetId = :spreadsheetId AND tab = :tab AND state = 'NEEDS_ATTENTION' ORDER BY createdAt")
    fun observeNeedsAttention(spreadsheetId: String, tab: String): Flow<List<PendingCheckIn>>

    @Query("DELETE FROM pending_check_ins WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("UPDATE pending_check_ins SET state = 'NEEDS_ATTENTION', note = :note WHERE id IN (:ids)")
    suspend fun markNeedsAttention(ids: List<Long>, note: String)
}

@Database(entities = [PendingCheckIn::class], version = 1, exportSchema = false)
abstract class QueueDatabase : RoomDatabase() {
    abstract fun pendingCheckIns(): PendingCheckInDao
}
