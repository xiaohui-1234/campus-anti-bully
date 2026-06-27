package com.campus.android.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.campus.android.CampusApplication
import com.campus.android.core.security.SensitiveLog
import com.campus.android.notifications.CampusNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RealtimeForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var subscribeJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        startRealtimeForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as CampusApplication
        if (!app.tokenStore.isLoggedIn()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startRealtimeForeground()
        subscribeBoundDevices()
        return START_STICKY
    }

    override fun onDestroy() {
        subscribeJob?.cancel()
        (application as CampusApplication).realtimeClient.close()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startRealtimeForeground() {
        val notification = (application as CampusApplication).notifier.foregroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                CampusNotifier.FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(CampusNotifier.FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun subscribeBoundDevices() {
        val app = application as CampusApplication
        subscribeJob?.cancel()
        subscribeJob = serviceScope.launch {
            runCatching {
                val devices = app.repository.devices(page = 1, size = DEVICE_PAGE_SIZE).records
                app.realtimeClient.subscribeEvents(devices.map { it.deviceId })
            }.onFailure {
                SensitiveLog.w(TAG, "Realtime device subscription failed", it)
                app.realtimeClient.connect()
            }
        }
    }

    companion object {
        private const val TAG = "RealtimeService"
        private const val DEVICE_PAGE_SIZE = 100L

        fun start(context: Context) {
            val intent = Intent(context, RealtimeForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RealtimeForegroundService::class.java))
        }
    }
}
