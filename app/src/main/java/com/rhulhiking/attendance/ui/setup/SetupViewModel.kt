package com.rhulhiking.attendance.ui.setup

import android.app.Activity
import androidx.lifecycle.viewModelScope
import com.rhulhiking.attendance.attendance.CheckInService
import com.rhulhiking.attendance.auth.GoogleAuthManager
import com.rhulhiking.attendance.auth.SignInResult
import com.rhulhiking.attendance.settings.SettingsRepository
import com.rhulhiking.attendance.sheets.SheetsException
import com.rhulhiking.attendance.sheets.SpreadsheetIds
import com.rhulhiking.attendance.ui.common.AuthAwareViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val email: String? = null,
    val authorized: Boolean = false,
    val sheetInput: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val done: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val auth: GoogleAuthManager,
    private val settings: SettingsRepository,
    private val service: CheckInService,
) : AuthAwareViewModel(auth) {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    init {
        viewModelScope.launch {
            val s = settings.current()
            _state.update { it.copy(email = s.accountEmail, sheetInput = s.spreadsheetId.orEmpty()) }
            // Silently confirm an existing grant so a returning user skips step 1.
            if (s.accountEmail != null) {
                runCatching { auth.accessToken() }.onSuccess { _state.update { st -> st.copy(authorized = true) } }
            }
        }
    }

    fun signIn(activity: Activity) = viewModelScope.launch {
        _state.update { it.copy(busy = true, error = null) }
        if (auth.credentialSignInAvailable) {
            when (val r = auth.signIn(activity)) {
                SignInResult.Cancelled -> return@launch _state.update { it.copy(busy = false) }
                is SignInResult.Failed -> return@launch _state.update { it.copy(busy = false, error = r.message) }
                is SignInResult.SignedIn -> _state.update { it.copy(email = r.email) }
            }
        }
        authorize()
    }

    private suspend fun authorize() {
        try {
            auth.accessToken()
            _state.update { it.copy(busy = false, authorized = true, email = settings.current().accountEmail) }
        } catch (e: SheetsException.AuthRequired) {
            if (!requestAuth(e) { authorize() }) {
                _state.update { it.copy(busy = false, error = "Google didn't grant access to Sheets. Try again.") }
            }
        } catch (e: SheetsException) {
            _state.update { it.copy(busy = false, error = e.message) }
        }
    }

    fun signOut() = viewModelScope.launch {
        auth.signOut()
        _state.update { SetupUiState(sheetInput = it.sheetInput) }
    }

    fun onSheetInput(text: String) = _state.update { it.copy(sheetInput = text, error = null) }

    fun saveSpreadsheet() = viewModelScope.launch {
        val id = SpreadsheetIds.parse(_state.value.sheetInput)
            ?: return@launch _state.update { it.copy(error = "That doesn't look like a Google Sheets link or ID.") }
        _state.update { it.copy(busy = true, error = null) }
        verifyAndSave(id)
    }

    /** Fetching the tab list confirms the ID is right and this account can open it. */
    private suspend fun verifyAndSave(id: String) {
        try {
            service.listTabs(id)
            settings.setSpreadsheetId(id)
            _state.update { it.copy(busy = false, done = true) }
        } catch (e: SheetsException.AuthRequired) {
            if (!requestAuth(e) { verifyAndSave(id) }) {
                _state.update { it.copy(busy = false, authorized = false, error = e.message) }
            }
        } catch (e: SheetsException) {
            _state.update { it.copy(busy = false, error = e.message) }
        }
    }

    fun onNavigated() = _state.update { it.copy(done = false) }

    override fun onAuthDeclined() = _state.update { it.copy(busy = false, error = "Access to Google Sheets is needed to take attendance.") }
    override fun onAuthFailed(e: SheetsException) = _state.update { it.copy(busy = false, error = e.message) }
}
