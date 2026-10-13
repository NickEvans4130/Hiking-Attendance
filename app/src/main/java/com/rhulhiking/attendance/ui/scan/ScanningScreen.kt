package com.rhulhiking.attendance.ui.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhulhiking.attendance.attendance.Attendee
import com.rhulhiking.attendance.ui.common.AuthResolutionHandler
import com.rhulhiking.attendance.ui.theme.StatusColors

@Composable
fun ScanningScreen(onEndSession: () -> Unit, viewModel: ScanningViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthResolutionHandler(viewModel)
    KeepScreenOn()
    BackHandler(onBack = onEndSession)

    Box(Modifier.fillMaxSize()) {
        ScanningContent(state, onEndSession, viewModel::refresh)
        if (state.display != ScanDisplay.Ready) {
            OutcomeOverlay(
                display = state.display,
                onDismiss = viewModel::dismiss,
                onAddWalkIn = viewModel::addWalkIn,
            )
        }
    }
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScanningContent(state: ScanUiState, onEndSession: () -> Unit, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.hikeName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onEndSession) { Text("End session") }
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(24.dp))
                .padding(vertical = 28.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Tap student card",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                "Hold it flat against the back of the phone",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val summary = state.summary
                Text(
                    if (summary == null) "- / - here" else "${summary.hereCount} / ${summary.total} here",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (state.pendingCount > 0) {
                    Badge(containerColor = StatusColors.Info, contentColor = StatusColors.OnInfo) {
                        Text("${state.pendingCount} to sync", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }

        state.listError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        }

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
            AttendeeList(state.summary?.attendees.orEmpty())
        }
    }
}

@Composable
private fun AttendeeList(attendees: List<Attendee>) {
    val (here, notHere) = remember(attendees) { attendees.partition { it.here } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        item { SectionHeader("Not here yet (${notHere.size})") }
        items(notHere, key = { "n" + it.studentId }) { AttendeeRow(it) }
        item { SectionHeader("Here (${here.size})") }
        items(here, key = { "h" + it.studentId }) { AttendeeRow(it) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun AttendeeRow(a: Attendee) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp).background(
                if (a.here) StatusColors.Success else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(7.dp),
            ),
        )
        Spacer(Modifier.size(14.dp))
        Text(a.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    HorizontalDivider()
}

@Composable
private fun OutcomeOverlay(display: ScanDisplay, onDismiss: () -> Unit, onAddWalkIn: (String) -> Unit) {
    val (bg, fg) = when (display) {
        is ScanDisplay.CheckedIn, is ScanDisplay.WalkInAdded -> StatusColors.Success to StatusColors.OnSuccess
        is ScanDisplay.AlreadyHere -> StatusColors.Warning to StatusColors.OnWarning
        is ScanDisplay.NotFound, is ScanDisplay.Problem -> StatusColors.Error to StatusColors.OnError
        is ScanDisplay.Queued -> StatusColors.Info to StatusColors.OnInfo
        ScanDisplay.Working, ScanDisplay.Ready -> MaterialTheme.colorScheme.surface to MaterialTheme.colorScheme.onSurface
    }
    Surface(
        color = bg,
        contentColor = fg,
        modifier = Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            enabled = display != ScanDisplay.Working,
            onClick = onDismiss,
        ),
    ) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (display) {
                ScanDisplay.Working -> {
                    CircularProgressIndicator(Modifier.size(72.dp), strokeWidth = 6.dp)
                    Big("Checking...")
                }
                is ScanDisplay.CheckedIn -> {
                    Big("Welcome!")
                    Huge(display.name)
                    DuplicateNote(display.matchCount)
                }
                is ScanDisplay.AlreadyHere -> {
                    Big("Already registered")
                    Huge(display.name)
                    DuplicateNote(display.matchCount)
                }
                is ScanDisplay.NotFound -> {
                    Big("Not signed up for this hike")
                    Body("Student ${display.studentId}")
                    if (display.walkInAllowed) {
                        Button(
                            onClick = { onAddWalkIn(display.studentId) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = bg),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
                        ) { Text("Add as walk-in", style = MaterialTheme.typography.titleLarge) }
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                        Text("Dismiss", color = fg, style = MaterialTheme.typography.titleMedium)
                    }
                }
                is ScanDisplay.WalkInAdded -> {
                    Big("Walk-in added")
                    Huge(display.studentId)
                }
                is ScanDisplay.Queued -> {
                    Big("Queued: will sync")
                    Huge(display.studentId)
                    Body("No signal. This check-in is saved and will be written to the sheet automatically.")
                }
                is ScanDisplay.Problem -> {
                    Big(display.title)
                    Body(display.message)
                }
                ScanDisplay.Ready -> Unit
            }
        }
    }
}

@Composable private fun Huge(text: String) =
    Text(text, fontSize = 44.sp, lineHeight = 50.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)

@Composable private fun Big(text: String) =
    Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

@Composable private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)

@Composable private fun DuplicateNote(matchCount: Int) {
    if (matchCount > 1) Body("Note: signed up $matchCount times. All rows marked.")
}
