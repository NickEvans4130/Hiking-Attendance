package com.rhulhiking.attendance.nfc

sealed interface NfcReadResult {
    data class Success(val studentId: String) : NfcReadResult
    data object AuthFailed : NfcReadResult
    data class Error(val message: String) : NfcReadResult
}
