package com.rhulhiking.attendance.auth

/** Supplies OAuth access tokens for the Sheets API. */
interface AccessTokenProvider {
    /** A valid token. Throws SheetsException.AuthRequired if the user must interact first. */
    suspend fun accessToken(): String

    /** Drops [token] (e.g. after a 401) so the next [accessToken] call fetches a fresh one. */
    suspend fun invalidate(token: String)
}
