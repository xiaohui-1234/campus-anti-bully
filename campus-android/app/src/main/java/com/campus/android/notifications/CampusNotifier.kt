package com.campus.android.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.campus.android.MainActivity
import com.campus.android.R
import com.campus.android.core.labels.DisplayLabels
import com.campus.android.core.system.AppSystemSettings
import com.campus.android.data.model.EventInfo

class CampusNotifier(
    private val context: Context
) {
    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val realtime = NotificationChannel(
            CHANNEL_REALTIME,
            context.getString(R.string.notification_channel_realtime),
            NotificationManager.IMPORTANCE_HIGH
        )
        val sync = NotificationChannel(
            CHANNEL_SYNC,
            context.getString(R.string.notification_channel_sync),
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(realtime)
        manager.createNotificationChannel(sync)
    }

    fun foregroundNotification(): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SYNC)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("校园防护运行中")
            .setContentText("正在保持风险事件接收能力")
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    @SuppressLint("MissingPermission")
    fun showEventNotification(event: EventInfo) {
        if (!canPostNotifications()) return
        val title = if (event.readStatus == "UNREAD") "新的风险事件" else "事件更新"
        val device = event.deviceId ?: "未知设备"
        val info = DisplayLabels.eventSummary(event.alarmInfo, event.eventType)
        val notification = NotificationCompat.Builder(context, CHANNEL_REALTIME)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("$device · $info")
            .setContentIntent(openAppIntent(event))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching {
            NotificationManagerCompat.from(context)
                .notify(event.eventId.hashCode(), notification)
        }
    }

    private fun canPostNotifications(): Boolean {
        return AppSystemSettings.canPostNotifications(context)
    }

    private fun openAppIntent(event: EventInfo? = null): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            event?.eventId?.let { putExtra(MainActivity.EXTRA_EVENT_ID, it) }
        }
        return PendingIntent.getActivity(
            context,
            event?.eventId?.hashCode() ?: OPEN_APP_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_REALTIME = "campus_realtime"
        const val CHANNEL_SYNC = "campus_sync"
        const val FOREGROUND_NOTIFICATION_ID = 1314520
        private const val OPEN_APP_REQUEST_CODE = 520
    }
}
