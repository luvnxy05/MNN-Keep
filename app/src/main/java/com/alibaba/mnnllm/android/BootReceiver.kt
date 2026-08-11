package com.alibaba.mnnllm.android

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alibaba.mnnllm.api.openai.service.OpenAIService

/**
 * Boot auto-start for the API service.
 *
 * Deliberately two-phase: BOOT_COMPLETED only schedules an alarm 30s later,
 * then the second phase does a plain startService(). Android 15 restricts
 * starting dataSync foreground services directly from BOOT_COMPLETED, so we
 * wait for the boot-time restriction window to pass and let OpenAIService
 * promote itself to foreground inside onCreate/onStartCommand.
 *
 * Note: vendor ROMs (HyperOS/HarmonyOS/MagicOS) additionally require the user
 * to allow auto-start in their app-startup manager — the app's vendor-guide
 * page covers that.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val ACTION_AUTO_START = "com.alibaba.mnnllm.android.action.AUTO_START"
        private const val BOOT_START_DELAY_MS = 30000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.i(TAG, "BOOT_COMPLETED received, scheduling API service auto-start")
                try {
                    val pi = PendingIntent.getBroadcast(
                        context,
                        1,
                        Intent(context, BootReceiver::class.java).setAction(ACTION_AUTO_START),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + BOOT_START_DELAY_MS, pi)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to schedule auto-start", e)
                }
            }

            ACTION_AUTO_START -> {
                Log.i(TAG, "Auto-starting API service")
                try {
                    val serviceIntent = Intent(context, OpenAIService::class.java)
                    serviceIntent.putExtra("autoStart", true)
                    context.startService(serviceIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Auto-start service failed", e)
                }
            }
        }
    }
}
