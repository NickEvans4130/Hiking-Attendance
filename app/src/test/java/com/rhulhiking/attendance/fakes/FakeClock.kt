package com.rhulhiking.attendance.fakes

import com.rhulhiking.attendance.util.Clock

class FakeClock(var elapsed: Long = 0L, var now: Long = 1_760_000_000_000L) : Clock {
    override fun elapsedMillis() = elapsed
    override fun nowMillis() = now
    fun advance(ms: Long) {
        elapsed += ms
        now += ms
    }
}
