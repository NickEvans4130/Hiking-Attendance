package com.rhulhiking.attendance.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhulhiking.attendance.settings.AppSettings
import com.rhulhiking.attendance.settings.ColumnConfig
import com.rhulhiking.attendance.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val loaded: Boolean = false,
    val accountEmail: String? = null,
    val spreadsheetId: String? = null,
    val columns: ColumnConfig = ColumnConfig(),
    val walkInsEnabled: Boolean = true,
    val columnError: String? = null,
    val savedMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state

    init {
        viewModelScope.launch {
            val s = settings.current()
            _state.value = SettingsUiState(
                loaded = true,
                accountEmail = s.accountEmail,
                spreadsheetId = s.spreadsheetId,
                columns = s.columns,
                walkInsEnabled = s.walkInsEnabled,
            )
        }
    }

    fun editColumns(transform: (ColumnConfig) -> ColumnConfig) =
        _state.update { it.copy(columns = transform(it.columns), columnError = null, savedMessage = null) }

    fun saveColumns() {
        val c = _state.value.columns.normalized()
        val error = c.validate()
        if (error != null) return _state.update { it.copy(columnError = error) }
        viewModelScope.launch {
            settings.setColumns(c)
            _state.update { it.copy(columns = c, savedMessage = "Columns saved") }
        }
    }

    fun resetColumns() = editColumns { ColumnConfig() }

    fun setWalkIns(enabled: Boolean) {
        _state.update { it.copy(walkInsEnabled = enabled) }
        viewModelScope.launch { settings.setWalkInsEnabled(enabled) }
    }

    fun resetHiddenTabs() = viewModelScope.launch {
        settings.setHiddenTabs(AppSettings.DEFAULT_HIDDEN_TABS)
        _state.update { it.copy(savedMessage = "Hidden tabs reset") }
    }
}
