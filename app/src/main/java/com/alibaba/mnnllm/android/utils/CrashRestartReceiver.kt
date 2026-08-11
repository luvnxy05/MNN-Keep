package com.alibaba.mnnllm.android.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Fired by [CrashUtil] ~3s after an uncaught exception: relaunches the app so
 * unattended devices recover by themselves after a crash.
 */
class CrashRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                launch.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                context.startActivity(launch)
                Log.i(TAG, "Auto-restart after crash: launching main activity")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Auto-restart failed", e)
        }
    }

    companion object {
        private const val TAG = "CrashRestartReceiver"
    }
}
