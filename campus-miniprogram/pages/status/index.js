const deviceApi = require('../../services/device-api')
const eventApi = require('../../services/event-api')
const time = require('../../utils/time')
const tabSwipe = require('../../utils/tab-swipe')

Page(tabSwipe.withTabSwipe({
  data: {
    loading: false,
    initialized: false,
    loadError: false,
    navStyle: '',
    contentStyle: '',
    stats: {
      deviceTotal: 0,
      onlineTotal: 0,
      todayEvents: 0,
      unreadEvents: 0
    },
    realtimeText: '实时离线',
    realtimeClass: 'offline'
  },
  onLoad() {
    getApp().setNavLayout(this)
    this.onRealtimeConnectionStatus(getApp().getRealtimeStatus())
  },
  onShow() {
    this.pageVisible = true
    getApp().setNavLayout(this)
    this.onRealtimeConnectionStatus(getApp().getRealtimeStatus())
    getApp().deferEnsureEventRealtime()
    this.load()
  },
  async onPullDownRefresh() {
    try {
      await this.load(true)
    } finally {
      wx.stopPullDownRefresh()
    }
  },
  onUnload() {
    clearTimeout(this.reloadTimer)
  },
  onHide() {
    this.pageVisible = false
    clearTimeout(this.reloadTimer)
  },
  async load(force = false, bypassFresh = false) {
    if (!force && !bypassFresh && this.lastLoadedAt && Date.now() - this.lastLoadedAt < 5000) return
    const requestId = (this.loadRequestId || 0) + 1
    this.loadRequestId = requestId
    this.setData({ loading: true, loadError: false })
    try {
      const range = time.todayRange()
      const [devicePage, todayEvents, unreadEvents] = await Promise.all([
        getApp().getBoundDevices(force),
        eventApi.search({ start_time: range.start_time, end_time: range.end_time, page: 1, size: 1 }, { showError: false }),
        getApp().refreshUnreadEventBadge(force)
      ])
      if (requestId !== this.loadRequestId) return
      const devices = devicePage.records || []
      this.setData({
        initialized: true,
        stats: {
          deviceTotal: devices.length,
          onlineTotal: devices.filter((item) => (item.online_status || item.onlineStatus) === 'ONLINE').length,
          todayEvents: todayEvents.total || 0,
          unreadEvents
        }
      })
      this.lastLoadedAt = Date.now()
    } catch (err) {
      if (requestId === this.loadRequestId) {
        this.setData({ loadError: true })
      }
    } finally {
      if (requestId === this.loadRequestId) {
        this.setData({ loading: false })
      }
    }
  },
  onRealtimeDeviceStatus() {
    const devices = getApp().globalData.boundDevices || []
    this.setData({
      'stats.deviceTotal': devices.length,
      'stats.onlineTotal': devices.filter((item) => (item.online_status || item.onlineStatus) === 'ONLINE').length
    })
  },
  onRealtimeNewEvent() {
    this.setData({
      'stats.unreadEvents': this.data.stats.unreadEvents + 1
    })
    this.scheduleLoad()
  },
  onRealtimeConnectionStatus(status) {
    const state = {
      CONNECTED: { realtimeText: '实时通道正常', realtimeClass: 'online' },
      CONNECTING: { realtimeText: '正在连接实时通道', realtimeClass: 'connecting' },
      RECONNECTING: { realtimeText: '实时通道恢复中', realtimeClass: 'connecting' },
      OFFLINE: { realtimeText: '实时通道离线', realtimeClass: 'offline' }
    }[status] || { realtimeText: '实时通道离线', realtimeClass: 'offline' }
    this.setData(state)
  },
  openEvents() {
    wx.switchTab({ url: '/pages/events/index' })
  },
  openUnreadEvents() {
    getApp().setEventListPreset({
      filters: { read_status: 'UNREAD' },
      labels: { readLabel: '未读' }
    })
    wx.switchTab({ url: '/pages/events/index' })
  },
  openDevices() {
    wx.switchTab({ url: '/pages/devices/index' })
  },
  scheduleLoad() {
    if (!this.pageVisible) return
    clearTimeout(this.reloadTimer)
    this.reloadTimer = setTimeout(() => this.load(false, true), 1200)
  }
}))
