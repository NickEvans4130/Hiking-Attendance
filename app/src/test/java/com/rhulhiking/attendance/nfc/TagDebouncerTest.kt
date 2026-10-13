package com.rhulhiking.attendance.nfc

import com.rhulhiking.attendance.fakes.FakeClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagDebouncerTest {
    private val clock = FakeClock()
    private val debouncer = TagDebouncer(clock)
    private val uidA = byteArrayOf(1, 2, 3, 4)
    private val uidB = byteArrayOf(9, 9, 9, 9)

    @Test
    fun `same uid ignored within window then allowed`() {
        assertFalse(debouncer.shouldIgnore(uidA))
        debouncer.recordSuccess(uidA)
        clock.advance(2_999)
        assertTrue(debouncer.shouldIgnore(uidA.copyOf()))
        clock.advance(1)
        assertFalse(debouncer.shouldIgnore(uidA))
    }

    @Test
    fun `different uid is never debounced`() {
        debouncer.recordSuccess(uidA)
        assertFalse(debouncer.shouldIgnore(uidB))
    }

    @Test
    fun `failed reads are not recorded`() {
        // Nothing recorded, so an immediate retap is processed.
        assertFalse(debouncer.shouldIgnore(uidA))
        assertFalse(debouncer.shouldIgnore(uidA))
    }
}
