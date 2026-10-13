package com.rhulhiking.attendance.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhulhiking.attendance.ui.common.AuthResolutionHandler

@Composable
fun SetupScreen(onDone: () -> Unit, viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalActivity.current
    AuthResolutionHandler(viewModel)
    LaunchedEffect(state.done) {
        if (state.done) {
            viewModel.onNavigated()
            onDone()
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Set up", style = MaterialTheme.typography.headlineLarge)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("1. Sign in with Google", style = MaterialTheme.typography.titleLarge)
                if (state.authorized) {
                    Text("Signed in as ${state.email ?: "your Google account"}", style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = viewModel::signOut, enabled = !state.busy) { Text("Use a different account") }
                } else {
                    Text(
                        "Use the club or committee Google account that can edit the attendance sheet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = { activity?.let(viewModel::signIn) }, enabled = !state.busy && activity != null, modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in with Google")
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("2. Attendance spreadsheet", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = state.sheetInput,
                    onValueChange = viewModel::onSheetInput,
                    label = { Text("Spreadsheet link or ID") },
                    singleLine = true,
                    enabled = state.authorized && !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::saveSpreadsheet,
                    enabled = state.authorized && !state.busy && state.sheetInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Continue") }
            }
        }

        if (state.busy) CircularProgressIndicator()
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
