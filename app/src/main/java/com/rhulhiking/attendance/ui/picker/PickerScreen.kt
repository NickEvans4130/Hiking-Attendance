package com.rhulhiking.attendance.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhulhiking.attendance.ui.common.AuthResolutionHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerScreen(
    onStart: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onNeedsSetup: () -> Unit,
    viewModel: PickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthResolutionHandler(viewModel)
    LaunchedEffect(state.needsSetup) { if (state.needsSetup) onNeedsSetup() }

    var expanded by remember { mutableStateOf(false) }
    var manageHidden by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start a session", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }

        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it && !state.loading }) {
            OutlinedTextField(
                value = state.selected ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Hike") },
                placeholder = { Text(if (state.loading) "Loading tabs..." else "Choose a hike") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.visibleTabs.forEach { tab ->
                    DropdownMenuItem(
                        text = { Text(tab, style = MaterialTheme.typography.titleMedium) },
                        onClick = {
                            viewModel.select(tab)
                            expanded = false
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
            }
        }

        Button(
            onClick = { state.selected?.let(onStart) },
            enabled = state.selected != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        ) { Text("Start taking attendance", style = MaterialTheme.typography.titleLarge) }

        if (state.loading) CircularProgressIndicator()
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { viewModel.load() }) { Text("Try again") }
        }

        HorizontalDivider()
        TextButton(onClick = { manageHidden = !manageHidden }) {
            Text(if (manageHidden) "Done hiding tabs" else "Hide non-hike tabs (${state.hidden.count { it in state.allTabs }} hidden)")
        }
        if (manageHidden) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Remember hidden tabs", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.rememberHidden, onCheckedChange = viewModel::setRememberHidden)
            }
            state.allTabs.forEach { tab ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tab in state.hidden, onCheckedChange = { viewModel.setHidden(tab, it) })
                    Text(tab, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
