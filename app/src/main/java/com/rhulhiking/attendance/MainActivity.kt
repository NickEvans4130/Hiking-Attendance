package com.rhulhiking.attendance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.rhulhiking.attendance.nfc.NfcCapabilityChecker
import com.rhulhiking.attendance.nfc.NfcReaderController
import com.rhulhiking.attendance.nfc.NfcStatus
import com.rhulhiking.attendance.nfc.SmartCardManager
import com.rhulhiking.attendance.ui.AppNavHost
import com.rhulhiking.attendance.ui.MainViewModel
import com.rhulhiking.attendance.ui.nfc.NfcUnavailableScreen
import com.rhulhiking.attendance.ui.theme.HikingTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var smartCardManager: SmartCardManager
    @Inject lateinit var capabilityChecker: NfcCapabilityChecker

    private val mainViewModel: MainViewModel by viewModels()
    private lateinit var reader: NfcReaderController
    private val nfcStatus = MutableStateFlow(NfcStatus.Available)
    /** The user chose "try anyway" on a phone that doesn't advertise MIFARE Classic support. */
    private val mifareOverride = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        reader = NfcReaderController(this, capabilityChecker.adapter(), lifecycleScope, smartCardManager)
        nfcStatus.value = capabilityChecker.check()

        val effectiveStatus = combine(nfcStatus, mifareOverride) { status, override ->
            if (status == NfcStatus.NoMifareSupport && override) NfcStatus.Available else status
        }.stateIn(lifecycleScope, SharingStarted.Eagerly, nfcStatus.value)

        setContent {
            HikingTheme {
                Surface(Modifier.fillMaxSize()) {
                    val status by effectiveStatus.collectAsStateWithLifecycle()
                    val start by mainViewModel.startDestination.collectAsStateWithLifecycle()
                    // The nav host stays composed underneath so an NFC toggle mid-hike keeps the session.
                    start?.let { AppNavHost(startDestination = it) }
                    if (status != NfcStatus.Available) Surface(Modifier.fillMaxSize()) {
                        NfcUnavailableScreen(
                            status = status,
                            onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                            onTryAnyway = {
                                mifareOverride.value = true
                                updateReader()
                            },
                            onRecheck = ::refreshNfcStatus,
                        )
                    }
                }
            }
        }
    }

    /** NFC switched on or off from quick settings while the app is in front. */
    private val adapterStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshNfcStatus()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            adapterStateReceiver,
            IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(adapterStateReceiver)
    }

    override fun onResume() {
        super.onResume()
        refreshNfcStatus()
    }

    override fun onPause() {
        super.onPause()
        reader.disable()
    }

    private fun refreshNfcStatus() {
        nfcStatus.value = capabilityChecker.check()
        updateReader()
    }

    private fun updateReader() {
        val status = nfcStatus.value
        val usable = status == NfcStatus.Available ||
            (status == NfcStatus.NoMifareSupport && mifareOverride.value)
        if (usable) reader.enable() else reader.disable()
    }
}
