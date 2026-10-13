package com.rhulhiking.attendance.nfc

import com.rhulhiking.attendance.util.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ignores the same card UID for [WINDOW_MS] after a successful read, so a card held against the
 * phone doesn't fire repeated check-ins. Failed reads are not recorded, so "tap again" works.
 */
@Singleton
class TagDebouncer @Inject constructor(private val clock: Clock) {
    private var lastUid: ByteArray? = null
    private var lastSuccessAt = 0L

    @Synchronized
    fun shouldIgnore(uid: ByteArray): Boolean {
        val last = lastUid ?: return false
        return last.contentEquals(uid) && clock.elapsedMillis() - lastSuccessAt < WINDOW_MS
    }

    @Synchronized
    fun recordSuccess(uid: ByteArray) {
        lastUid = uid.copyOf()
        lastSuccessAt = clock.elapsedMillis()
    }

    @Synchronized
    fun reset() {
        lastUid = null
    }

    companion object {
        const val WINDOW_MS = 3_000L
    }
}
