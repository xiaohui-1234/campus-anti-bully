package com.campus.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.campus.android.background.RealtimeForegroundService
import com.campus.android.core.labels.DisplayLabels
import com.campus.android.core.system.AppSystemSettings
import com.campus.android.data.model.DeviceInfo
import com.campus.android.data.model.EventInfo
import com.campus.android.data.model.UserInfo
import com.campus.android.realtime.RealtimeStatus
import com.campus.android.ui.theme.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable
fun CampusApp(viewModel: CampusViewModel) {
    val state = viewModel.state
    val context = LocalContext.current

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) {
            RealtimeForegroundService.start(context)
        } else {
            RealtimeForegroundService.stop(context)
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) { Text("知道了") }
            },
            title = { Text("提示") },
            text = { Text(message) }
        )
    }
    state.pendingDeleteEvent?.let { event ->
        ConfirmDeleteDialog(
            event = event,
            onDismiss = viewModel::cancelDeleteEvent,
            onConfirm = viewModel::confirmDeleteEvent
        )
    }
    state.selectedDevice?.let { device ->
        DeviceDetailDialog(
            device = device,
            onDismiss = viewModel::closeDeviceDetail,
            onSave = viewModel::saveDeviceInfo,
            onUnbind = viewModel::requestUnbindDevice
        )
    }
    state.pendingUnbindDevice?.let { device ->
        ConfirmUnbindDialog(
            device = device,
            onDismiss = viewModel::cancelUnbindDevice,
            onConfirm = viewModel::confirmUnbindDevice
        )
    }
    state.selectedEvent?.let { event ->
        EventDetailDialog(
            event = event,
            isAudioLoading = state.audioLoadingEventId == event.eventId,
            onDismiss = viewModel::closeEventDetail,
            onMarkRead = viewModel::markRead,
            onDelete = viewModel::deleteEvent,
            onPlayAudio = viewModel::prepareAudio
        )
    }
    if (state.showProfileEdit && state.user != null) {
        ProfileEditDialog(
            user = state.user,
            onDismiss = viewModel::hideProfileEdit,
            onSave = viewModel::saveProfile
        )
    }
    if (state.showSecurityCenter) {
        SecurityCenterDialog(
            state = state,
            onDismiss = viewModel::hideSecurityCenter,
            onModeChange = viewModel::setSecurityMode,
            onSendOldEmailCode = viewModel::sendOldSecurityEmailCode,
            onVerifyOldEmail = viewModel::verifyOldSecurityEmail,
            onSendNewEmailCode = viewModel::sendNewSecurityEmailCode,
            onConfirmEmailChange = viewModel::confirmSecurityEmailChange,
            onChangePassword = viewModel::changePassword,
            onSendResetCode = viewModel::sendCurrentResetCode,
            onResetPassword = viewModel::resetCurrentPassword
        )
    }
    if (state.showBindDevice) {
        BindDeviceDialog(
            onDismiss = viewModel::hideBindDevice,
            onBind = viewModel::bindDevice
        )
    }
    if (state.audioUrl != null && state.audioEvent != null) {
        AudioPlayerDialog(
            event = state.audioEvent,
            audioUrl = state.audioUrl,
            onDismiss = viewModel::closeAudio
        )
    }

    if (!state.isLoggedIn) {
        AuthScreen(
            state = state,
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onResetPassword = viewModel::resetPassword,
            onSendRegisterCode = viewModel::sendRegisterCode,
            onSendResetCode = viewModel::sendResetCode,
            onModeChange = viewModel::setAuthMode
        )
        return
    }

    Scaffold(
        containerColor = CampusBackground,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                CampusTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        icon = { Icon(tab.icon(), contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(CampusBackground)
        ) {
            when (state.selectedTab) {
                CampusTab.Home -> HomeScreen(state, viewModel::refreshAll)
                CampusTab.Devices -> DevicesScreen(
                    devices = state.devices,
                    keyword = state.deviceKeyword,
                    onKeywordChange = viewModel::setDeviceKeyword,
                    onRefresh = viewModel::refreshAll,
                    onShowBind = viewModel::showBindDevice,
                    onOpenDetail = viewModel::openDeviceDetail
                )
                CampusTab.Events -> EventsScreen(
                    events = state.events,
                    filter = state.eventFilter,
                    keyword = state.eventKeyword,
                    eventPage = state.eventPage,
                    eventPageTotal = state.eventPageTotal,
                    eventTotal = state.eventTotal,
                    isLoading = state.isLoading,
                    audioLoadingEventId = state.audioLoadingEventId,
                    onFilterChange = viewModel::setEventFilter,
                    onKeywordChange = viewModel::setEventKeyword,
                    onRefresh = viewModel::refreshAll,
                    onOpenDetail = viewModel::openEventDetail,
                    onMarkRead = viewModel::markRead,
                    onDelete = viewModel::deleteEvent,
                    onPlayAudio = viewModel::prepareAudio,
                    onPreviousPage = viewModel::previousEventPage,
                    onNextPage = viewModel::nextEventPage,
                    onPageChange = viewModel::changeEventPage
                )
                CampusTab.Status -> StatusScreen(state, viewModel::refreshAll)
                CampusTab.Profile -> ProfileScreen(
                    state = state,
                    onEdit = viewModel::showProfileEdit,
                    onSecurity = viewModel::showSecurityCenter,
                    onLogout = viewModel::logout
                )
            }
            if (state.isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AuthScreen(
    state: CampusUiState,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String) -> Unit,
    onResetPassword: (String, String, String) -> Unit,
    onSendRegisterCode: (String) -> Unit,
    onSendResetCode: (String) -> Unit,
    onModeChange: (AuthMode) -> Unit
) {
    var loginId by remember { mutableStateOf("") }
    var securityEmail by remember { mutableStateOf("") }
    var verifyCode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CampusBackground)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("校园防护", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(authSubtitle(state.authMode), color = CampusMuted, modifier = Modifier.padding(top = 8.dp, bottom = 18.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AuthMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.authMode == mode,
                    onClick = { onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, AuthMode.entries.size),
                    label = { Text(mode.label()) }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        when (state.authMode) {
            AuthMode.Login -> {
                OutlinedTextField(
                    value = loginId,
                    onValueChange = { loginId = it },
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PasswordField(password, onValueChange = { password = it }, label = "密码")
            }
            AuthMode.Register, AuthMode.ResetPassword -> {
                OutlinedTextField(
                    value = securityEmail,
                    onValueChange = { securityEmail = it },
                    label = { Text("安全邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                if (state.authMode == AuthMode.Register) {
                                    onSendRegisterCode(securityEmail)
                                } else {
                                    onSendResetCode(securityEmail)
                                }
                            }
                        ) {
                            Text("验证码")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = verifyCode,
                    onValueChange = { verifyCode = it },
                    label = { Text("验证码") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PasswordField(password, onValueChange = { password = it }, label = if (state.authMode == AuthMode.Register) "密码" else "新密码")
            }
        }
        Button(
            onClick = {
                when (state.authMode) {
                    AuthMode.Login -> onLogin(loginId, password)
                    AuthMode.Register -> onRegister(securityEmail, verifyCode, password)
                    AuthMode.ResetPassword -> onResetPassword(securityEmail, verifyCode, password)
                }
            },
            enabled = !state.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(if (state.authMode == AuthMode.Login) Icons.AutoMirrored.Rounded.Login else Icons.Rounded.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(state.authMode.primaryAction())
        }
    }
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun HomeScreen(state: CampusUiState, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SummaryCard(state, onRefresh)
        }
        item {
            SectionTitle("最新事件")
        }
        items(state.latestEvents.take(5), key = { it.eventId }) { event ->
            EventCard(event, compact = true)
        }
        if (state.latestEvents.isEmpty()) {
            item { EmptyCard("暂无风险事件") }
        }
    }
}

@Composable
private fun DevicesScreen(
    devices: List<DeviceInfo>,
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowBind: () -> Unit,
    onOpenDetail: (DeviceInfo) -> Unit
) {
    val visibleDevices = devices.filterBy(keyword)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    label = { Text("搜索设备") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新设备")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("绑定设备")
                Spacer(Modifier.weight(1f))
                FilledTonalButton(onClick = onShowBind, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("绑定")
                }
            }
        }
        items(visibleDevices, key = { it.deviceId }) { device ->
            DeviceCard(device, onClick = { onOpenDetail(device) })
        }
        if (visibleDevices.isEmpty()) {
            item { EmptyCard("暂无绑定设备") }
        }
    }
}

@Composable
private fun EventsScreen(
    events: List<EventInfo>,
    filter: EventFilter,
    keyword: String,
    eventPage: Long,
    eventPageTotal: Long,
    eventTotal: Long,
    isLoading: Boolean,
    audioLoadingEventId: String?,
    onFilterChange: (EventFilter) -> Unit,
    onKeywordChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenDetail: (EventInfo) -> Unit,
    onMarkRead: (EventInfo) -> Unit,
    onDelete: (EventInfo) -> Unit,
    onPlayAudio: (EventInfo) -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPageChange: (Long) -> Unit
) {
    val visibleEvents = events
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = onKeywordChange,
                    label = { Text("搜索事件") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新事件")
                }
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EventFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { onFilterChange(item) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
        item {
            EventPaginationCard(
                page = eventPage,
                pageTotal = eventPageTotal,
                total = eventTotal,
                isLoading = isLoading,
                initiallyExpanded = false,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                onPageChange = onPageChange
            )
        }
        items(visibleEvents, key = { it.eventId }) { event ->
            val isAudioLoading = audioLoadingEventId == event.eventId
            EventCard(
                event = event,
                compact = false,
                onClick = { onOpenDetail(event) },
                isAudioLoading = isAudioLoading,
                onMarkRead = onMarkRead,
                onDelete = onDelete,
                onPlayAudio = onPlayAudio
            )
        }
        if (visibleEvents.isEmpty()) {
            item { EmptyCard("暂无事件记录") }
        }
        item {
            EventPaginationCard(
                page = eventPage,
                pageTotal = eventPageTotal,
                total = eventTotal,
                isLoading = isLoading,
                initiallyExpanded = true,
                onPreviousPage = onPreviousPage,
                onNextPage = onNextPage,
                onPageChange = onPageChange
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EventPaginationCard(
    page: Long,
    pageTotal: Long,
    total: Long,
    isLoading: Boolean,
    initiallyExpanded: Boolean,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPageChange: (Long) -> Unit
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    SoftCard(containerColor = CampusSoftBlue) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("第 $page / $pageTotal 页", fontWeight = FontWeight.Bold)
                    Text("共 $total 条事件", color = CampusMuted, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "收起分页" else "展开分页"
                    )
                }
            }
            if (expanded) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPreviousPage,
                        enabled = !isLoading && page > 1,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("上一页")
                    }
                    Button(
                        onClick = onNextPage,
                        enabled = !isLoading && page < pageTotal,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("下一页")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    visiblePageItems(page, pageTotal).forEach { item ->
                        if (item == null) {
                            Text("...", color = CampusMuted, modifier = Modifier.padding(horizontal = 4.dp))
                        } else {
                            FilterChip(
                                selected = item == page,
                                enabled = !isLoading,
                                onClick = { onPageChange(item) },
                                label = { Text(item.toString()) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusScreen(state: CampusUiState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val devices = state.devices
    val events = state.events
    var notificationEnabled by remember { mutableStateOf(AppSystemSettings.canPostNotifications(context)) }
    var batteryAllowed by remember { mutableStateOf(AppSystemSettings.isIgnoringBatteryOptimizations(context)) }

    fun refreshSystemState() {
        notificationEnabled = AppSystemSettings.canPostNotifications(context)
        batteryAllowed = AppSystemSettings.isIgnoringBatteryOptimizations(context)
    }

    DisposableEffect(context) {
        val lifecycleOwner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshSystemState()
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        refreshSystemState()
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Sync, contentDescription = null, tint = statusColor(state.realtimeStatus))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("实时连接", fontWeight = FontWeight.Bold)
                        Text(state.realtimeStatus.label(), color = CampusMuted)
                    }
                    Button(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
                        Text("刷新")
                    }
                }
            }
        }
        item {
            SoftCard(containerColor = CampusSoftBlue) {
                MetricGrid(
                    "设备" to devices.size.toString(),
                    "在线" to devices.count { it.onlineStatus == "ONLINE" }.toString(),
                    "未读" to state.unreadCount.toString(),
                    "全部消息" to state.eventTotal.toString()
                )
            }
        }
        item {
            SoftCard {
                Text("后台提醒", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                BackgroundCapabilityRow(
                    icon = Icons.Rounded.Notifications,
                    title = "通知权限",
                    status = if (notificationEnabled) "已允许" else "未允许",
                    statusColor = if (notificationEnabled) CampusSuccess else CampusWarning,
                    action = if (notificationEnabled) "查看" else "设置",
                    onAction = {
                        AppSystemSettings.openNotificationSettings(context)
                        refreshSystemState()
                    }
                )
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = CampusMuted.copy(alpha = 0.18f))
                BackgroundCapabilityRow(
                    icon = Icons.Rounded.BatterySaver,
                    title = "电池优化",
                    status = if (batteryAllowed) "已放行" else "受限制",
                    statusColor = if (batteryAllowed) CampusSuccess else CampusWarning,
                    action = "设置",
                    onAction = {
                        AppSystemSettings.openBatteryOptimizationSettings(context)
                        refreshSystemState()
                    }
                )
            }
        }
        item { SectionTitle("订阅设备 ${devices.size}") }
        items(devices, key = { it.deviceId }) { device ->
            DeviceCard(device)
        }
    }
}

@Composable
private fun ProfileScreen(
    state: CampusUiState,
    onEdit: () -> Unit,
    onSecurity: () -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SoftCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ProfileAvatar(state.user)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            state.user?.nickname ?: "校园用户",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            state.user?.userId ?: "",
                            color = CampusMuted,
                            modifier = Modifier.padding(top = 4.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    "安全邮箱：${state.user?.securityEmailMasked ?: "未绑定"}",
                    color = CampusMuted,
                    modifier = Modifier.padding(top = 14.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "通知邮箱：${state.user?.email ?: "未填写"}",
                    color = CampusMuted,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "手机号：${state.user?.phone ?: "未填写"}",
                    color = CampusMuted,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                FilledTonalButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("编辑资料")
                }
                FilledTonalButton(
                    onClick = onSecurity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("账号安全")
                }
            }
        }
        item {
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CampusDanger),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("退出登录")
            }
        }
    }
}

@Composable
private fun ProfileAvatar(user: UserInfo?) {
    val avatarUrl = user?.avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
    val fallbackText = user?.nickname?.trim()?.firstOrNull()?.toString()
        ?: user?.userId?.trim()?.firstOrNull()?.toString()
        ?: "我"

    Box(
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(CampusSoftBlue)
            .border(1.dp, CampusBlue.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            fallbackText,
            color = CampusBlue,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun BindDeviceDialog(
    onDismiss: () -> Unit,
    onBind: (String, String, String?) -> Unit
) {
    var deviceId by remember { mutableStateOf("") }
    var bindCode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onBind(deviceId, bindCode, deviceName) }) {
                Text("绑定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("绑定设备") },
        text = {
            DialogContentColumn {
                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("设备 ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bindCode,
                    onValueChange = { bindCode = it },
                    label = { Text("绑定码") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("设备名称，可选") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
private fun DeviceDetailDialog(
    device: DeviceInfo,
    onDismiss: () -> Unit,
    onSave: (DeviceInfo, String?, String?, String?) -> Unit,
    onUnbind: (DeviceInfo) -> Unit
) {
    var deviceName by remember(device.deviceId) { mutableStateOf(device.deviceName.orEmpty()) }
    var location by remember(device.deviceId) { mutableStateOf(device.location.orEmpty()) }
    var note by remember(device.deviceId) { mutableStateOf(device.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(device, deviceName, location, note) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = {
            Text(
                device.deviceName ?: device.deviceId,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            DialogContentColumn {
                DetailLine("设备 ID", device.deviceId)
                DetailLine("在线状态", onlineStatusLabel(device.onlineStatus))
                DetailLine("最后在线", device.lastOnlineTime ?: "-")
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("设备名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("位置") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { onUnbind(device) },
                    colors = ButtonDefaults.textButtonColors(contentColor = CampusDanger)
                ) {
                    Icon(Icons.Rounded.LinkOff, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("解绑设备")
                }
            }
        }
    )
}

@Composable
private fun ConfirmUnbindDialog(
    device: DeviceInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = CampusDanger)
            ) {
                Text("确认解绑")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("解绑设备") },
        text = {
            Text("解绑“${device.deviceName ?: device.deviceId}”后，你将不再接收该设备的报警。")
        }
    )
}

@Composable
private fun ConfirmDeleteDialog(
    event: EventInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(CampusDanger.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = CampusDanger,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "删除事件",
                            color = CampusText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "删除后将不再显示在事件列表中。",
                            color = CampusMuted,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Text(
                    "此操作不可撤销，请确认是否继续。",
                    color = CampusMuted,
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("取消")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CampusDanger)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("确认删除")
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SecurityCenterDialog(
    state: CampusUiState,
    onDismiss: () -> Unit,
    onModeChange: (SecurityMode) -> Unit,
    onSendOldEmailCode: () -> Unit,
    onVerifyOldEmail: (String) -> Unit,
    onSendNewEmailCode: (String) -> Unit,
    onConfirmEmailChange: (String, String) -> Unit,
    onChangePassword: (String, String, String) -> Unit,
    onSendResetCode: (String) -> Unit,
    onResetPassword: (String, String, String, String) -> Unit
) {
    var oldEmailCode by remember { mutableStateOf("") }
    var newSecurityEmail by remember { mutableStateOf("") }
    var newEmailCode by remember { mutableStateOf("") }
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var resetEmail by remember { mutableStateOf("") }
    var resetCode by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }
    var resetConfirmPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = { Text("账号安全") },
        text = {
            DialogContentColumn {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SecurityMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.securityMode == mode,
                            onClick = { onModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SecurityMode.entries.size),
                            label = { Text(mode.label()) }
                        )
                    }
                }
                when (state.securityMode) {
                    SecurityMode.Email -> {
                        DetailLine("当前安全邮箱", state.user?.securityEmailMasked ?: "未绑定")
                        OutlinedTextField(
                            value = oldEmailCode,
                            onValueChange = { oldEmailCode = it.filter { char -> char.isDigit() }.take(6) },
                            label = { Text("旧邮箱验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                TextButton(onClick = onSendOldEmailCode) { Text("发送") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onVerifyOldEmail(oldEmailCode) },
                            enabled = !state.securityOldEmailVerified,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (state.securityOldEmailVerified) "已验证" else "验证旧邮箱")
                        }
                        OutlinedTextField(
                            value = newSecurityEmail,
                            onValueChange = { newSecurityEmail = it },
                            label = { Text("新安全邮箱") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = newEmailCode,
                            onValueChange = { newEmailCode = it.filter { char -> char.isDigit() }.take(6) },
                            label = { Text("新邮箱验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            trailingIcon = {
                                TextButton(
                                    enabled = state.securityOldEmailVerified,
                                    onClick = { onSendNewEmailCode(newSecurityEmail) }
                                ) { Text("发送") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onConfirmEmailChange(newSecurityEmail, newEmailCode) },
                            enabled = state.securityOldEmailVerified,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("更新安全邮箱")
                        }
                    }
                    SecurityMode.Password -> {
                        PasswordField(oldPassword, onValueChange = { oldPassword = it }, label = "原密码")
                        PasswordField(newPassword, onValueChange = { newPassword = it }, label = "新密码")
                        PasswordField(confirmPassword, onValueChange = { confirmPassword = it }, label = "确认新密码")
                        Button(
                            onClick = { onChangePassword(oldPassword, newPassword, confirmPassword) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("修改密码")
                        }
                    }
                    SecurityMode.Reset -> {
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text("安全邮箱") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            trailingIcon = {
                                TextButton(onClick = { onSendResetCode(resetEmail) }) { Text("发送") }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = resetCode,
                            onValueChange = { resetCode = it.filter { char -> char.isDigit() }.take(6) },
                            label = { Text("验证码") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PasswordField(resetPassword, onValueChange = { resetPassword = it }, label = "新密码")
                        PasswordField(resetConfirmPassword, onValueChange = { resetConfirmPassword = it }, label = "确认新密码")
                        Button(
                            onClick = { onResetPassword(resetEmail, resetCode, resetPassword, resetConfirmPassword) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("重置密码")
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun ProfileEditDialog(
    user: UserInfo,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var nickname by remember(user.userId) { mutableStateOf(user.nickname.orEmpty()) }
    var phone by remember(user.userId) { mutableStateOf(user.phone.orEmpty()) }
    var email by remember(user.userId) { mutableStateOf(user.email.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(nickname, phone, email) }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
        title = { Text("编辑资料") },
        text = {
            DialogContentColumn {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("昵称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it.filter { char -> char.isDigit() }.take(11) },
                    label = { Text("手机号") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("通知邮箱") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EventDetailDialog(
    event: EventInfo,
    isAudioLoading: Boolean,
    onDismiss: () -> Unit,
    onMarkRead: (EventInfo) -> Unit,
    onDelete: (EventInfo) -> Unit,
    onPlayAudio: (EventInfo) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            if (event.readStatus != "READ") {
                TextButton(onClick = { onMarkRead(event) }) { Text("标记已读") }
            }
        },
        title = {
            Text(
                eventTypeLabel(event.eventType),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            DialogContentColumn(verticalSpacing = 10.dp) {
                DetailLine("事件 ID", event.eventId)
                DetailLine("设备", event.deviceId ?: "未知设备")
                DetailLine("告警信息", alarmInfoLabel(event.alarmInfo))
                DetailLine("录音状态", fileStatusLabel(event.fileStatus))
                DetailLine("通知状态", pushStatusLabel(event.pushStatus))
                DetailLine("阅读状态", readStatusLabel(event.readStatus))
                DetailLine("发生时间", event.eventTime ?: "-")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onPlayAudio(event) },
                        enabled = event.fileStatus == "SUCCESS" && !isAudioLoading,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            if (isAudioLoading) Icons.Rounded.HourglassTop else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isAudioLoading) "获取中" else "播放音频")
                    }
                    TextButton(
                        onClick = { onDelete(event) },
                        colors = ButtonDefaults.textButtonColors(contentColor = CampusDanger)
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    )
}

@Composable
private fun AudioPlayerDialog(
    event: EventInfo,
    audioUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playbackState by remember(audioUrl) { mutableIntStateOf(Player.STATE_IDLE) }
    var isPlaying by remember(audioUrl) { mutableStateOf(false) }
    var positionMs by remember(audioUrl) { mutableLongStateOf(0L) }
    var durationMs by remember(audioUrl) { mutableLongStateOf(0L) }
    var playbackError by remember(audioUrl) { mutableStateOf<String?>(null) }
    val player = remember(audioUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(audioMediaItem(audioUrl))
            prepare()
            playWhenReady = true
        }
    }

    fun syncPlayerState() {
        playbackState = player.playbackState
        isPlaying = player.isPlaying
        positionMs = player.currentPosition.coerceAtLeast(0)
        val playerDuration = player.duration
        if (playerDuration > 0) {
            durationMs = playerDuration
        }
    }

    DisposableEffect(player, context) {
        val lifecycleOwner = context as? LifecycleOwner
        val lifecycleObserver = LifecycleEventObserver { _, lifecycleEvent ->
            if (lifecycleEvent == Lifecycle.Event.ON_STOP) {
                player.pause()
            }
        }
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                syncPlayerState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncPlayerState()
            }

            override fun onPlayerError(error: PlaybackException) {
                playbackError = playbackFailureLabel(error)
                syncPlayerState()
            }
        }
        player.addListener(listener)
        lifecycleOwner?.lifecycle?.addObserver(lifecycleObserver)
        syncPlayerState()
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            syncPlayerState()
            delay(250)
        }
    }

    val canSeek = durationMs > 0 && playbackError == null
    val sliderMax = if (durationMs > 0) durationMs.toFloat() else 1f
    val sliderPosition = positionMs.coerceIn(0L, if (durationMs > 0) durationMs else 1L).toFloat()
    val playbackFailed = playbackError != null
    val mainIcon = when {
        playbackFailed || playbackState == Player.STATE_ENDED -> Icons.Rounded.Replay
        isPlaying -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        title = {
            Text(
                eventTypeLabel(event.eventType),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            DialogContentColumn {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = CampusBlue)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            event.deviceId ?: "未知设备",
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            event.eventTime ?: "无时间",
                            color = CampusMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    playbackError ?: playbackStatusLabel(playbackState, isPlaying),
                    color = if (playbackError == null) CampusMuted else CampusDanger,
                    style = MaterialTheme.typography.bodySmall
                )
                Slider(
                    value = sliderPosition,
                    onValueChange = { positionMs = it.toLong() },
                    onValueChangeFinished = {
                        if (canSeek) {
                            player.seekTo(positionMs.coerceIn(0L, durationMs))
                        }
                    },
                    enabled = canSeek,
                    valueRange = 0f..sliderMax,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(positionMs), color = CampusMuted, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.weight(1f))
                    Text(formatDuration(durationMs), color = CampusMuted, style = MaterialTheme.typography.bodySmall)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(
                        onClick = {
                            playbackError = null
                            when {
                                playbackFailed -> {
                                    player.prepare()
                                    player.playWhenReady = true
                                }
                                playbackState == Player.STATE_ENDED -> {
                                    player.seekTo(0)
                                    player.play()
                                }
                                isPlaying -> player.pause()
                                else -> player.play()
                            }
                            syncPlayerState()
                        }
                    ) {
                        Icon(mainIcon, contentDescription = null)
                    }
                }
            }
        }
    )
}

private fun playbackStatusLabel(playbackState: Int, isPlaying: Boolean): String {
    return when {
        playbackState == Player.STATE_BUFFERING -> "缓冲中"
        playbackState == Player.STATE_ENDED -> "播放完成"
        isPlaying -> "播放中"
        playbackState == Player.STATE_READY -> "已暂停"
        else -> "准备中"
    }
}

private fun playbackFailureLabel(error: PlaybackException): String {
    return when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "网络异常，播放失败"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "录音地址已失效，请关闭后重新播放"
        else -> "播放失败，请关闭后重试"
    }
}

private fun audioMediaItem(audioUrl: String): MediaItem {
    val lowerUrl = audioUrl.lowercase()
    val mimeType = when {
        ".mp3" in lowerUrl -> MimeTypes.AUDIO_MPEG
        ".wav" in lowerUrl || ".wave" in lowerUrl -> MimeTypes.AUDIO_WAV
        else -> null
    }
    return MediaItem.Builder()
        .setUri(audioUrl)
        .apply {
            if (mimeType != null) {
                setMimeType(mimeType)
            }
        }
        .build()
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
private fun SummaryCard(state: CampusUiState, onRefresh: () -> Unit) {
    SoftCard(containerColor = CampusSoftBlue) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("校园防护", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("实时连接：${state.realtimeStatus.label()}", color = CampusMuted, modifier = Modifier.padding(top = 4.dp))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
            }
        }
        Spacer(Modifier.height(18.dp))
        MetricGrid(
            "设备" to state.devices.size.toString(),
            "在线" to state.devices.count { it.onlineStatus == "ONLINE" }.toString(),
            "未读" to state.unreadCount.toString()
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            color = CampusBlue,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(label, color = CampusMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MetricGrid(vararg metrics: Pair<String, String>) {
    val maxItems = if (metrics.size <= 3) 3 else 2
    val minItemWidth = if (metrics.size <= 3) 72.dp else 92.dp
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = maxItems,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        metrics.forEach { (label, value) ->
            Metric(label, value, Modifier.weight(1f).widthIn(min = minItemWidth))
        }
    }
}

@Composable
private fun BackgroundCapabilityRow(
    icon: ImageVector,
    title: String,
    status: String,
    statusColor: Color,
    action: String,
    onAction: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = statusColor)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(status, color = statusColor, modifier = Modifier.padding(top = 2.dp))
        }
        FilledTonalButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Rounded.Settings, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(action)
        }
    }
}

@Composable
private fun DeviceCard(device: DeviceInfo, onClick: (() -> Unit)? = null) {
    SoftCard(modifier = if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Devices, contentDescription = null, tint = statusColor(device.onlineStatus))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.deviceName ?: device.deviceId,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${device.location ?: "未设置地点"} · ${device.deviceId}",
                    color = CampusMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AssistChip(
                onClick = {},
                label = { Text(onlineStatusLabel(device.onlineStatus)) },
                colors = AssistChipDefaults.assistChipColors(labelColor = statusColor(device.onlineStatus))
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun EventCard(
    event: EventInfo,
    compact: Boolean,
    onClick: (() -> Unit)? = null,
    isAudioLoading: Boolean = false,
    onMarkRead: ((EventInfo) -> Unit)? = null,
    onDelete: ((EventInfo) -> Unit)? = null,
    onPlayAudio: ((EventInfo) -> Unit)? = null
) {
    val accent = riskColor(event)
    val unread = event.readStatus == "UNREAD"
    val modifier = if (onClick == null) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().clickable(onClick = onClick)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unread) Color(0xFFFFFBF4) else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (unread) 2.dp else 1.dp)
    ) {
        Column(Modifier.padding(if (compact) 14.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(if (compact) 46.dp else 52.dp)
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(if (compact) 24.dp else 28.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            eventTypeLabel(event.eventType),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = CampusText,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(8.dp))
                        EventReadIndicator(event.readStatus)
                    }
                    Text(
                        alarmInfoLabel(event.alarmInfo),
                        color = if (unread) CampusText else CampusMuted,
                        modifier = Modifier.padding(top = 8.dp),
                        maxLines = if (compact) 2 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EventMetaItem(
                    icon = Icons.Rounded.Devices,
                    text = event.deviceId ?: "未知设备",
                    modifier = Modifier.weight(1f)
                )
                EventMetaItem(
                    icon = Icons.Rounded.Schedule,
                    text = event.eventTime ?: "时间未知",
                    modifier = Modifier.weight(1.3f)
                )
            }
            if (!compact) {
                Spacer(Modifier.height(10.dp))
                EventActionsGrid(
                    event = event,
                    isAudioLoading = isAudioLoading,
                    onMarkRead = onMarkRead,
                    onDelete = onDelete,
                    onPlayAudio = onPlayAudio
                )
            }
        }
    }
}

@Composable
private fun EventActionsGrid(
    event: EventInfo,
    isAudioLoading: Boolean,
    onMarkRead: ((EventInfo) -> Unit)?,
    onDelete: ((EventInfo) -> Unit)?,
    onPlayAudio: ((EventInfo) -> Unit)?
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EventRecordingStatusRow(event)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 292.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        EventAudioActionChip(
                            event = event,
                            isAudioLoading = isAudioLoading,
                            onPlayAudio = onPlayAudio,
                            modifier = Modifier.weight(1f)
                        )
                        EventDeleteActionChip(
                            event = event,
                            onDelete = onDelete,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (event.readStatus != "READ" && onMarkRead != null) {
                        EventMarkReadActionChip(
                            event = event,
                            onMarkRead = onMarkRead,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EventAudioActionChip(
                        event = event,
                        isAudioLoading = isAudioLoading,
                        onPlayAudio = onPlayAudio,
                        modifier = Modifier.width(84.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.width(106.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EventMarkReadActionChip(
                            event = event,
                            onMarkRead = onMarkRead,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    EventDeleteActionChip(
                        event = event,
                        onDelete = onDelete,
                        modifier = Modifier.width(84.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EventRecordingStatusRow(event: EventInfo, modifier: Modifier = Modifier) {
    val color = fileStatusColor(event.fileStatus)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF7F8FB),
        contentColor = CampusText
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "录音状态",
                color = CampusMuted,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                fileStatusLabel(event.fileStatus),
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventActionPlaceholder(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(34.dp))
}

@Composable
private fun EventAudioActionChip(
    event: EventInfo,
    isAudioLoading: Boolean,
    onPlayAudio: ((EventInfo) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (onPlayAudio == null) {
        EventActionPlaceholder(modifier)
        return
    }
    EventActionChip(
        icon = if (isAudioLoading) Icons.Rounded.HourglassTop else Icons.Rounded.PlayArrow,
        label = if (isAudioLoading) "获取中" else "音频",
        onClick = { onPlayAudio(event) },
        enabled = event.fileStatus == "SUCCESS" && !isAudioLoading,
        modifier = modifier
    )
}

@Composable
private fun EventMarkReadActionChip(
    event: EventInfo,
    onMarkRead: ((EventInfo) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (event.readStatus == "READ" || onMarkRead == null) {
        EventActionPlaceholder(modifier)
        return
    }
    EventActionChip(
        icon = Icons.Rounded.CheckCircle,
        label = "标记已读",
        onClick = { onMarkRead(event) },
        color = CampusSuccess,
        modifier = modifier
    )
}

@Composable
private fun EventDeleteActionChip(
    event: EventInfo,
    onDelete: ((EventInfo) -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (onDelete == null) {
        EventActionPlaceholder(modifier)
        return
    }
    EventActionChip(
        icon = Icons.Rounded.Delete,
        label = "删除",
        onClick = { onDelete(event) },
        color = CampusDanger,
        modifier = modifier
    )
}

@Composable
private fun EventMetaItem(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color(0xFFF5F7FA), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CampusMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = CampusMuted,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EventStatusPill(
    text: String,
    color: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(34.dp),
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        contentColor = color
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EventReadIndicator(readStatus: String?) {
    Box(
        modifier = Modifier.width(58.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (readStatus == "READ") {
            Text(
                "已读",
                color = CampusMuted,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            EventStatusPill(
                text = readStatusLabel(readStatus),
                color = readStatusColor(readStatus)
            )
        }
    }
}

@Composable
private fun EventActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = CampusBlue,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(999.dp)
    val contentColor = if (enabled) color else CampusMuted.copy(alpha = 0.55f)
    val containerColor = if (enabled) color.copy(alpha = 0.10f) else Color(0xFFF3F5F8)
    Surface(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun EmptyCard(text: String) {
    SoftCard {
        Text(text, color = CampusMuted, modifier = Modifier.padding(vertical = 18.dp))
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = CampusMuted, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DialogContentColumn(
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing),
        content = content
    )
}

@Composable
private fun SoftCard(
    modifier: Modifier = Modifier,
    containerColor: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

private fun CampusTab.icon() = when (this) {
    CampusTab.Home -> Icons.Rounded.Home
    CampusTab.Devices -> Icons.Rounded.Devices
    CampusTab.Events -> Icons.Rounded.Warning
    CampusTab.Status -> Icons.Rounded.Notifications
    CampusTab.Profile -> Icons.Rounded.Person
}

private fun AuthMode.label() = when (this) {
    AuthMode.Login -> "登录"
    AuthMode.Register -> "注册"
    AuthMode.ResetPassword -> "找回"
}

private fun AuthMode.primaryAction() = when (this) {
    AuthMode.Login -> "登录"
    AuthMode.Register -> "注册并登录"
    AuthMode.ResetPassword -> "重置密码"
}

private fun SecurityMode.label() = when (this) {
    SecurityMode.Email -> "邮箱"
    SecurityMode.Password -> "密码"
    SecurityMode.Reset -> "找回"
}

private fun authSubtitle(mode: AuthMode) = when (mode) {
    AuthMode.Login -> "请使用安全邮箱或 user_id 登录"
    AuthMode.Register -> "新账号使用安全邮箱注册"
    AuthMode.ResetPassword -> "通过安全邮箱重置密码"
}

private fun List<EventInfo>.filterBy(filter: EventFilter): List<EventInfo> {
    return when (filter) {
        EventFilter.All -> this
        EventFilter.Unread -> filter { it.readStatus == "UNREAD" }
        EventFilter.Sos -> filter { it.eventType == "SOS" }
        EventFilter.Help -> filter { it.eventType == "HELP" }
        EventFilter.Fight -> filter { it.eventType == "FIGHT" }
        EventFilter.Voice -> filter { it.eventType == "VOICE" }
        EventFilter.Button -> filter { it.eventType == "BUTTON" }
        EventFilter.Playable -> filter { it.fileStatus == "SUCCESS" }
        EventFilter.Uploading -> filter { it.fileStatus == "UPLOADING" }
        EventFilter.Failed -> filter { it.fileStatus == "FAILED" }
    }
}

private fun List<EventInfo>.filterByKeyword(keyword: String): List<EventInfo> {
    val key = keyword.trim()
    if (key.isEmpty()) return this
    return filter { event ->
        listOfNotNull(
            event.eventId,
            event.deviceId,
            event.eventType,
            DisplayLabels.eventType(event.eventType, fallback = ""),
            event.alarmInfo,
            DisplayLabels.alarmInfo(event.alarmInfo, fallback = ""),
            event.fileStatus,
            DisplayLabels.fileStatus(event.fileStatus, fallback = ""),
            event.pushStatus,
            DisplayLabels.pushStatus(event.pushStatus, fallback = ""),
            event.readStatus,
            DisplayLabels.readStatus(event.readStatus, fallback = ""),
            event.eventTime
        ).any { it.contains(key, ignoreCase = true) }
    }
}

private fun List<DeviceInfo>.filterBy(keyword: String): List<DeviceInfo> {
    val key = keyword.trim()
    if (key.isEmpty()) return this
    return filter { device ->
        listOfNotNull(
            device.deviceId,
            device.deviceName,
            device.location,
            device.note,
            device.onlineStatus,
            DisplayLabels.onlineStatus(device.onlineStatus, fallback = ""),
            device.lastOnlineTime
        ).any { it.contains(key, ignoreCase = true) }
    }
}

private fun eventTypeLabel(type: String?): String {
    return DisplayLabels.eventType(type)
}

private fun alarmInfoLabel(value: String?): String {
    return DisplayLabels.alarmInfo(value)
}

private fun readStatusLabel(status: String?): String {
    return DisplayLabels.readStatus(status)
}

private fun readStatusColor(status: String?): Color {
    return when (status) {
        "READ" -> CampusSuccess
        "UNREAD" -> CampusWarning
        else -> CampusMuted
    }
}

private fun pushStatusLabel(status: String?): String {
    return DisplayLabels.pushStatus(status)
}

private fun onlineStatusLabel(status: String?): String {
    return DisplayLabels.onlineStatus(status)
}

private fun fileStatusLabel(status: String?): String {
    return DisplayLabels.fileStatus(status, fallback = "无录音状态")
}

private fun fileStatusColor(status: String?): Color {
    return when (status) {
        "SUCCESS" -> CampusTeal
        "UPLOADING" -> CampusWarning
        "FAILED" -> CampusDanger
        else -> CampusMuted
    }
}

private fun visiblePageItems(currentPage: Long, pageTotal: Long): List<Long?> {
    val total = pageTotal.coerceAtLeast(1)
    val current = currentPage.coerceIn(1, total)
    val pages = if (total <= 7) {
        (1..total).toList()
    } else {
        listOf(1, current - 1, current, current + 1, total)
            .filter { it in 1..total }
            .distinct()
            .sorted()
    }
    return pages.flatMapIndexed { index, page ->
        if (index > 0 && page - pages[index - 1] > 1) listOf<Long?>(null, page) else listOf(page)
    }
}

private fun RealtimeStatus.label() = when (this) {
    RealtimeStatus.OFFLINE -> "未连接"
    RealtimeStatus.CONNECTING -> "连接中"
    RealtimeStatus.CONNECTED -> "已连接"
    RealtimeStatus.RECONNECTING -> "恢复中"
}

private fun statusColor(status: RealtimeStatus) = when (status) {
    RealtimeStatus.CONNECTED -> CampusSuccess
    RealtimeStatus.CONNECTING, RealtimeStatus.RECONNECTING -> CampusWarning
    RealtimeStatus.OFFLINE -> CampusMuted
}

private fun statusColor(status: String?) = when (status) {
    "ONLINE" -> CampusSuccess
    "OFFLINE" -> CampusMuted
    else -> CampusWarning
}

private fun riskColor(event: EventInfo): Color {
    when (event.eventType) {
        "SOS", "HELP", "FIGHT" -> return CampusDanger
        "VOICE" -> return CampusWarning
        "BUTTON" -> return CampusBlue
    }
    val text = listOfNotNull(event.alarmInfo, event.eventType).joinToString(" ").lowercase()
    return when {
        "help" in text || "fight" in text || "救命" in text || "打" in text -> CampusDanger
        event.readStatus == "UNREAD" -> CampusWarning
        else -> CampusBlue
    }
}
