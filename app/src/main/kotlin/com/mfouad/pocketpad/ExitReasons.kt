package com.mfouad.pocketpad

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build

/** Why the previous process ended (API 30+ only). */
object ExitReasons {
    /**
     * True if the last exit was the user stopping the app (for example from the Task Manager
     * on Android 13+). No callback runs in that case, so held input could not be released.
     */
    fun lastExitWasUserStop(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val last = am.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return false
        return last.reason == ApplicationExitInfo.REASON_USER_REQUESTED
    }
}
