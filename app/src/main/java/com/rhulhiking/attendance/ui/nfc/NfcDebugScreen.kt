package com.rhulhiking.attendance.ui.nfc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rhulhiking.attendance.nfc.NfcReadResult
import com.rhulhiking.attendance.nfc.SmartCardManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class NfcDebugViewModel @Inject constructor(smartCardManager: SmartCardManager) : ViewModel() {
    val lastRead: StateFlow<String> = smartCardManager.results
        .map { result ->
            when (result) {
                is NfcReadResult.Success -> "Student ID: ${result.studentId}"
                NfcReadResult.AuthFailed -> "Authentication failed: none of the keys opened sector 6"
                is NfcReadResult.Error -> "Error: ${result.message}"
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Tap a card")
}

/** Shows the raw read result. Used to check that a phone can read RHUL cards. */
@Composable
fun NfcDebugScreen(viewModel: NfcDebugViewModel = hiltViewModel()) {
    val lastRead by viewModel.lastRead.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("NFC test", style = MaterialTheme.typography.titleLarge)
        Text(lastRead, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    }
}
