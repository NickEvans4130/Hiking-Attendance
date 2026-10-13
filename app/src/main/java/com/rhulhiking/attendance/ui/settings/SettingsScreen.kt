package com.rhulhiking.attendance.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onChangeAccountOrSheet: () -> Unit,
    onOpenNfcTest: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("Done") }
        }

        Section("Account and spreadsheet") {
            Text("Signed in as ${state.accountEmail ?: "nobody"}", style = MaterialTheme.typography.bodyLarge)
            Text("Spreadsheet: ${state.spreadsheetId ?: "not set"}", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onChangeAccountOrSheet, modifier = Modifier.fillMaxWidth()) {
                Text("Change account or spreadsheet")
            }
        }

        Section("Sheet columns") {
            Text(
                "The app only reads these three columns and only ever writes the status column.",
                style = MaterialTheme.typography.bodyMedium,
            )
            ColumnField("Student ID column", state.columns.idColumn) { v -> viewModel.editColumns { it.copy(idColumn = v) } }
            ColumnField("Name column", state.columns.nameColumn) { v -> viewModel.editColumns { it.copy(nameColumn = v) } }
            ColumnField("Status column", state.columns.statusColumn) { v -> viewModel.editColumns { it.copy(statusColumn = v) } }
            OutlinedTextField(
                value = state.columns.presentValue,
                onValueChange = { v -> viewModel.editColumns { it.copy(presentValue = v) } },
                label = { Text("Value written when present") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            state.columnError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::saveColumns) { Text("Save columns") }
                OutlinedButton(onClick = viewModel::resetColumns) { Text("Defaults") }
            }
        }

        Section("Walk-ins") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Allow adding walk-ins", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Adds a row with only the time, student ID and status for people not signed up.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Switch(checked = state.walkInsEnabled, onCheckedChange = viewModel::setWalkIns)
            }
        }

        Section("Other") {
            OutlinedButton(onClick = viewModel::resetHiddenTabs, modifier = Modifier.fillMaxWidth()) {
                Text("Reset hidden tabs to defaults")
            }
            OutlinedButton(onClick = onOpenNfcTest, modifier = Modifier.fillMaxWidth()) {
                Text("NFC card read test")
            }
        }

        state.savedMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
private fun ColumnField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isLetter).uppercase().take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        modifier = Modifier.fillMaxWidth(),
    )
}
