package com.campus.android.realtime

import com.campus.android.core.config.ApiConfig
import com.campus.android.core.security.SensitiveLog
import com.campus.android.core.storage.SecureTokenStore
import com.campus.android.data.model.EventInfo
import com.campus.android.notifications.CampusNotifier
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicInteger

enum class RealtimeStatus {
    OFFLINE,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

data class DeviceRealtimeStatus(
    @SerializedName("device_id")
    val deviceId: String,
    @SerializedName("online_status")
    val onlineStatus: String?,
    @SerializedName("last_online_time")
    val lastOnlineTime: String?
)

class CampusRealtimeClient(
    private val client: OkHttpClient,
    private val tokenStore: SecureTokenStore,
    private val notifier: CampusNotifier,
    private val refreshAccessToken: suspend () -> Boolean = { false },
    private val pullMissedEvents: suspend () -> List<EventInfo> = { emptyList() }
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val lock = Any()
    private val reconnectCount = AtomicInteger(0)

    private var webSocket: WebSocket? = null
    private var manualClose = false
    private var reconnectJob: Job? = null
    private var heartbeatJob: Job? = null
    private var heartbeatTimeoutJob: Job? = null
    private var subscribedDeviceIds: List<String> = emptyList()

    private val _status = MutableStateFlow(RealtimeStatus.OFFLINE)
    val status: StateFlow<RealtimeStatus> = _status

    private val _events = MutableSharedFlow<EventInfo>(extraBufferCapacity = 64)
    val events: SharedFlow<EventInfo> = _events

    private val _deviceStatuses = MutableSharedFlow<DeviceRealtimeStatus>(extraBufferCapacity = 64)
    val deviceStatuses: SharedFlow<DeviceRealtimeStatus> = _deviceStatuses

    fun connect() {
        val token = tokenStore.accessToken()
        if (token.isNullOrBlank()) {
            setStatus(RealtimeStatus.OFFLINE)
            return
        }
        synchronized(lock) {
            if (webSocket != null) return
            manualClose = false
            setStatus(RealtimeStatus.CONNECTING)
            val request = Request.Builder()
                .url(ApiConfig.WS_URL)
                .header("Authorization", "Bearer $token")
                .build()
            webSocket = client.newWebSocket(request, SocketListener())
        }
    }

    fun subscribeEvents(deviceIds: List<String>) {
        val nextDeviceIds = deviceIds
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        val shouldSend = synchronized(lock) {
            val changed = subscribedDeviceIds.toSet() != nextDeviceIds.toSet()
            subscribedDeviceIds = nextDeviceIds
            changed && webSocket != null && status.value == RealtimeStatus.CONNECTED
        }
        if (shouldSend) sendSubscription()
        connect()
    }

    fun close() {
        val socket = synchronized(lock) {
            manualClose = true
            reconnectJob?.cancel()
            heartbeatJob?.cancel()
            heartbeatTimeoutJob?.cancel()
            reconnectJob = null
            heartbeatJob = null
            heartbeatTimeoutJob = null
            subscribedDeviceIds = emptyList()
            webSocket.also { webSocket = null }
        }
        socket?.close(NORMAL_CLOSE, "client closed")
        setStatus(RealtimeStatus.OFFLINE)
    }

    private fun sendSubscription() {
        val ids = synchronized(lock) { subscribedDeviceIds }
        send(
            mapOf(
                "type" to "SUBSCRIBE_EVENTS",
                "device_ids" to ids
            )
        )
    }

    private fun sendPing() {
        send(
            mapOf(
                "type" to "PING",
                "timestamp" to System.currentTimeMillis()
            )
        )
        heartbeatTimeoutJob?.cancel()
        heartbeatTimeoutJob = scope.launch {
            delay(HEARTBEAT_TIMEOUT_MS)
            handleSocketLost()
        }
    }

    private fun send(payload: Any) {
        val socket = synchronized(lock) { webSocket } ?: return
        socket.send(gson.toJson(payload))
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatTimeoutJob?.cancel()
        heartbeatJob = scope.launch {
            sendPing()
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                sendPing()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatTimeoutJob?.cancel()
        heartbeatJob = null
        heartbeatTimeoutJob = null
    }

    private fun handleSocketLost() {
        val socket = synchronized(lock) {
            webSocket.also { webSocket = null }
        }
        socket?.cancel()
        stopHeartbeat()
        if (!manualClose) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        synchronized(lock) {
            if (manualClose || reconnectJob?.isActive == true) return
            setStatus(RealtimeStatus.RECONNECTING)
            val delayMs = minOf(MAX_RECONNECT_DELAY_MS, 1000L * (1L shl reconnectCount.getAndIncrement().coerceAtMost(5)))
            reconnectJob = scope.launch {
                delay(delayMs)
                val refreshed = runCatching { refreshAccessToken() }.getOrElse {
                    SensitiveLog.w(TAG, "Realtime token refresh failed", it)
                    false
                }
                if (!refreshed && tokenStore.accessToken().isNullOrBlank()) {
                    setStatus(RealtimeStatus.OFFLINE)
                    return@launch
                }
                if (!refreshed && tokenStore.isAccessTokenExpired()) {
                    setStatus(RealtimeStatus.OFFLINE)
                    return@launch
                }
                synchronized(lock) { reconnectJob = null }
                connect()
            }
        }
    }

    private fun handleTextMessage(text: String) {
        val body = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        when (body.stringValue("type")) {
            "PONG" -> {
                heartbeatTimeoutJob?.cancel()
                heartbeatTimeoutJob = null
            }
            "NEW_EVENT" -> parseData<EventInfo>(body)?.let { event ->
                _events.tryEmit(event)
                notifier.showEventNotification(event)
            }
            "DEVICE_STATUS" -> parseData<DeviceRealtimeStatus>(body)?.let { status ->
                _deviceStatuses.tryEmit(status)
            }
            "SUBSCRIBE_EVENTS_ACK" -> {
                if (body.booleanValue("success") == false) {
                    SensitiveLog.w(TAG, "Realtime subscription rejected")
                }
            }
        }
    }

    private inline fun <reified T> parseData(body: JsonObject): T? {
        val data = body.get("data") ?: return null
        return runCatching { gson.fromJson(data, T::class.java) }.getOrNull()
    }

    private fun setStatus(status: RealtimeStatus) {
        _status.value = status
    }

    private fun pullMissedEventsSafely() {
        scope.launch {
            runCatching { pullMissedEvents() }
                .onSuccess { events ->
                    events.forEach { event ->
                        _events.tryEmit(event)
                        notifier.showEventNotification(event)
                    }
                }
                .onFailure { SensitiveLog.w(TAG, "Realtime missed event pull failed", it) }
        }
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            synchronized(lock) {
                if (this@CampusRealtimeClient.webSocket != webSocket || manualClose) {
                    webSocket.close(NORMAL_CLOSE, "stale socket")
                    return
                }
                reconnectCount.set(0)
            }
            setStatus(RealtimeStatus.CONNECTED)
            startHeartbeat()
            if (subscribedDeviceIds.isNotEmpty()) sendSubscription()
            pullMissedEventsSafely()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleTextMessage(text)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onSocketEnded(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            SensitiveLog.w(TAG, "Realtime socket failed", t)
            onSocketEnded(webSocket)
        }

        private fun onSocketEnded(socket: WebSocket) {
            val shouldReconnect = synchronized(lock) {
                if (webSocket != socket) return
                webSocket = null
                !manualClose
            }
            stopHeartbeat()
            if (shouldReconnect) {
                scheduleReconnect()
            } else {
                setStatus(RealtimeStatus.OFFLINE)
            }
        }
    }

    private companion object {
        const val TAG = "CampusRealtime"
        const val NORMAL_CLOSE = 1000
        const val HEARTBEAT_INTERVAL_MS = 25_000L
        const val HEARTBEAT_TIMEOUT_MS = 10_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
    }
}

private fun JsonObject.stringValue(name: String): String? {
    return get(name)?.takeIf { !it.isJsonNull }?.asString
}

private fun JsonObject.booleanValue(name: String): Boolean? {
    return get(name)?.takeIf { !it.isJsonNull }?.asBoolean
}
