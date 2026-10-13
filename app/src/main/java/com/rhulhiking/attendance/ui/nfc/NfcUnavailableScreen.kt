package com.rhulhiking.attendance.ui.nfc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rhulhiking.attendance.nfc.NfcStatus

@Composable
fun NfcUnavailableScreen(
    status: NfcStatus,
    onOpenNfcSettings: () -> Unit,
    onTryAnyway: () -> Unit,
    onRecheck: () -> Unit,
) {
    val (title, body) = when (status) {
        NfcStatus.NoNfcHardware -> "This phone has no NFC" to
            "Hiking Attendance reads student cards over NFC, and this phone doesn't have an NFC " +
            "reader. Use a different committee phone."
        NfcStatus.NoMifareSupport -> "This phone can't read student cards" to
            "RHUL student cards use MIFARE Classic. This phone has NFC, but its NFC chip doesn't " +
            "support MIFARE Classic. This is common on Google Pixels and some other phones. " +
            "Use a phone with an NXP NFC chip, such as most Samsung phones.\n\n" +
            "If you think this is wrong, you can try anyway. The card read will fail if the " +
            "chip really is unsupported."
        NfcStatus.Disabled -> "NFC is turned off" to
            "Turn NFC on in your phone's settings, then come back to this app."
        NfcStatus.Available -> "NFC is ready" to ""
    }

    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        when (status) {
            NfcStatus.Disabled -> Button(onClick = onOpenNfcSettings, Modifier.fillMaxWidth()) {
                Text("Open NFC settings")
            }
            NfcStatus.NoMifareSupport -> OutlinedButton(onClick = onTryAnyway, Modifier.fillMaxWidth()) {
                Text("Try anyway")
            }
            else -> Unit
        }
        OutlinedButton(onClick = onRecheck, Modifier.fillMaxWidth()) { Text("Check again") }
    }
}
