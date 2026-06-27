package com.campus.android

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.campus.android.background.EventSyncWorker
import com.campus.android.core.network.NetworkModule
import com.campus.android.core.storage.SecureTokenStore
import com.campus.android.data.repository.CampusRepository
import com.campus.android.notifications.CampusNotifier
import com.campus.android.realtime.CampusRealtimeClient
import java.util.concurrent.TimeUnit

class CampusApplication : Application() {

    lateinit var tokenStore: SecureTokenStore
        private set
    lateinit var repository: CampusRepository
        private set
    lateinit var realtimeClient: CampusRealtimeClient
        private set
    lateinit var notifier: CampusNotifier
        private set

    override fun onCreate() {
        super.onCreate()
        tokenStore = SecureTokenStore(this)
        notifier = CampusNotifier(this)
        notifier.ensureChannels()

        val api = NetworkModule.createApi(tokenStore)
        repository = CampusRepository(api, tokenStore)
        realtimeClient = CampusRealtimeClient(
            client = NetworkModule.createWebSocketClient(),
            tokenStore = tokenStore,
            notifier = notifier,
            refreshAccessToken = { repository.refreshSession() },
            pullMissedEvents = { repository.unpulledEvents() }
        )
        scheduleEventSync()
    }

    private fun scheduleEventSync() {
        val request = PeriodicWorkRequestBuilder<EventSyncWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            EventSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }
}
