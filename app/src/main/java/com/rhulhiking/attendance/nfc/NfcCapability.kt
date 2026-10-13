package com.rhulhiking.attendance.nfc

import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class NfcStatus {
    Available,
    NoNfcHardware,
    /** The NFC controller can't speak MIFARE Classic (common on Pixels and other non-NXP phones). */
    NoMifareSupport,
    Disabled,
}

@Singleton
class NfcCapabilityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun adapter(): NfcAdapter? = NfcAdapter.getDefaultAdapter(context)

    fun check(): NfcStatus {
        val pm = context.packageManager
        val adapter = adapter()
        return when {
            !pm.hasSystemFeature(PackageManager.FEATURE_NFC) || adapter == null -> NfcStatus.NoNfcHardware
            !pm.hasSystemFeature(MIFARE_FEATURE) -> NfcStatus.NoMifareSupport
            !adapter.isEnabled -> NfcStatus.Disabled
            else -> NfcStatus.Available
        }
    }

    companion object {
        const val MIFARE_FEATURE = "com.nxp.mifare"
    }
}
