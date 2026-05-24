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
    eventPullTimer: null,
    eventPulling: false
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
    clearTimeout(this.globalData.eventRealtimeStartTimer)
    this.globalData.eventRealtimeStartTimer = setTimeout(() => {
      this.ensureEventRealtime(force)
    }, 300)
  },
  async ensureEventRealtime(force = false) {
    if (!storage.getAccessToken()) return
    if (this.globalData.eventRealtimeLoading) return
    this.globalData.eventRealtimeLoading = true
    try {
      if (!this.globalData.eventRealtimeListenerReady) {
        websocket.onMessage('NEW_EVENT', (event) => {
          this.handleSocketMessage('NEW_EVENT', event)
        })
        websocket.onMessage('DEVICE_STATUS', (status) => {
          this.handleSocketMessage('DEVICE_STATUS', status)
        })
        this.globalData.eventRealtimeListenerReady = true
      }
      const data = await deviceApi.list({ page: 1, size: 100 })
      const devices = data.records || []
      websocket.connect()
      websocket.subscribeEvents(devices.map((item) => item.device_id || item.deviceId))
      this.startEventPulling()
      this.refreshUnreadEventBadge()
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
      return
    }
    if (eventId) {
      this.globalData.pushedEventIds = [eventId].concat(this.globalData.pushedEventIds)
    }
    this.globalData.pushedEvents = [event].concat(this.globalData.pushedEvents)
    this.setHomePushBadge(this.globalData.pushedEventCount + 1)
    this.setUnreadEventBadge(this.globalData.unreadEventCount + 1)
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
  async refreshUnreadEventBadge() {
    try {
      const count = await eventApi.countUnread()
      this.setUnreadEventBadge(count)
    } catch (err) {
      this.setUnreadEventBadge(this.globalData.unreadEventCount)
    }
  },
  setUnreadEventBadge(count) {
    const next = Math.max(0, Number(count) || 0)
    const text = next > 99 ? '99+' : String(next)
    this.globalData.unreadEventCount = next
    this.setTabBadge(0, next, text)
  },
  setHomePushBadge(count) {
    const next = Math.max(0, Number(count) || 0)
    const text = next > 99 ? '99+' : String(next)
    this.globalData.pushedEventCount = next
    this.setTabBadge(2, next, text)
    this.setTabRedDot(2, next)
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
      this.addPushedEvent(data)
      this.notifyPages('onRealtimeNewEvent', data)
      return
    }
    if (type === 'DEVICE_STATUS') {
      this.notifyPages('onRealtimeDeviceStatus', data)
    }
  },
  startEventPulling() {
    if (this.globalData.eventPullTimer) return
    this.globalData.eventPullTimer = setInterval(() => {
      this.pullPendingEvents()
    }, 3000)
  },
  async pullPendingEvents() {
    if (!storage.getAccessToken()) return
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
