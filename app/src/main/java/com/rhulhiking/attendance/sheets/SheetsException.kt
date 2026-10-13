package com.rhulhiking.attendance.sheets

import android.app.PendingIntent

/** Failures talking to Google Sheets, each with a message fit to show a committee member. */
sealed class SheetsException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** Worth queueing and retrying later: the request never reached a working Sheets backend. */
    open val isTransient: Boolean get() = false

    class Network(cause: Throwable? = null) :
        SheetsException("No connection to Google Sheets.", cause) {
        override val isTransient get() = true
    }

    class RateLimited :
        SheetsException("Google Sheets is busy (rate limited). Will retry shortly.") {
        override val isTransient get() = true
    }

    class Server(val code: Int) :
        SheetsException("Google Sheets had a problem (HTTP $code). Will retry shortly.") {
        override val isTransient get() = true
    }

    class NoAccess :
        SheetsException("This Google account doesn't have edit access to the sheet. Ask for it to be shared with you as an Editor.")

    class ApiDisabled :
        SheetsException("The Google Sheets API isn't enabled for this app's Google Cloud project. See the README setup steps.")

    class NotFound :
        SheetsException("Spreadsheet or tab not found. Check the spreadsheet link and that the hike tab still exists.")

    class ProtectedCell :
        SheetsException("The status cell is protected in the sheet. Ask the sheet owner to let this account edit it.")

    class OutsideGrid :
        SheetsException("A configured column is outside this tab's grid. Check the column letters in Settings.")

    /** The user must sign in or grant access again. [resolution] launches the consent UI when available. */
    class AuthRequired(val resolution: PendingIntent? = null) :
        SheetsException("Your Google sign-in needs to be renewed. Sign in again to continue.")

    class Unexpected(val code: Int, detail: String) :
        SheetsException("Unexpected response from Google Sheets (HTTP $code): $detail")
}
