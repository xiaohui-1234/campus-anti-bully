const deviceApi = require('../../services/device-api')
const eventApi = require('../../services/event-api')
const websocket = require('../../services/websocket')
const audioPlayer = require('../../utils/audio-player')
const eventLabels = require('../../utils/event-labels')
const tabSwipe = require('../../utils/tab-swipe')

Page(tabSwipe.withTabSwipe({
  data: {
    loading: false,
    initialized: false,
    navStyle: '',
    contentStyle: '',
    devices: [],
    onlineTotal: 0,
    events: [],
    pendingTotal: 0,
    loadError: false,
    realtimeText: '实时离线',
    realtimeClass: 'offline',
    audioState: {
      event_id: '',
      playing: false,
      progress: 0
    }
  },
  onLoad() {
    getApp().setNavLayout(this)
    this.onRealtimeConnectionStatus(getApp().getRealtimeStatus())
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    const changedAt = getApp().globalData.boundDevicesChangedAt || 0
    const force = !!changedAt && this.handledBoundDevicesChangedAt !== changedAt
    if (force) {
      this.handledBoundDevicesChangedAt = changedAt
      this.lastLoadedAt = 0
      const removedDeviceIds = getApp().globalData.removedBoundDeviceIds || []
      removedDeviceIds.forEach((deviceId) => {
        this.removeDeviceSnapshot(deviceId)
      })
    }
    this.bootstrap(force)
  },
  async onPullDownRefresh() {
    try {
      await this.bootstrap(true)
    } finally {
      wx.stopPullDownRefresh()
    }
  },
  onHide() {
    this.pendingAudioEventId = ''
    audioPlayer.stop()
  },
  onUnload() {
    this.pendingAudioEventId = ''
    audioPlayer.stop()
  },
  updateAudioState(state) {
    this.setData({
      audioState: state || {
        event_id: '',
        playing: false,
        progress: 0
      }
    })
  },
  async bootstrap(force = false) {
    if (!force && this.lastLoadedAt && Date.now() - this.lastLoadedAt < 5000) return
    const requestId = (this.bootstrapRequestId || 0) + 1
    this.bootstrapRequestId = requestId
    this.setData({ loading: true, loadError: false })
    try {
      const cachedEvents = this.filterEventsByDevices(getApp().getPushedEvents(), this.data.devices)
      if (cachedEvents.length) {
        this.setData({ events: cachedEvents.map(eventLabels.formatEvent) })
      }
      const [devicePage, unreadPage, pulledEvents] = await Promise.all([
        getApp().getBoundDevices(force),
        eventApi.search({ read_status: 'UNREAD', page: 1, size: 10 }),
        getApp().getRealtimeStatus() === 'CONNECTED'
          ? Promise.resolve([])
          : eventApi.unpulled().catch(() => [])
      ])
      if (requestId !== this.bootstrapRequestId) return
      const devices = devicePage.records || []
      const filteredPulledEvents = this.filterEventsByDevices(pulledEvents, devices)
      const filteredUnreadRecords = this.filterEventsByDevices(unreadPage.records || [], devices)
      const currentEvents = this.filterEventsByDevices(this.data.events, devices)
      getApp().setPushedEvents(filteredPulledEvents)
      const events = this.mergeEvents(filteredPulledEvents, currentEvents, filteredUnreadRecords)
        .map((item) => this.formatEvent(item, devices))
      const pendingTotal = this.resolvePendingTotal(unreadPage, filteredUnreadRecords, devices)
      const nextPendingTotal = Math.max(pendingTotal, events.length)
      this.setData({
        devices,
        onlineTotal: this.countOnlineDevices(devices),
        events,
        pendingTotal: nextPendingTotal,
        initialized: true
      })
      getApp().consumePushedEvents()
      getApp().syncUnreadEventBadge(nextPendingTotal)
      this.bindWebSocket(devices)
      this.lastLoadedAt = Date.now()
    } catch (err) {
      if (requestId !== this.bootstrapRequestId) return
      const events = this.filterEventsByDevices(getApp().consumePushedEvents(), this.data.devices)
        .map((item) => this.formatEvent(item, this.data.devices))
      this.setData({
        events,
        pendingTotal: this.getDeviceIds(this.data.devices).length
          ? Math.max(this.data.pendingTotal, events.length)
          : events.length,
        loadError: true
      })
      getApp().refreshUnreadEventBadge()
    } finally {
      if (requestId === this.bootstrapRequestId) {
        this.setData({ loading: false })
      }
    }
  },
  bindWebSocket(devices) {
    websocket.connect()
    websocket.subscribeEvents(devices.map((item) => item.device_id || item.deviceId))
  },
  countOnlineDevices(devices) {
    return devices.filter((item) => (item.online_status || item.onlineStatus) === 'ONLINE').length
  },
  getDeviceId(item) {
    return item && (item.device_id || item.deviceId)
  },
  getDeviceIds(devices = []) {
    return (devices || []).map((item) => this.getDeviceId(item)).filter(Boolean)
  },
  hasKnownDeviceList(devices = this.data.devices) {
    const app = getApp()
    return this.getDeviceIds(devices).length > 0 ||
      !!app.globalData.boundDevicesFetchedAt ||
      !!app.globalData.boundDevicesChangedAt
  },
  filterEventsByDevices(events = [], devices = this.data.devices) {
    const removed = new Set(getApp().globalData.removedBoundDeviceIds || [])
    const allowedIds = this.getDeviceIds(devices)
    const allowed = new Set(allowedIds)
    const hasKnownDevices = this.hasKnownDeviceList(devices)
    return (events || []).filter((item) => {
      const deviceId = this.getDeviceId(item)
      if (deviceId && removed.has(deviceId)) return false
      if (!hasKnownDevices) return true
      return deviceId && allowed.has(deviceId)
    })
  },
  resolvePendingTotal(pageData, filteredRecords, devices = this.data.devices) {
    if (this.hasKnownDeviceList(devices) && !this.getDeviceIds(devices).length) {
      return 0
    }
    const total = Number(pageData && pageData.total) || 0
    const rawCount = (pageData && pageData.records || []).length
    if (rawCount && filteredRecords.length < rawCount && total <= rawCount) {
      return filteredRecords.length
    }
    return total
  },
  mergeEvents(...groups) {
    const seen = new Set()
    return groups.flat().filter((item) => {
      const eventId = item && (item.event_id || item.eventId)
      if (!eventId || seen.has(eventId)) return false
      seen.add(eventId)
      return true
    }).slice(0, 10)
  },
  formatEvent(event, devices) {
    const value = eventLabels.formatEvent(event)
    const deviceId = value.device_id || value.deviceId
    const device = (devices || []).find((item) => (item.device_id || item.deviceId) === deviceId)
    return Object.assign({}, value, {
      device_name_text: device && (device.device_name || device.deviceName) || deviceId || '未知设备'
    })
  },
  onRealtimeNewEvent(event) {
    if (!this.filterEventsByDevices([event], this.data.devices).length) {
      getApp().consumePushedEvents()
      return
    }
    const eventId = event.event_id || event.eventId
    const exists = this.data.events.some((item) => (item.event_id || item.eventId) === eventId)
    if (!exists) {
      this.setData({
        events: [this.formatEvent(event, this.data.devices)].concat(this.data.events).slice(0, 10),
        pendingTotal: this.data.pendingTotal + 1
      })
    }
    getApp().consumePushedEvents()
  },
  onRealtimeDeviceStatus(status) {
    const deviceId = status.device_id || status.deviceId
    const index = this.data.devices.findIndex((item) => (item.device_id || item.deviceId) === deviceId)
    if (index < 0) return
    const current = this.data.devices[index]
    const previousStatus = current.online_status || current.onlineStatus
    const nextStatus = status.online_status || status.onlineStatus
    const onlineDelta = previousStatus === nextStatus ? 0 : nextStatus === 'ONLINE' ? 1 : -1
    this.setData({
      [`devices[${index}].online_status`]: nextStatus,
      [`devices[${index}].last_online_time`]: status.last_online_time || status.lastOnlineTime,
      onlineTotal: Math.max(0, this.data.onlineTotal + onlineDelta)
    })
  },
  onBoundDeviceRemoved(payload) {
    const deviceId = payload && (payload.device_id || payload.deviceId)
    if (!deviceId) return
    this.removeDeviceSnapshot(deviceId)
    this.lastLoadedAt = 0
    this.handledBoundDevicesChangedAt = getApp().globalData.boundDevicesChangedAt || this.handledBoundDevicesChangedAt
    this.bootstrap(true)
  },
  removeDeviceSnapshot(deviceId) {
    const devices = this.data.devices.filter((item) => (item.device_id || item.deviceId) !== deviceId)
    const removedEvents = this.data.events.filter((item) => (item.device_id || item.deviceId) === deviceId)
    const events = this.data.events.filter((item) => (item.device_id || item.deviceId) !== deviceId)
    const removedUnreadCount = removedEvents.filter((item) => (item.read_status || item.readStatus) !== 'READ').length
    this.setData({
      devices,
      onlineTotal: this.countOnlineDevices(devices),
      events,
      pendingTotal: Math.max(0, this.data.pendingTotal - removedUnreadCount)
    })
  },
  onRealtimeConnectionStatus(status) {
    const state = {
      CONNECTED: { realtimeText: '实时在线', realtimeClass: 'online' },
      CONNECTING: { realtimeText: '正在连接', realtimeClass: 'connecting' },
      RECONNECTING: { realtimeText: '正在重连', realtimeClass: 'connecting' },
      OFFLINE: { realtimeText: '实时离线', realtimeClass: 'offline' }
    }[status] || { realtimeText: '实时离线', realtimeClass: 'offline' }
    this.setData(state)
  },
  openEvent(event) {
    getApp().setPendingEvent(event.detail)
    wx.switchTab({ url: '/pages/events/index' })
  },
  openAllEvents() {
    wx.switchTab({ url: '/pages/events/index' })
  },
  openUnreadEvents() {
    getApp().setEventListPreset({
      filters: { read_status: 'UNREAD' },
      labels: { readLabel: '未读' }
    })
    wx.switchTab({ url: '/pages/events/index' })
  },
  handleEmptyAction() {
    if (this.data.loadError) {
      this.bootstrap(true)
      return
    }
    this.openAllEvents()
  },
  async playAudio(event) {
    const target = event && event.detail
    const eventId = target && (target.event_id || target.eventId)
    if (!eventId) return
    if (audioPlayer.isPlaying(eventId)) {
      audioPlayer.stop()
      return
    }
    if (this.pendingAudioEventId === eventId) {
      this.pendingAudioEventId = ''
      this.updateAudioState()
      return
    }
    audioPlayer.stop()
    this.pendingAudioEventId = eventId
    let data
    try {
      data = await eventApi.refreshUrl(eventId)
    } catch (err) {
      if (this.pendingAudioEventId === eventId) {
        this.pendingAudioEventId = ''
      }
      this.updateAudioState()
      return
    }
    if (this.pendingAudioEventId !== eventId) return
    this.pendingAudioEventId = ''
    audioPlayer.play(data.file_url || data.fileUrl, {
      event_id: eventId,
      onState: (state) => this.updateAudioState(state)
    })
  }
}))
