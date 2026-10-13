package com.rhulhiking.attendance.util

import android.os.SystemClock

/** Monotonic + wall clock, injectable so time-based logic is testable. */
interface Clock {
    /** Monotonic milliseconds, for measuring intervals. */
    fun elapsedMillis(): Long

    /** Wall-clock epoch milliseconds, for timestamps. */
    fun nowMillis(): Long
}

object SystemClockImpl : Clock {
    override fun elapsedMillis(): Long = SystemClock.elapsedRealtime()
    override fun nowMillis(): Long = System.currentTimeMillis()
}
