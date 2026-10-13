package com.rhulhiking.attendance.ui.picker

import androidx.lifecycle.viewModelScope
import com.rhulhiking.attendance.attendance.CheckInService
import com.rhulhiking.attendance.auth.GoogleAuthManager
import com.rhulhiking.attendance.settings.SettingsRepository
import com.rhulhiking.attendance.sheets.SheetsException
import com.rhulhiking.attendance.ui.common.AuthAwareViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PickerUiState(
    val allTabs: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val rememberHidden: Boolean = true,
    val selected: String? = null,
    val loading: Boolean = true,
    val error: String? = null,
    val needsSetup: Boolean = false,
) {
    val visibleTabs: List<String> get() = allTabs.filterNot { it in hidden }
}

@HiltViewModel
class PickerViewModel @Inject constructor(
    auth: GoogleAuthManager,
    private val settings: SettingsRepository,
    private val service: CheckInService,
) : AuthAwareViewModel(auth) {

    private val _state = MutableStateFlow(PickerUiState())
    val state: StateFlow<PickerUiState> = _state

    init {
        viewModelScope.launch {
            val s = settings.current()
            _state.update { it.copy(hidden = s.hiddenTabs, rememberHidden = s.rememberHiddenTabs) }
            load()
        }
    }

    fun load() = viewModelScope.launch { loadTabs() }

    private suspend fun loadTabs() {
        val id = settings.current().spreadsheetId ?: return _state.update { it.copy(needsSetup = true) }
        _state.update { it.copy(loading = true, error = null) }
        try {
            val tabs = service.listTabs(id)
            _state.update { st ->
                val visible = tabs.filterNot { it in st.hidden }
                st.copy(allTabs = tabs, loading = false, selected = st.selected?.takeIf { it in visible })
            }
        } catch (e: SheetsException.AuthRequired) {
            if (!requestAuth(e) { loadTabs() }) _state.update { it.copy(loading = false, error = e.message) }
        } catch (e: SheetsException) {
            _state.update { it.copy(loading = false, error = e.message) }
        }
    }

    fun select(tab: String) = _state.update { it.copy(selected = tab) }

    fun setHidden(tab: String, hidden: Boolean) {
        _state.update { st ->
            val set = if (hidden) st.hidden + tab else st.hidden - tab
            st.copy(hidden = set, selected = st.selected?.takeUnless { hidden && it == tab })
        }
        persistHidden()
    }

    fun setRememberHidden(remember: Boolean) {
        _state.update { it.copy(rememberHidden = remember) }
        viewModelScope.launch { settings.setRememberHiddenTabs(remember) }
        persistHidden()
    }

    /** Hidden tabs only survive this screen when "remember" is on. */
    private fun persistHidden() {
        val st = _state.value
        if (st.rememberHidden) viewModelScope.launch { settings.setHiddenTabs(st.hidden) }
    }

    override fun onAuthDeclined() = _state.update { it.copy(loading = false, error = "Access to Google Sheets is needed.") }
    override fun onAuthFailed(e: SheetsException) = _state.update { it.copy(loading = false, error = e.message) }
}
