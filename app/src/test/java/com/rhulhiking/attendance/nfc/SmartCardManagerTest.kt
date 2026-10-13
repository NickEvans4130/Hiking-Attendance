package com.rhulhiking.attendance.nfc

import android.nfc.TagLostException
import com.rhulhiking.attendance.fakes.FakeClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SmartCardManagerTest {

    private val manager = SmartCardManager(TagDebouncer(FakeClock()))

    private class FakeCard(
        private val acceptKeyAtAttempt: Int? = 0,
        private val throwOnAuthAttempt: Int? = null,
        private val throwable: Exception = IOException("Tag was lost."),
        private val block: ByteArray = "101096712".toByteArray() + ByteArray(7),
    ) : ClassicCard {
        var attempts = 0
        var closed = false
        override fun connect() = Unit
        override fun authenticateSectorWithKeyA(sector: Int, key: ByteArray) = attempt()
        override fun authenticateSectorWithKeyB(sector: Int, key: ByteArray) = attempt()
        private fun attempt(): Boolean {
            val n = attempts++
            if (n == throwOnAuthAttempt) throw throwable
            return n == acceptKeyAtAttempt
        }
        override fun readBlock(block: Int) = this.block
        override fun close() { closed = true }
    }

    @Test
    fun `reads null-terminated ASCII student id`() {
        val card = FakeCard(block = " 101096712".toByteArray() + byteArrayOf(0, 0x41, 0x41))
        assertEquals(NfcReadResult.Success("101096712"), manager.readCard(card))
        assertTrue(card.closed)
    }

    @Test
    fun `tries key B and later keys before giving up`() {
        val card = FakeCard(acceptKeyAtAttempt = 5)
        assertEquals(NfcReadResult.Success("101096712"), manager.readCard(card))
        assertEquals(6, card.attempts)
    }

    @Test
    fun `no key authenticates gives AuthFailed`() {
        val card = FakeCard(acceptKeyAtAttempt = null)
        assertEquals(NfcReadResult.AuthFailed, manager.readCard(card))
        assertEquals(18, card.attempts)
    }

    @Test
    fun `IOException during auth loop becomes card moved error`() {
        val card = FakeCard(acceptKeyAtAttempt = null, throwOnAuthAttempt = 3)
        assertEquals(NfcReadResult.Error(SmartCardManager.CARD_MOVED), manager.readCard(card))
        assertTrue(card.closed)
    }

    @Test
    fun `TagLostException during auth loop becomes card moved error`() {
        val card = FakeCard(acceptKeyAtAttempt = null, throwOnAuthAttempt = 0, throwable = TagLostException())
        assertEquals(NfcReadResult.Error(SmartCardManager.CARD_MOVED), manager.readCard(card))
    }

    @Test
    fun `null card is not mifare`() {
        assertEquals(NfcReadResult.Error(SmartCardManager.NOT_MIFARE), manager.readCard(null))
    }

    @Test
    fun `empty block is an error not a blank success`() {
        val card = FakeCard(block = ByteArray(16))
        assertEquals(NfcReadResult.Error(SmartCardManager.NO_STUDENT_ID), manager.readCard(card))
    }
}
