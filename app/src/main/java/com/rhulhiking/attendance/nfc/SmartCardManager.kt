package com.rhulhiking.attendance.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartCardManager @Inject constructor(
    private val debouncer: TagDebouncer,
) {

    private val _results = MutableSharedFlow<NfcReadResult>(replay = 0)
    val results: SharedFlow<NfcReadResult> = _results

    private val KEYS: Array<ByteArray> = arrayOf(
        byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()
        ),
        hexToBytes("4D57414C5648"),
        hexToBytes("4D48414C5648"),
        hexToBytes("843E76ABD01F"),
        hexToBytes("45FA49A0C327"),
        hexToBytes("5C8FF9990DA2"),
        hexToBytes("D01AFEEB890A"),
        hexToBytes("75CCB59C9BED"),
        hexToBytes("4B791BEA7BCC")
    )

    suspend fun handleTag(tag: Tag) {
        val uid: ByteArray = tag.id ?: ByteArray(0)
        if (debouncer.shouldIgnore(uid)) return

        val result = withContext(Dispatchers.IO) {
            val mifare = runCatching { MifareClassic.get(tag) }.getOrNull()
            readCard(mifare?.let(::AndroidClassicCard))
        }
        if (result is NfcReadResult.Success) debouncer.recordSuccess(uid)
        _results.emit(result)
    }

    /** Blocking read; call off the main thread. */
    internal fun readCard(card: ClassicCard?): NfcReadResult {
        if (card == null) return NfcReadResult.Error(NOT_MIFARE)

        return try {
            card.connect()

            // authenticateSectorWithKeyA/B throw IOException (TagLostException) if the card
            // leaves the field mid-loop; that is caught below as "card moved".
            val authenticated = KEYS.any { key ->
                card.authenticateSectorWithKeyA(SECTOR, key) ||
                    card.authenticateSectorWithKeyB(SECTOR, key)
            }

            if (authenticated) {
                val raw = card.readBlock(BLOCK)
                val studentId = raw.takeWhile { it != 0.toByte() }
                    .toByteArray()
                    .toString(Charsets.US_ASCII)
                    .trim()
                if (studentId.isEmpty()) NfcReadResult.Error(NO_STUDENT_ID)
                else NfcReadResult.Success(studentId)
            } else {
                NfcReadResult.AuthFailed
            }
        } catch (e: IOException) {
            NfcReadResult.Error(CARD_MOVED)
        } catch (e: Exception) {
            NfcReadResult.Error(e.message ?: "Unknown NFC error")
        } finally {
            runCatching { card.close() }
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val SECTOR = 6
        private const val BLOCK = 24
        const val NOT_MIFARE = "Not a MIFARE Classic card. Is this an RHUL student card?"
        const val CARD_MOVED = "Card moved. Hold it still and tap again."
        const val NO_STUDENT_ID = "Card read, but no student number was on it."
    }
}
