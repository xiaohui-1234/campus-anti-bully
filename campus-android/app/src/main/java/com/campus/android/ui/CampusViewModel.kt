package com.campus.android.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campus.android.core.network.CampusApiException
import com.campus.android.data.model.DeviceInfo
import com.campus.android.data.model.EventInfo
import com.campus.android.data.model.UserInfo
import com.campus.android.data.repository.CampusRepository
import com.campus.android.realtime.CampusRealtimeClient
import com.campus.android.realtime.RealtimeStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CampusViewModel(
    private val repository: CampusRepository,
    private val realtimeClient: CampusRealtimeClient
) : ViewModel() {

    private companion object {
        const val DEVICE_PAGE_SIZE = 50L
        const val EVENT_PAGE_SIZE = 20L
        const val EVENT_SEARCH_DEBOUNCE_MS = 450L
    }

    private var pendingNotificationEventId: String? = null
    private var eventSearchJob: Job? = null
    private var audioRequestToken = 0

    var state by mutableStateOf(CampusUiState(isLoggedIn = repository.isLoggedIn()))
        private set

    init {
        observeRealtime()
        if (state.isLoggedIn) {
            refreshAll()
        }
    }

    fun selectTab(tab: CampusTab) {
        state = state.copy(selectedTab = tab)
    }

    fun login(loginId: String, password: String) {
        if (loginId.isBlank() || password.isBlank()) {
            state = state.copy(message = "请输入账号和密码")
            return
        }
        runLoading {
            repository.login(loginId.trim(), password)
            state = state.copy(isLoggedIn = true, message = null)
            loadDashboard()
        }
    }

    fun register(securityEmail: String, verifyCode: String, password: String) {
        if (securityEmail.isBlank() || verifyCode.isBlank() || password.isBlank()) {
            state = state.copy(message = "请填写邮箱、验证码和密码")
            return
        }
        runLoading {
            repository.register(securityEmail.trim(), verifyCode.trim(), password)
            state = state.copy(isLoggedIn = true, message = "注册成功")
            loadDashboard()
        }
    }

    fun sendRegisterCode(securityEmail: String) {
        if (securityEmail.isBlank()) {
            state = state.copy(message = "请先输入安全邮箱")
            return
        }
        runLoading {
            repository.sendEmailCode("PASSWORD_REGISTER", securityEmail.trim())
            state = state.copy(message = "验证码已发送")
        }
    }

    fun sendResetCode(securityEmail: String) {
        if (securityEmail.isBlank()) {
            state = state.copy(message = "请先输入安全邮箱")
            return
        }
        runLoading {
            repository.sendEmailCode("RESET_PASSWORD", securityEmail.trim())
            state = state.copy(message = "验证码已发送")
        }
    }

    fun resetPassword(securityEmail: String, verifyCode: String, newPassword: String) {
        if (securityEmail.isBlank() || verifyCode.isBlank() || newPassword.isBlank()) {
            state = state.copy(message = "请填写邮箱、验证码和新密码")
            return
        }
        runLoading {
            repository.resetPassword(securityEmail.trim(), verifyCode.trim(), newPassword)
            state = state.copy(authMode = AuthMode.Login, message = "密码已重置，请重新登录")
        }
    }

    fun setAuthMode(mode: AuthMode) {
        state = state.copy(authMode = mode, message = null)
    }

    fun showSecurityCenter() {
        state = state.copy(
            showSecurityCenter = true,
            securityMode = SecurityMode.Email,
            securityChangeTicket = null,
            securityOldEmailVerified = false,
            message = null
        )
    }

    fun hideSecurityCenter() {
        state = state.copy(showSecurityCenter = false)
    }

    fun setSecurityMode(mode: SecurityMode) {
        state = state.copy(securityMode = mode, message = null)
    }

    fun sendOldSecurityEmailCode() {
        if (state.user?.securityEmailVerified != true) {
            state = state.copy(message = "当前账号未绑定安全邮箱")
            return
        }
        runLoading {
            repository.sendEmailCode("CHANGE_SECURITY_EMAIL_OLD")
            state = state.copy(message = "验证码已发送")
        }
    }

    fun verifyOldSecurityEmail(verifyCode: String) {
        val code = verifyCode.trim()
        if (code.isBlank()) {
            state = state.copy(message = "请输入验证码")
            return
        }
        runLoading {
            val ticket = repository.verifyOldSecurityEmail(code)
            state = state.copy(
                securityChangeTicket = ticket.changeTicket,
                securityOldEmailVerified = true,
                message = "旧安全邮箱已验证"
            )
        }
    }

    fun sendNewSecurityEmailCode(newSecurityEmail: String) {
        val email = newSecurityEmail.trim()
        val ticket = state.securityChangeTicket
        if (!validateEmail(email)) return
        if (ticket.isNullOrBlank() || !state.securityOldEmailVerified) {
            state = state.copy(message = "请先验证旧安全邮箱")
            return
        }
        runLoading {
            repository.sendEmailCode(
                scene = "CHANGE_SECURITY_EMAIL_NEW",
                securityEmail = email,
                changeTicket = ticket
            )
            state = state.copy(message = "验证码已发送")
        }
    }

    fun confirmSecurityEmailChange(newSecurityEmail: String, newVerifyCode: String) {
        val email = newSecurityEmail.trim()
        val code = newVerifyCode.trim()
        val ticket = state.securityChangeTicket
        if (!validateEmail(email)) return
        if (code.isBlank()) {
            state = state.copy(message = "请输入验证码")
            return
        }
        if (ticket.isNullOrBlank() || !state.securityOldEmailVerified) {
            state = state.copy(message = "请先验证旧安全邮箱")
            return
        }
        runLoading {
            repository.changeSecurityEmail(ticket, email, code)
            val me = repository.me()
            state = state.copy(
                user = me,
                securityChangeTicket = null,
                securityOldEmailVerified = false,
                message = "安全邮箱已更新"
            )
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        if (oldPassword.isBlank()) {
            state = state.copy(message = "请输入原密码")
            return
        }
        if (!validatePasswordPair(newPassword, confirmPassword)) return
        if (oldPassword == newPassword) {
            state = state.copy(message = "新密码不能与原密码相同")
            return
        }
        runLoading {
            repository.changePassword(oldPassword, newPassword)
            endSession("密码已修改，请重新登录")
        }
    }

    fun sendCurrentResetCode(securityEmail: String) {
        val email = securityEmail.trim()
        if (!validateEmail(email)) return
        runLoading {
            repository.sendEmailCode("RESET_PASSWORD", email)
            state = state.copy(message = "验证码已发送")
        }
    }

    fun resetCurrentPassword(securityEmail: String, verifyCode: String, newPassword: String, confirmPassword: String) {
        val email = securityEmail.trim()
        val code = verifyCode.trim()
        if (!validateEmail(email)) return
        if (code.isBlank()) {
            state = state.copy(message = "请输入验证码")
            return
        }
        if (!validatePasswordPair(newPassword, confirmPassword)) return
        runLoading {
            repository.resetPassword(email, code, newPassword)
            endSession("密码已重置，请重新登录")
        }
    }

    fun logout() {
        runLoading {
            realtimeClient.close()
            repository.logout()
            state = CampusUiState(isLoggedIn = false)
        }
    }

    fun showProfileEdit() {
        state = state.copy(showProfileEdit = true)
    }

    fun hideProfileEdit() {
        state = state.copy(showProfileEdit = false)
    }

    fun saveProfile(nickname: String, phone: String, email: String) {
        val nextNickname = nickname.trim()
        val nextPhone = phone.trim()
        val nextEmail = email.trim()
        if (nextNickname.isBlank()) {
            state = state.copy(message = "昵称不能为空")
            return
        }
        if (nextPhone.isNotBlank() && !Regex("^1\\d{10}$").matches(nextPhone)) {
            state = state.copy(message = "请输入正确的手机号")
            return
        }
        if (nextEmail.isNotBlank() && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(nextEmail)) {
            state = state.copy(message = "请输入正确的邮箱")
            return
        }
        runLoading {
            val updated = repository.updateMe(
                nickname = nextNickname,
                phone = nextPhone.takeIf { it.isNotBlank() },
                email = nextEmail.takeIf { it.isNotBlank() }
            )
            state = state.copy(
                user = updated,
                showProfileEdit = false,
                message = "资料已保存"
            )
        }
    }

    fun refreshAll() {
        runLoading {
            loadDashboard()
        }
    }

    fun previousEventPage() {
        changeEventPage(state.eventPage - 1)
    }

    fun nextEventPage() {
        changeEventPage(state.eventPage + 1)
    }

    fun changeEventPage(page: Long) {
        if (state.isLoading) return
        val targetPage = page.coerceIn(1, state.eventPageTotal)
        if (targetPage == state.eventPage) return
        runLoading {
            loadEventsPage(targetPage)
        }
    }

    fun markRead(event: EventInfo) {
        runLoading {
            repository.markEventRead(event.eventId)
            val nextEvents = state.events.map {
                if (it.eventId == event.eventId) it.copy(readStatus = "READ") else it
            }
            val nextLatestEvents = state.latestEvents.map {
                if (it.eventId == event.eventId) it.copy(readStatus = "READ") else it
            }
            state = state.copy(
                events = nextEvents,
                latestEvents = nextLatestEvents,
                unreadCount = if (event.readStatus == "UNREAD") {
                    (state.unreadCount - 1).coerceAtLeast(0)
                } else {
                    state.unreadCount
                },
                selectedEvent = state.selectedEvent?.let {
                    if (it.eventId == event.eventId) it.copy(readStatus = "READ") else it
                }
            )
        }
    }

    fun setEventFilter(filter: EventFilter) {
        state = state.copy(eventFilter = filter)
        runLoading {
            loadEventsPage()
        }
    }

    fun setEventKeyword(keyword: String) {
        state = state.copy(eventKeyword = keyword)
        eventSearchJob?.cancel()
        eventSearchJob = viewModelScope.launch {
            delay(EVENT_SEARCH_DEBOUNCE_MS)
            if (!state.isLoggedIn) return@launch
            runLoading {
                loadEventsPage()
            }
        }
    }

    fun openEventDetail(event: EventInfo) {
        state = state.copy(selectedEvent = event)
    }

    fun openEventFromNotification(eventId: String) {
        val targetEventId = eventId.trim().takeIf { it.isNotBlank() } ?: return
        pendingNotificationEventId = targetEventId
        if (!state.isLoggedIn) {
            state = state.copy(message = "请先登录查看通知事件")
            return
        }
        runLoading {
            openNotificationEvent(targetEventId)
            pendingNotificationEventId = null
        }
    }

    fun closeEventDetail() {
        state = state.copy(selectedEvent = null)
    }

    fun deleteEvent(event: EventInfo) {
        state = state.copy(pendingDeleteEvent = event)
    }

    fun cancelDeleteEvent() {
        state = state.copy(pendingDeleteEvent = null)
    }

    fun confirmDeleteEvent() {
        val event = state.pendingDeleteEvent ?: return
        runLoading {
            repository.deleteEvent(event.eventId)
            val targetPage = state.eventPage.coerceAtMost(pageTotal((state.eventTotal - 1).coerceAtLeast(0), EVENT_PAGE_SIZE))
            val page = fetchEvents(targetPage)
            val nextLatestEvents = state.latestEvents.filterNot { it.eventId == event.eventId }
            state = state.copy(
                events = page.records,
                latestEvents = nextLatestEvents,
                eventPage = page.page,
                eventTotal = page.total,
                eventPageTotal = pageTotal(page.total, page.size),
                unreadCount = if (event.readStatus == "UNREAD") {
                    (state.unreadCount - 1).coerceAtLeast(0)
                } else {
                    state.unreadCount
                },
                pendingDeleteEvent = null,
                selectedEvent = null
            )
        }
    }

    fun showBindDevice() {
        state = state.copy(showBindDevice = true)
    }

    fun setDeviceKeyword(keyword: String) {
        state = state.copy(deviceKeyword = keyword)
    }

    fun hideBindDevice() {
        state = state.copy(showBindDevice = false)
    }

    fun bindDevice(deviceId: String, bindCode: String, deviceName: String?) {
        if (deviceId.isBlank() || bindCode.isBlank()) {
            state = state.copy(message = "请填写设备 ID 和绑定码")
            return
        }
        runLoading {
            repository.bindDevice(deviceId.trim(), bindCode.trim(), deviceName?.trim()?.takeIf { it.isNotBlank() })
            state = state.copy(showBindDevice = false, message = "绑定成功")
            loadDashboard()
        }
    }

    fun openDeviceDetail(device: DeviceInfo) {
        state = state.copy(selectedDevice = device)
    }

    fun closeDeviceDetail() {
        state = state.copy(selectedDevice = null)
    }

    fun saveDeviceInfo(device: DeviceInfo, deviceName: String?, location: String?, note: String?) {
        runLoading {
            val updated = repository.updateDevice(
                deviceId = device.deviceId,
                deviceName = deviceName?.trim()?.takeIf { it.isNotBlank() },
                location = location?.trim()?.takeIf { it.isNotBlank() },
                note = note?.trim()?.takeIf { it.isNotBlank() }
            )
            val nextDevices = state.devices.map {
                if (it.deviceId == updated.deviceId) updated else it
            }
            state = state.copy(
                devices = nextDevices,
                selectedDevice = null,
                message = "设备信息已保存"
            )
            realtimeClient.subscribeEvents(nextDevices.map { it.deviceId })
        }
    }

    fun requestUnbindDevice(device: DeviceInfo) {
        state = state.copy(pendingUnbindDevice = device)
    }

    fun cancelUnbindDevice() {
        state = state.copy(pendingUnbindDevice = null)
    }

    fun confirmUnbindDevice() {
        val device = state.pendingUnbindDevice ?: return
        runLoading {
            repository.unbindDevice(device.deviceId)
            state = state.copy(
                selectedDevice = null,
                pendingUnbindDevice = null,
                message = "设备已解绑"
            )
            loadDashboard()
        }
    }

    fun prepareAudio(event: EventInfo) {
        if (event.fileStatus != "SUCCESS") {
            state = state.copy(message = "音频暂不可播放")
            return
        }
        val requestToken = ++audioRequestToken
        state = state.copy(
            audioEvent = null,
            audioUrl = null,
            audioLoadingEventId = event.eventId,
            message = null
        )
        viewModelScope.launch {
            state = state.copy(isLoading = true)
            runCatching {
                repository.refreshEventUrl(event.eventId)
            }.onSuccess { audio ->
                if (requestToken != audioRequestToken) return@onSuccess
                val audioUrl = audio.fileUrl.trim()
                state = if (audioUrl.isBlank()) {
                    state.copy(message = "录音地址无效，请稍后重试")
                } else if (!audioUrl.startsWith("https://", ignoreCase = true)) {
                    state.copy(message = "后端返回了非 HTTPS 录音地址，请检查 MinIO public-endpoint 配置")
                } else {
                    state.copy(audioEvent = event, audioUrl = audioUrl)
                }
            }.onFailure { error ->
                if (requestToken != audioRequestToken) return@onFailure
                if (shouldEndSession(error)) {
                    endSession(error.message ?: "登录状态已失效，请重新登录")
                } else {
                    state = state.copy(message = error.message ?: "音频地址获取失败")
                }
            }
            if (requestToken == audioRequestToken) {
                state = state.copy(isLoading = false, audioLoadingEventId = null)
            }
        }
    }

    fun closeAudio() {
        audioRequestToken += 1
        state = state.copy(audioEvent = null, audioUrl = null, audioLoadingEventId = null)
    }

    fun clearMessage() {
        state = state.copy(message = null)
    }

    private fun observeRealtime() {
        viewModelScope.launch {
            realtimeClient.status.collect { status ->
                state = state.copy(realtimeStatus = status)
            }
        }
        viewModelScope.launch {
            realtimeClient.events.collect { event ->
                val exists = state.events.any { it.eventId == event.eventId }
                val known = state.latestEvents.firstOrNull { it.eventId == event.eventId }
                    ?: state.events.firstOrNull { it.eventId == event.eventId }
                val unreadDelta = unreadDelta(known, event)
                val nextLatestEvents = state.latestEvents.mergeWith(listOf(event)).take(EVENT_PAGE_SIZE.toInt())
                val nextEvents = when {
                    exists -> state.events.map { if (it.eventId == event.eventId) event else it }
                    state.eventPage == 1L -> (listOf(event) + state.events).distinctBy { it.eventId }.take(EVENT_PAGE_SIZE.toInt())
                    else -> state.events
                }
                val nextTotal = if (exists) state.eventTotal else state.eventTotal + 1
                state = state.copy(
                    events = nextEvents,
                    latestEvents = nextLatestEvents,
                    eventTotal = nextTotal,
                    eventPageTotal = pageTotal(nextTotal, EVENT_PAGE_SIZE),
                    unreadCount = (state.unreadCount + unreadDelta).coerceAtLeast(0)
                )
            }
        }
        viewModelScope.launch {
            realtimeClient.deviceStatuses.collect { deviceStatus ->
                val nextDevices = state.devices.map { device ->
                    if (device.deviceId == deviceStatus.deviceId) {
                        device.copy(
                            onlineStatus = deviceStatus.onlineStatus ?: device.onlineStatus,
                            lastOnlineTime = deviceStatus.lastOnlineTime ?: device.lastOnlineTime
                        )
                    } else {
                        device
                    }
                }
                state = state.copy(devices = nextDevices)
            }
        }
    }

    private suspend fun loadDashboard() {
        val me = repository.me()
        val devices = repository.devices(size = DEVICE_PAGE_SIZE).records
        val eventPage = fetchEvents(page = 1)
        val unreadTotal = repository.unreadEvents().total
        state = state.copy(
            user = me,
            devices = devices,
            latestEvents = eventPage.records,
            events = eventPage.records,
            eventPage = eventPage.page,
            eventTotal = eventPage.total,
            eventPageTotal = pageTotal(eventPage.total, eventPage.size),
            unreadCount = unreadTotal.toInt().coerceAtLeast(0),
            message = null
        )
        realtimeClient.subscribeEvents(devices.map { it.deviceId })
        pendingNotificationEventId?.let { eventId ->
            openNotificationEvent(eventId)
            pendingNotificationEventId = null
        }
    }

    private suspend fun openNotificationEvent(eventId: String) {
        val detail = repository.eventDetail(eventId)
        val nextEvents = if (state.eventPage == 1L) {
            state.events.mergeWith(listOf(detail)).take(EVENT_PAGE_SIZE.toInt())
        } else {
            state.events
        }
        val nextLatestEvents = state.latestEvents.mergeWith(listOf(detail)).take(EVENT_PAGE_SIZE.toInt())
        state = state.copy(
            selectedTab = CampusTab.Events,
            selectedEvent = detail,
            events = nextEvents,
            latestEvents = nextLatestEvents,
            unreadCount = nextLatestEvents.count { it.readStatus == "UNREAD" },
            message = null
        )
    }

    private suspend fun loadEventsPage(pageNumber: Long = 1) {
        val page = fetchEvents(page = pageNumber)
        state = state.copy(
            events = page.records,
            eventPage = page.page,
            eventTotal = page.total,
            eventPageTotal = pageTotal(page.total, page.size)
        )
    }

    private suspend fun fetchEvents(page: Long) = repository.events(
        page = page,
        size = EVENT_PAGE_SIZE,
        eventType = state.eventFilter.eventType(),
        fileStatus = state.eventFilter.fileStatus(),
        readStatus = state.eventFilter.readStatus(),
        keyword = state.eventKeyword.trim().takeIf { it.isNotBlank() }
    )

    private fun List<EventInfo>.mergeWith(incoming: List<EventInfo>): List<EventInfo> {
        if (incoming.isEmpty()) return this
        val merged = toMutableList()
        incoming.forEach { event ->
            val index = merged.indexOfFirst { it.eventId == event.eventId }
            if (index >= 0) {
                merged[index] = event
            } else {
                merged.add(0, event)
            }
        }
        return merged
    }

    private fun pageTotal(total: Long, size: Long): Long {
        val pageSize = size.coerceAtLeast(1)
        return maxOf(1, (total + pageSize - 1) / pageSize)
    }

    private fun unreadDelta(previous: EventInfo?, current: EventInfo): Int {
        val wasUnread = previous?.readStatus == "UNREAD"
        val isUnread = current.readStatus == "UNREAD"
        return when {
            !wasUnread && isUnread -> 1
            wasUnread && !isUnread -> -1
            else -> 0
        }
    }

    private fun runLoading(block: suspend () -> Unit) {
        viewModelScope.launch {
            state = state.copy(isLoading = true)
            runCatching { block() }
                .onFailure { error ->
                    if (shouldEndSession(error)) {
                        endSession(error.message ?: "登录状态已失效，请重新登录")
                    } else {
                        state = state.copy(message = error.message ?: "操作失败")
                    }
                }
            state = state.copy(isLoading = false)
        }
    }

    private fun shouldEndSession(error: Throwable): Boolean {
        return state.isLoggedIn && error is CampusApiException && error.code == 401
    }

    private suspend fun endSession(message: String) {
        realtimeClient.close()
        repository.clearLocalSession()
        state = CampusUiState(isLoggedIn = false, message = message)
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isBlank()) {
            state = state.copy(message = "请输入安全邮箱")
            return false
        }
        if (!Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email)) {
            state = state.copy(message = "请输入正确的邮箱")
            return false
        }
        return true
    }

    private fun validatePasswordPair(password: String, confirmPassword: String): Boolean {
        if (password.isBlank()) {
            state = state.copy(message = "请输入密码")
            return false
        }
        if (password.length !in 8..64) {
            state = state.copy(message = "密码长度需为8到64位")
            return false
        }
        if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            state = state.copy(message = "密码需包含字母和数字")
            return false
        }
        if (password != confirmPassword) {
            state = state.copy(message = "两次输入的密码不一致")
            return false
        }
        return true
    }
}

data class CampusUiState(
    val isLoggedIn: Boolean = false,
    val isLoading: Boolean = false,
    val selectedTab: CampusTab = CampusTab.Home,
    val user: UserInfo? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val latestEvents: List<EventInfo> = emptyList(),
    val events: List<EventInfo> = emptyList(),
    val eventPage: Long = 1,
    val eventTotal: Long = 0,
    val eventPageTotal: Long = 1,
    val unreadCount: Int = 0,
    val realtimeStatus: RealtimeStatus = RealtimeStatus.OFFLINE,
    val eventFilter: EventFilter = EventFilter.All,
    val eventKeyword: String = "",
    val deviceKeyword: String = "",
    val authMode: AuthMode = AuthMode.Login,
    val showProfileEdit: Boolean = false,
    val showSecurityCenter: Boolean = false,
    val securityMode: SecurityMode = SecurityMode.Email,
    val securityOldEmailVerified: Boolean = false,
    val securityChangeTicket: String? = null,
    val showBindDevice: Boolean = false,
    val selectedDevice: DeviceInfo? = null,
    val pendingUnbindDevice: DeviceInfo? = null,
    val selectedEvent: EventInfo? = null,
    val pendingDeleteEvent: EventInfo? = null,
    val audioEvent: EventInfo? = null,
    val audioUrl: String? = null,
    val audioLoadingEventId: String? = null,
    val message: String? = null
)

enum class AuthMode {
    Login,
    Register,
    ResetPassword
}

enum class SecurityMode {
    Email,
    Password,
    Reset
}

enum class EventFilter(val label: String) {
    All("全部"),
    Unread("未读"),
    Sos("紧急"),
    Help("求助"),
    Fight("打架"),
    Voice("声音"),
    Button("按钮"),
    Playable("可播放"),
    Uploading("上传中"),
    Failed("失败")
}

enum class CampusTab(val title: String) {
    Home("首页"),
    Devices("设备"),
    Events("事件"),
    Status("状态"),
    Profile("我的")
}

private fun EventFilter.eventType(): String? = when (this) {
    EventFilter.Sos -> "SOS"
    EventFilter.Help -> "HELP"
    EventFilter.Fight -> "FIGHT"
    EventFilter.Voice -> "VOICE"
    EventFilter.Button -> "BUTTON"
    else -> null
}

private fun EventFilter.fileStatus(): String? = when (this) {
    EventFilter.Playable -> "SUCCESS"
    EventFilter.Uploading -> "UPLOADING"
    EventFilter.Failed -> "FAILED"
    else -> null
}

private fun EventFilter.readStatus(): String? = when (this) {
    EventFilter.Unread -> "UNREAD"
    else -> null
}

class CampusViewModelFactory(
    private val repository: CampusRepository,
    private val realtimeClient: CampusRealtimeClient
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CampusViewModel(repository, realtimeClient) as T
    }
}
