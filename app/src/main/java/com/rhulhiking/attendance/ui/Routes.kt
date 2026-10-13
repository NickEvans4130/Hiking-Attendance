package com.rhulhiking.attendance.ui

import kotlinx.serialization.Serializable

@Serializable data object SetupRoute
@Serializable data object PickerRoute
@Serializable data class ScanRoute(val tab: String)
@Serializable data object SettingsRoute
@Serializable data object NfcTestRoute
