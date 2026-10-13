package com.rhulhiking.attendance.queue

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A check-in that couldn't reach the sheet. Holds the minimum needed to replay it and is deleted
 * as soon as it syncs. Nothing else about students is stored on the device.
 */
@Entity(tableName = "pending_check_ins", indices = [Index("spreadsheetId", "tab")])
data class PendingCheckIn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spreadsheetId: String,
    val tab: String,
    /** Normalised student ID. */
    val studentId: String,
    val createdAt: Long,
    val state: State = State.PENDING,
    /** Why the entry needs a human, when [state] is NEEDS_ATTENTION. */
    val note: String? = null,
) {
    enum class State { PENDING, NEEDS_ATTENTION }
}
