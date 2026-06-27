package com.campus.android.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.campus.android.CampusApplication
import com.campus.android.core.security.SensitiveLog

class EventSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as CampusApplication
        if (!app.tokenStore.isLoggedIn()) return Result.success()
        return runCatching {
            val events = app.repository.unpulledEvents()
            events.forEach(app.notifier::showEventNotification)
            Result.success()
        }.getOrElse {
            SensitiveLog.w(TAG, "Background event sync failed", it)
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "campus_event_sync"
        private const val TAG = "EventSyncWorker"
    }
}
