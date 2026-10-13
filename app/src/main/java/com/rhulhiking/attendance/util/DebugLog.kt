package com.rhulhiking.attendance.util

import android.util.Log
import com.rhulhiking.attendance.BuildConfig

/**
 * Logging that only exists in debug builds. Messages are lambdas so release builds never even
 * build strings containing student IDs, names or tokens. R8 also strips android.util.Log in
 * release (see proguard-rules.pro).
 */
object DebugLog {
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    inline fun w(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, message(), throwable)
    }
}
