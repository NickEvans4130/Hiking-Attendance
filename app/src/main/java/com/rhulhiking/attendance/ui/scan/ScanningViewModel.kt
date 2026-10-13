package com.rhulhiking.attendance.ui.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.rhulhiking.attendance.attendance.CheckInBatch
import com.rhulhiking.attendance.attendance.CheckInOutcome
import com.rhulhiking.attendance.attendance.CheckInService
import com.rhulhiking.attendance.attendance.HikeTarget
import com.rhulhiking.attendance.attendance.RosterSummary
import com.rhulhiking.attendance.auth.GoogleAuthManager
import com.rhulhiking.attendance.feedback.FeedbackPlayer
import com.rhulhiking.attendance.nfc.NfcReadResult
import com.rhulhiking.attendance.nfc.SmartCardManager
import com.rhulhiking.attendance.settings.AppSettings
import com.rhulhiking.attendance.settings.SettingsRepository
import com.rhulhiking.attendance.sheets.SheetsException
import com.rhulhiking.attendance.ui.ScanRoute
import com.rhulhiking.attendance.ui.common.AuthAwareViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class ScanUiState(
    val hikeName: String,
    val summary: RosterSummary? = null,
    val refreshing: Boolean = false,
    val listError: String? = null,
    val display: ScanDisplay = ScanDisplay.Ready,
    val pendingCount: Int = 0,
)

@HiltViewModel
class ScanningViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    auth: GoogleAuthManager,
    private val service: CheckInService,
    private val settings: SettingsRepository,
    private val feedback: FeedbackPlayer,
    smartCardManager: SmartCardManager,
) : AuthAwareViewModel(auth) {

    private val tab = savedStateHandle.toRoute<ScanRoute>().tab
    private val _state = MutableStateFlow(ScanUiState(hikeName = tab))
    val state: StateFlow<ScanUiState> = _state

    /** Taps and button actions run one at a time so writes never interleave. */
    private val sheetLock = Mutex()
    private var dismissJob: Job? = null

    init {
        refresh()
        viewModelScope.launch { smartCardManager.results.collect(::onCardRead) }
    }

    fun refresh() = viewModelScope.launch {
        _state.update { it.copy(refreshing = true) }
        sheetLock.withLock { loadRoster() }
    }

    private suspend fun loadRoster() {
        val s = settings.current()
        val target = target(s) ?: return _state.update { it.copy(refreshing = false) }
        try {
            val roster = service.fetchRoster(target, s.columns)
            _state.update { it.copy(summary = roster.summary(s.columns.presentValue), refreshing = false, listError = null) }
        } catch (e: SheetsException.AuthRequired) {
            _state.update { it.copy(refreshing = false, listError = e.message) }
            requestAuth(e) { sheetLock.withLock { loadRoster() } }
        } catch (e: SheetsException) {
            val msg = if (e.isTransient) "Offline. The list may be out of date." else e.message
            _state.update { it.copy(refreshing = false, listError = msg) }
        }
    }

    private suspend fun onCardRead(result: NfcReadResult) {
        when (result) {
            is NfcReadResult.Success -> sheetLock.withLock { checkIn(result.studentId) }
            NfcReadResult.AuthFailed -> showProblem(
                "Card not recognised",
                "This card's keys didn't match. Is it an RHUL student card? Tap again.",
            )
            is NfcReadResult.Error -> showProblem("Tap again", result.message)
        }
    }

    private suspend fun checkIn(rawId: String) {
        val s = settings.current()
        val target = target(s) ?: return
        show(ScanDisplay.Working)
        try {
            val batch = service.checkIn(target, s.columns, rawId)
            applyBatch(batch, s)
        } catch (e: SheetsException) {
            handleFailure(e, rawId) { sheetLock.withLock { checkIn(rawId) } }
        }
    }

    private fun applyBatch(batch: CheckInBatch, s: AppSettings) {
        _state.update { it.copy(summary = batch.roster.summary(s.columns.presentValue), listError = null) }
        when (val outcome = batch.outcomes.single()) {
            is CheckInOutcome.CheckedIn -> {
                feedback.success()
                show(ScanDisplay.CheckedIn(outcome.displayName, outcome.matchCount))
            }
            is CheckInOutcome.AlreadyHere -> {
                feedback.warning()
                show(ScanDisplay.AlreadyHere(outcome.displayName, outcome.matchCount))
            }
            is CheckInOutcome.NotFound -> {
                feedback.error()
                show(ScanDisplay.NotFound(outcome.studentId, s.walkInsEnabled))
            }
            is CheckInOutcome.WalkInAdded -> {
                feedback.success()
                show(ScanDisplay.WalkInAdded(outcome.studentId))
            }
            is CheckInOutcome.InvalidId -> showProblem("Tap again", "No student number could be read from this card.")
        }
    }

    private suspend fun handleFailure(e: SheetsException, rawId: String, retry: suspend () -> Unit) {
        when {
            e is SheetsException.AuthRequired -> {
                if (!requestAuth(e, retry)) showProblem("Sign in again", e.message.orEmpty())
                else show(ScanDisplay.Problem("Sign-in needed", "Confirm your Google account, then this tap will be retried."))
            }
            else -> showProblem("Couldn't update the sheet", e.message.orEmpty())
        }
    }

    fun addWalkIn(studentId: String) = viewModelScope.launch { sheetLock.withLock { walkIn(studentId) } }

    private suspend fun walkIn(studentId: String) {
        val s = settings.current()
        if (!s.walkInsEnabled) return
        val target = target(s) ?: return
        show(ScanDisplay.Working)
        try {
            applyBatch(service.addWalkIn(target, s.columns, studentId), s)
        } catch (e: SheetsException) {
            if (e.isTransient) {
                showProblem("Walk-in not added", "No connection to Google Sheets. Tap their card again when you have signal.")
            } else {
                handleFailure(e, studentId) { sheetLock.withLock { walkIn(studentId) } }
            }
        }
    }

    fun dismiss() = show(ScanDisplay.Ready)

    private fun showProblem(title: String, message: String) {
        feedback.error()
        show(ScanDisplay.Problem(title, message))
    }

    private fun show(display: ScanDisplay) {
        dismissJob?.cancel()
        _state.update { it.copy(display = display) }
        val timeout = autoDismissMillis(display) ?: return
        dismissJob = viewModelScope.launch {
            delay(timeout)
            _state.update { if (it.display == display) it.copy(display = ScanDisplay.Ready) else it }
        }
    }

    private fun autoDismissMillis(d: ScanDisplay): Long? = when (d) {
        ScanDisplay.Ready, ScanDisplay.Working -> null
        is ScanDisplay.CheckedIn, is ScanDisplay.WalkInAdded, is ScanDisplay.Queued -> 3_000
        is ScanDisplay.AlreadyHere -> 4_000
        is ScanDisplay.Problem -> 5_000
        // Long enough to decide on a walk-in; the next tap replaces it anyway.
        is ScanDisplay.NotFound -> 15_000
    }

    private fun target(s: AppSettings): HikeTarget? = s.spreadsheetId?.let { HikeTarget(it, tab) }

    override fun onAuthDeclined() = show(ScanDisplay.Problem("Not signed in", "Access to Google Sheets is needed. Tap the card again to retry."))
    override fun onAuthFailed(e: SheetsException) = showProblem("Sign-in failed", e.message.orEmpty())
}
