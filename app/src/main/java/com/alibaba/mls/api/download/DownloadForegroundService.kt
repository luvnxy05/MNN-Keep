package com.alibaba.mls.api.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.alibaba.mnnllm.android.R as AppR
import com.alibaba.mnnllm.android.main.MainActivity

class DownloadForegroundService : Service() {
    private lateinit var notificationManager: NotificationManager
    private var currentDownloadCount = 0
    private var currentModelName: String? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
        instance = this
        Log.d(TAG, "onCreate")
        // Foreground ASAP: the system starts a 5s clock the moment
        // startForegroundService() is called, and cold process startup
        // (slow Application.onCreate on low-end devices / vendor freezers)
        // can blow that budget before onStartCommand ever runs.
        // Calling startForeground() here is the earliest legal point.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(SERVICE_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(SERVICE_ID, createNotification())
            }
            Log.d(TAG, "startForeground in onCreate succeeded")
        } catch (e: Exception) {
            Log.e(TAG, "startForeground in onCreate failed", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(AppR.string.download_service_title),
            NotificationManager.IMPORTANCE_LOW
        )
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.setSound(null, null)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.w(TAG, "onStartCommand received null intent startId=$startId")
            // Never stopSelf() before startForeground() when launched via
            // startForegroundService(), otherwise Android throws
            // ForegroundServiceDidNotStartInTimeException.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(SERVICE_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } else {
                    startForeground(SERVICE_ID, createNotification())
                }
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed on null intent", e)
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        
        // Extract download count and model name from intent if available
        currentDownloadCount = intent.getIntExtra(EXTRA_DOWNLOAD_COUNT, 0)
        currentModelName = intent.getStringExtra(EXTRA_MODEL_NAME)
        Log.d(
            TAG,
            "onStartCommand startId=$startId flags=$flags count=$currentDownloadCount modelName=$currentModelName"
        )
        
        val notification = createNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(SERVICE_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(SERVICE_ID, notification)
            }
            Log.d(TAG, "startForeground succeeded startId=$startId")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "startForeground failed startId=$startId count=$currentDownloadCount modelName=$currentModelName",
                e
            )
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun createNotification(): Notification {
        val contentTitle = getString(AppR.string.download_service_title)
        val contentText = when {
            currentDownloadCount <= 0 -> getString(AppR.string.downloading_please_wait)
            currentDownloadCount == 1 && currentModelName != null -> {
                getString(AppR.string.downloading_single_model, currentModelName)
            }
            else -> {
                getString(AppR.string.downloading_multiple_models, currentDownloadCount)
            }
        }

        // Create intent to open MainActivity and select ModelMarketFragment
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SELECT_TAB, MainActivity.TAB_MODEL_MARKET)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            // Use android system download icon if available, or a fallback
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            
        return builder.build()
    }

    fun updateNotification(downloadCount: Int, modelName: String? = null) {
        currentDownloadCount = downloadCount
        currentModelName = modelName
        Log.d(TAG, "updateNotification count=$downloadCount modelName=$modelName")
        val notification = createNotification()
        notificationManager.notify(SERVICE_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "onDestroy")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    companion object {
        private const val TAG = "DownloadForegroundSvc"
        private const val CHANNEL_ID = "DownloadServiceChannel"
        private const val SERVICE_ID = 8888
        const val EXTRA_DOWNLOAD_COUNT = "download_count"
        const val EXTRA_MODEL_NAME = "model_name"
        
        @Volatile
        private var instance: DownloadForegroundService? = null
        
        fun getInstance(): DownloadForegroundService? = instance
        
        fun updateNotification(downloadCount: Int, modelName: String? = null) {
            instance?.updateNotification(downloadCount, modelName)
        }
    }
}
