package com.rhulhiking.attendance.ui.scan

/** What the scanning screen is showing. Everything except [Ready] is a full-screen state. */
sealed interface ScanDisplay {
    data object Ready : ScanDisplay
    data object Working : ScanDisplay
    data class CheckedIn(val name: String, val matchCount: Int) : ScanDisplay
    data class AlreadyHere(val name: String, val matchCount: Int) : ScanDisplay
    data class NotFound(val studentId: String, val walkInAllowed: Boolean) : ScanDisplay
    data class WalkInAdded(val studentId: String) : ScanDisplay
    data class Queued(val studentId: String) : ScanDisplay
    data class Problem(val title: String, val message: String) : ScanDisplay
}
