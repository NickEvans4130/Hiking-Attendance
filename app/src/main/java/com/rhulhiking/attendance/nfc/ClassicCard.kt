package com.rhulhiking.attendance.nfc

import android.nfc.tech.MifareClassic

/** The subset of [MifareClassic] that [SmartCardManager] uses, so the read logic can be unit-tested. */
interface ClassicCard {
    fun connect()
    fun authenticateSectorWithKeyA(sector: Int, key: ByteArray): Boolean
    fun authenticateSectorWithKeyB(sector: Int, key: ByteArray): Boolean
    fun readBlock(block: Int): ByteArray
    fun close()
}

class AndroidClassicCard(private val mifare: MifareClassic) : ClassicCard {
    override fun connect() = mifare.connect()
    override fun authenticateSectorWithKeyA(sector: Int, key: ByteArray) =
        mifare.authenticateSectorWithKeyA(sector, key)
    override fun authenticateSectorWithKeyB(sector: Int, key: ByteArray) =
        mifare.authenticateSectorWithKeyB(sector, key)
    override fun readBlock(block: Int): ByteArray = mifare.readBlock(block)
    override fun close() = mifare.close()
}
