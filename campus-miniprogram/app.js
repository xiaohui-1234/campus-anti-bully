const eventApi = require('./services/event-api')
const deviceApi = require('./services/device-api')
const websocket = require('./services/websocket')
const storage = require('./utils/storage')

App({
  globalData: {
    systemName: '校园防霸凌系统',
    navLayout: null,
    unreadEventCount: 0,
    pushedEventCount: 0,
    pushedEvents: [],
    pushedEventIds: [],
    eventRealtimeListenerReady: false,
    eventRealtimeLoading: false,
    eventRealtimeStartTimer: null,
    eventRealtimeForce: false,
    eventPullTimer: null,
    eventPulling: false,
    realtimeStatus: 'OFFLINE',
    pendingEvent: null,
    loginRedirecting: false,
    boundDevices: [],
    boundDevicesFetchedAt: 0,
    boundDevicesLoading: null,
    unreadBadgeFetchedAt: 0,
    unreadBadgeLoading: null,
    eventListPreset: null
  },
  onLaunch() {
    wx.setStorageSync('app_launched_at', Date.now())
  },
  getNavLayout() {
    if (this.globalData.navLayout) {
      return this.globalData.navLayout
    }
    const systemInfo = wx.getSystemInfoSync()
    const menuButton = typeof wx.getMenuButtonBoundingClientRect === 'function'
      ? wx.getMenuButtonBoundingClientRect()
      : {}
    const statusBarHeight = systemInfo.statusBarHeight || 0
    const menuHeight = menuButton.height || 32
    const navBarHeight = Math.max(44, menuHeight + 12)
    const navHeight = statusBarHeight + navBarHeight
    const titleTop = statusBarHeight + Math.max(0, (navBarHeight - 32) / 2)

    this.globalData.navLayout = {
      navStyle: `height:${navHeight}px;padding-top:${titleTop}px;`,
      contentStyle: `padding-top:${navHeight + 24}px;`
    }
    return this.globalData.navLayout
  },
  setNavLayout(page) {
    const layout = this.getNavLayout()
    if (page.data.navStyle !== layout.navStyle || page.data.contentStyle !== layout.contentStyle) {
      page.setData(layout)
    }
  },
  deferEnsureEventRealtime(force = false) {
    if (!storage.getAccessToken()) return
    this.globalData.eventRealtimeForce = this.globalData.eventRealtimeForce || force
    clearTimeout(this.globalData.eventRealtimeStartTimer)
    this.globalData.eventRealtimeStartTimer = setTimeout(() => {
      const shouldForce = this.globalData.eventRealtimeForce
      this.globalData.eventRealtimeForce = false
      this.ensureEventRealtime(shouldForce)
    }, 300)
  },
  async ensureEventRealtime(force = false) {
    if (!storage.getAccessToken()) return
    if (this.globalData.eventRealtimeLoading) {
      if (force) this.deferEnsureEventRealtime(true)
      return
    }
    this.globalData.eventRealtimeLoading = true
    try {
      if (!this.globalData.eventRealtimeListenerReady) {
        websocket.onMessage('NEW_EVENT', (event) => {
          this.handleSocketMessage('NEW_EVENT', event)
        })
        websocket.onMessage('DEVICE_STATUS', (status) => {
          this.handleSocketMessage('DEVICE_STATUS', status)
        })
        websocket.onStatus((status) => {
          this.setRealtimeStatus(status)
        })
        this.globalData.eventRealtimeListenerReady = true
      }
      const data = await this.getBoundDevices(force)
      const devices = data.records || []
      websocket.connect()
      websocket.subscribeEvents(devices.map((item) => item.device_id || item.deviceId))
      const pages = getCurrentPages()
      const currentPage = pages[pages.length - 1]
      if (!currentPage || currentPage.route !== 'pages/home/index') {
        this.refreshUnreadEventBadge()
      }
    } catch (err) {
      // 登录前或网络暂不可用时，等页面下次 onShow 再尝试。
    } finally {
      this.globalData.eventRealtimeLoading = false
    }
  },
  setPushedEvents(events = []) {
    const merged = []
    const seen = new Set()
    events.concat(this.globalData.pushedEvents).forEach((item) => {
      const eventId = item && (item.event_id || item.eventId)
      if (!eventId || seen.has(eventId)) return
      seen.add(eventId)
      merged.push(item)
    })
    this.globalData.pushedEvents = merged
    this.globalData.pushedEventIds = Array.from(seen)
    this.setHomePushBadge(merged.length)
  },
  addPushedEvents(events = []) {
    events.forEach((event) => this.addPushedEvent(event))
  },
  addPushedEvent(event) {
    const eventId = event && (event.event_id || event.eventId)
    if (eventId && this.globalData.pushedEventIds.includes(eventId)) {
      return false
    }
    if (eventId) {
      this.globalData.pushedEventIds = [eventId].concat(this.globalData.pushedEventIds)
    }
    this.globalData.pushedEvents = [event].concat(this.globalData.pushedEvents)
    this.setHomePushBadge(this.globalData.pushedEventCount + 1)
    this.setUnreadEventBadge(this.globalData.unreadEventCount + 1)
    return true
  },
  getPushedEvents() {
    return this.globalData.pushedEvents || []
  },
  consumePushedEvents() {
    const events = this.globalData.pushedEvents || []
    this.globalData.pushedEvents = []
    this.globalData.pushedEventIds = []
    this.setHomePushBadge(0)
    return events
  },
  async getBoundDevices(force = false) {
    const now = Date.now()
    if (!force && this.globalData.boundDevicesFetchedAt && now - this.globalData.boundDevicesFetchedAt < 30000) {
      return {
        records: this.globalData.boundDevices,
        total: this.globalData.boundDevices.length
      }
    }
    if (this.globalData.boundDevicesLoading) {
      return this.globalData.boundDevicesLoading
    }
    const loading = deviceApi.listAll({ showError: false })
      .then((data) => {
        this.globalData.boundDevices = data.records || []
        this.globalData.boundDevicesFetchedAt = Date.now()
        return data
      })
      .finally(() => {
        this.globalData.boundDevicesLoading = null
      })
    this.globalData.boundDevicesLoading = loading
    return loading
  },
  invalidateBoundDevices() {
    this.globalData.boundDevicesFetchedAt = 0
  },
  updateBoundDeviceStatus(status) {
    const deviceId = status && (status.device_id || status.deviceId)
    if (!deviceId) return
    const index = this.globalData.boundDevices.findIndex((item) => (item.device_id || item.deviceId) === deviceId)
    if (index < 0) return
    const devices = this.globalData.boundDevices.slice()
    devices[index] = Object.assign({}, devices[index], {
      online_status: status.online_status || status.onlineStatus,
      last_online_time: status.last_online_time || status.lastOnlineTime
    })
    this.globalData.boundDevices = devices
  },
  async refreshUnreadEventBadge(force = false) {
    const now = Date.now()
    if (!force && this.globalData.unreadBadgeFetchedAt && now - this.globalData.unreadBadgeFetchedAt < 5000) {
      return this.globalData.unreadEventCount
    }
    if (this.globalData.unreadBadgeLoading) {
      return this.globalData.unreadBadgeLoading
    }
    const loading = eventApi.countUnread()
      .then((count) => {
        this.globalData.unreadBadgeFetchedAt = Date.now()
        this.setUnreadEventBadge(count)
        return count
      })
      .catch(() => this.globalData.unreadEventCount)
      .finally(() => {
        this.globalData.unreadBadgeLoading = null
      })
    this.globalData.unreadBadgeLoading = loading
    try {
      return await loading
    } catch (err) {
      return this.globalData.unreadEventCount
    }
  },
  setUnreadEventBadge(count) {
    const next = Math.max(0, Number(count) || 0)
    const text = next > 99 ? '99+' : String(next)
    this.globalData.unreadEventCount = next
    this.setTabBadge(0, next, text)
  },
  syncUnreadEventBadge(count) {
    this.globalData.unreadBadgeFetchedAt = Date.now()
    this.setUnreadEventBadge(count)
  },
  setHomePushBadge(count) {
    const next = Math.max(0, Number(count) || 0)
    const text = next > 99 ? '99+' : String(next)
    this.globalData.pushedEventCount = next
    this.setTabBadge(2, next, text)
    this.setTabRedDot(2, next)
  },
  setRealtimeStatus(status) {
    this.globalData.realtimeStatus = status || 'OFFLINE'
    if (this.globalData.realtimeStatus === 'CONNECTED') {
      this.stopEventPulling()
    } else if (storage.getAccessToken()) {
      this.startEventPulling()
    }
    this.notifyPages('onRealtimeConnectionStatus', this.globalData.realtimeStatus)
  },
  getRealtimeStatus() {
    return this.globalData.realtimeStatus || websocket.getStatus()
  },
  setPendingEvent(event) {
    this.globalData.pendingEvent = event || null
  },
  consumePendingEvent() {
    const event = this.globalData.pendingEvent
    this.globalData.pendingEvent = null
    return event
  },
  setEventListPreset(preset) {
    this.globalData.eventListPreset = preset || null
  },
  consumeEventListPreset() {
    const preset = this.globalData.eventListPreset
    this.globalData.eventListPreset = null
    return preset
  },
  resetSession() {
    storage.clearTokens()
    clearTimeout(this.globalData.eventRealtimeStartTimer)
    this.stopEventPulling()
    websocket.close()
    this.globalData.eventRealtimeStartTimer = null
    this.globalData.eventRealtimeForce = false
    this.globalData.eventPullTimer = null
    this.globalData.eventPulling = false
    this.globalData.eventRealtimeLoading = false
    this.globalData.pushedEvents = []
    this.globalData.pushedEventIds = []
    this.globalData.pendingEvent = null
    this.globalData.boundDevices = []
    this.globalData.boundDevicesFetchedAt = 0
    this.globalData.boundDevicesLoading = null
    this.globalData.unreadBadgeFetchedAt = 0
    this.globalData.unreadBadgeLoading = null
    this.globalData.eventListPreset = null
    this.setUnreadEventBadge(0)
    this.setHomePushBadge(0)
    this.setRealtimeStatus('OFFLINE')
  },
  redirectToLogin() {
    if (this.globalData.loginRedirecting) return
    this.resetSession()
    const pages = getCurrentPages()
    const currentPage = pages[pages.length - 1]
    if (currentPage && currentPage.route === 'pages/login/index') return
    this.globalData.loginRedirecting = true
    wx.reLaunch({
      url: '/pages/login/index',
      complete: () => {
        this.globalData.loginRedirecting = false
      }
    })
  },
  setTabBadge(index, count, text) {
    const next = Math.max(0, Number(count) || 0)
    if (next > 0) {
      wx.setTabBarBadge({ index, text, fail() {} })
    } else {
      wx.removeTabBarBadge({ index, fail() {} })
    }
  },
  setTabRedDot(index, count) {
    const next = Math.max(0, Number(count) || 0)
    if (next > 0) {
      wx.showTabBarRedDot({ index, fail() {} })
    } else {
      wx.hideTabBarRedDot({ index, fail() {} })
    }
  },
  handleSocketMessage(type, data) {
    if (type === 'NEW_EVENT') {
      if (!this.addPushedEvent(data)) return
      this.notifyPages('onRealtimeNewEvent', data)
      return
    }
    if (type === 'DEVICE_STATUS') {
      this.updateBoundDeviceStatus(data)
      this.notifyPages('onRealtimeDeviceStatus', data)
    }
  },
  startEventPulling() {
    if (this.globalData.eventPullTimer) return
    this.globalData.eventPullTimer = setInterval(() => {
      this.pullPendingEvents()
    }, 10000)
  },
  stopEventPulling() {
    clearInterval(this.globalData.eventPullTimer)
    this.globalData.eventPullTimer = null
  },
  async pullPendingEvents() {
    if (!storage.getAccessToken()) return
    if (this.globalData.realtimeStatus === 'CONNECTED') return
    if (this.globalData.eventPulling) return
    this.globalData.eventPulling = true
    try {
      const events = await eventApi.unpulled()
      if (events && events.length) {
        events.forEach((event) => this.handleSocketMessage('NEW_EVENT', event))
      }
    } catch (err) {
      // 轮询只是 WebSocket 兜底，失败时保持当前徽标。
    } finally {
      this.globalData.eventPulling = false
    }
  },
  notifyPages(method, payload) {
    getCurrentPages().forEach((page) => {
      if (typeof page[method] === 'function') {
        page[method](payload)
      }
    })
  }
})
