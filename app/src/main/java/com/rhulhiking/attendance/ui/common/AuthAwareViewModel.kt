package com.rhulhiking.attendance.ui.common

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.rhulhiking.attendance.auth.GoogleAuthManager
import com.rhulhiking.attendance.sheets.SheetsException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Base for screens that call Sheets. When a call needs the user to (re)grant access, the
 * consent UI is launched and the failed action retried afterwards.
 */
abstract class AuthAwareViewModel(private val auth: GoogleAuthManager) : ViewModel() {
    private val _authResolution = MutableStateFlow<PendingIntent?>(null)
    val authResolution: StateFlow<PendingIntent?> = _authResolution
    private var retryAfterAuth: (suspend () -> Unit)? = null

    /** Starts the consent flow if possible. Returns false if there is no UI to show. */
    protected fun requestAuth(e: SheetsException.AuthRequired, retry: suspend () -> Unit): Boolean {
        val resolution = e.resolution ?: return false
        retryAfterAuth = retry
        _authResolution.value = resolution
        return true
    }

    fun onAuthLaunched() {
        _authResolution.value = null
    }

    fun onAuthResult(ok: Boolean, data: Intent?) {
        val retry = retryAfterAuth
        retryAfterAuth = null
        if (!ok) {
            onAuthDeclined()
            return
        }
        viewModelScope.launch {
            try {
                auth.completeAuthorization(data)
                retry?.invoke()
            } catch (e: SheetsException) {
                onAuthFailed(e)
            }
        }
    }

    protected open fun onAuthDeclined() = Unit
    protected open fun onAuthFailed(e: SheetsException) = Unit
}

/** Launches the Google consent screen whenever [viewModel] asks for it. */
@Composable
fun AuthResolutionHandler(viewModel: AuthAwareViewModel) {
    val resolution by viewModel.authResolution.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        viewModel.onAuthResult(r.resultCode == Activity.RESULT_OK, r.data)
    }
    LaunchedEffect(resolution) {
        val pi = resolution ?: return@LaunchedEffect
        viewModel.onAuthLaunched()
        launcher.launch(IntentSenderRequest.Builder(pi).build())
    }
}
