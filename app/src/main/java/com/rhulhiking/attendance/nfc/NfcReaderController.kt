package com.rhulhiking.attendance.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Wraps [NfcAdapter.enableReaderMode] for a foreground activity. */
class NfcReaderController(
    private val activity: Activity,
    private val adapter: NfcAdapter?,
    private val scope: CoroutineScope,
    private val smartCardManager: SmartCardManager,
) {
    private var enabled = false

    fun enable() {
        val nfc = adapter ?: return
        if (enabled || !nfc.isEnabled) return
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        nfc.enableReaderMode(
            activity,
            { tag -> scope.launch { smartCardManager.handleTag(tag) } },
            FLAGS,
            extras,
        )
        enabled = true
    }

    fun disable() {
        if (!enabled) return
        runCatching { adapter?.disableReaderMode(activity) }
        enabled = false
    }

    private companion object {
        const val FLAGS = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
    }
}
