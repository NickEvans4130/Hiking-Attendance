package com.rhulhiking.attendance.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data.map { p ->
        val defaults = ColumnConfig()
        AppSettings(
            spreadsheetId = p[SPREADSHEET_ID],
            accountEmail = p[ACCOUNT_EMAIL],
            columns = ColumnConfig(
                idColumn = p[ID_COLUMN] ?: defaults.idColumn,
                nameColumn = p[NAME_COLUMN] ?: defaults.nameColumn,
                statusColumn = p[STATUS_COLUMN] ?: defaults.statusColumn,
                presentValue = p[PRESENT_VALUE] ?: defaults.presentValue,
            ),
            walkInsEnabled = p[WALK_INS] ?: true,
            hiddenTabs = p[HIDDEN_TABS] ?: AppSettings.DEFAULT_HIDDEN_TABS,
            rememberHiddenTabs = p[REMEMBER_HIDDEN] ?: true,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setSpreadsheetId(id: String?) = dataStore.edit { p ->
        if (id == null) p.remove(SPREADSHEET_ID) else p[SPREADSHEET_ID] = id
    }

    suspend fun setAccountEmail(email: String?) = dataStore.edit { p ->
        if (email == null) p.remove(ACCOUNT_EMAIL) else p[ACCOUNT_EMAIL] = email
    }

    suspend fun setColumns(columns: ColumnConfig) {
        val c = columns.normalized()
        require(c.validate() == null) { c.validate()!! }
        dataStore.edit { p ->
            p[ID_COLUMN] = c.idColumn
            p[NAME_COLUMN] = c.nameColumn
            p[STATUS_COLUMN] = c.statusColumn
            p[PRESENT_VALUE] = c.presentValue
        }
    }

    suspend fun setWalkInsEnabled(enabled: Boolean) = dataStore.edit { it[WALK_INS] = enabled }

    suspend fun setHiddenTabs(tabs: Set<String>) = dataStore.edit { it[HIDDEN_TABS] = tabs }

    suspend fun setRememberHiddenTabs(remember: Boolean) = dataStore.edit { it[REMEMBER_HIDDEN] = remember }

    private companion object {
        val SPREADSHEET_ID = stringPreferencesKey("spreadsheet_id")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val ID_COLUMN = stringPreferencesKey("id_column")
        val NAME_COLUMN = stringPreferencesKey("name_column")
        val STATUS_COLUMN = stringPreferencesKey("status_column")
        val PRESENT_VALUE = stringPreferencesKey("present_value")
        val WALK_INS = booleanPreferencesKey("walk_ins_enabled")
        val HIDDEN_TABS = stringSetPreferencesKey("hidden_tabs")
        val REMEMBER_HIDDEN = booleanPreferencesKey("remember_hidden_tabs")
    }
}
