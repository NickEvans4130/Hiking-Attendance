package com.rhulhiking.attendance.auth

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.rhulhiking.attendance.BuildConfig
import com.rhulhiking.attendance.settings.SettingsRepository
import com.rhulhiking.attendance.sheets.SheetsException
import com.rhulhiking.attendance.util.Clock
import com.rhulhiking.attendance.util.DebugLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SignInResult {
    data class SignedIn(val email: String) : SignInResult
    data object Cancelled : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * Google sign-in via Credential Manager, then Sheets access via the Google Identity Services
 * AuthorizationClient. Access tokens are held in memory only.
 */
@Singleton
class GoogleAuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock,
) : AccessTokenProvider {

    private val authorizationClient = Identity.getAuthorizationClient(context)
    private val credentialManager = CredentialManager.create(context)
    private val mutex = Mutex()

    private data class CachedToken(val token: String, val obtainedAt: Long)

    @Volatile private var cached: CachedToken? = null

    /** Whether Credential Manager sign-in is configured (needs a Web OAuth client ID). */
    val credentialSignInAvailable: Boolean get() = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Picks the Google account with Credential Manager. Without a Web client ID this is skipped
     * and the account is chosen in the authorisation consent screen instead.
     */
    suspend fun signIn(activity: Activity): SignInResult {
        if (!credentialSignInAvailable) return SignInResult.Failed("Sign-in isn't configured")
        val option = GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = credentialManager.getCredential(activity, request).credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val email = GoogleIdTokenCredential.createFrom(credential.data).id
                switchAccount(email)
                SignInResult.SignedIn(email)
            } else {
                SignInResult.Failed("Unexpected credential type")
            }
        } catch (e: GetCredentialCancellationException) {
            SignInResult.Cancelled
        } catch (e: GetCredentialException) {
            DebugLog.w(TAG, e) { "Credential Manager sign-in failed" }
            SignInResult.Failed(e.message ?: "Google sign-in failed")
        }
    }

    override suspend fun accessToken(): String = mutex.withLock {
        cached?.takeIf { clock.elapsedMillis() - it.obtainedAt < TOKEN_REUSE_MS }?.let { return it.token }
        val result = try {
            authorizationClient.authorize(buildRequest()).await()
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
        acceptResult(result)
    }

    /**
     * Completes an interactive authorisation started from [SheetsException.AuthRequired.resolution].
     * Returns the account email.
     */
    suspend fun completeAuthorization(data: Intent?): String? {
        val result = try {
            authorizationClient.getAuthorizationResultFromIntent(data)
        } catch (e: ApiException) {
            throw mapApiException(e)
        }
        mutex.withLock { acceptResult(result) }
        val email = result.toGoogleSignInAccount()?.email
        if (email != null && settings.current().accountEmail == null) settings.setAccountEmail(email)
        return email ?: settings.current().accountEmail
    }

    override suspend fun invalidate(token: String) {
        mutex.withLock { if (cached?.token == token) cached = null }
        runCatching {
            authorizationClient.clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
        }
    }

    suspend fun signOut() {
        cached?.token?.let { invalidate(it) }
        cached = null
        settings.setAccountEmail(null)
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
    }

    private suspend fun switchAccount(email: String) {
        if (settings.current().accountEmail != email) {
            mutex.withLock { cached = null }
            settings.setAccountEmail(email)
        }
    }

    private suspend fun buildRequest(): AuthorizationRequest {
        val email = settings.current().accountEmail
        return AuthorizationRequest.Builder()
            .setRequestedScopes(listOf(Scope(SHEETS_SCOPE)))
            .apply { if (email != null) setAccount(Account(email, ACCOUNT_TYPE)) }
            .build()
    }

    private fun acceptResult(result: AuthorizationResult): String {
        if (result.hasResolution()) throw SheetsException.AuthRequired(result.pendingIntent)
        val token = result.accessToken ?: throw SheetsException.AuthRequired()
        cached = CachedToken(token, clock.elapsedMillis())
        return token
    }

    private fun mapApiException(e: ApiException): SheetsException = when (e.statusCode) {
        CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> SheetsException.Network(e)
        else -> SheetsException.AuthRequired()
    }

    companion object {
        const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
        private const val ACCOUNT_TYPE = "com.google"
        /** Google access tokens last an hour; reuse ours for 45 minutes, then ask GIS again. */
        private const val TOKEN_REUSE_MS = 45 * 60 * 1000L
        private const val TAG = "GoogleAuth"
    }
}
