package com.viceside.core

import android.util.Log

object CrashReporter {
    private const val TAG = "CrashReporter"

    @JvmStatic
    fun log(message: String) {
        Log.d(TAG, message)
    }

    @JvmStatic
    fun recordException(throwable: Throwable) {
        Log.e(TAG, "Recorded exception", throwable)
    }

    @JvmStatic
    fun setCustomKey(key: String, value: String) {
        Log.d(TAG, "Custom key: $key = $value")
    }

    @JvmStatic
    fun deleteUnsentReports() {
        // No-op
    }

    @JvmStatic
    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        // No-op
    }
}
