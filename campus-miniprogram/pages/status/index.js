const deviceApi = require('../../services/device-api')
const eventApi = require('../../services/event-api')
const time = require('../../utils/time')

Page({
  data: {
    loading: false,
    navStyle: '',
    contentStyle: '',
    stats: {
      deviceTotal: 0,
      onlineTotal: 0,
      todayEvents: 0,
      unreadEvents: 0
    }
  },
  onLoad() {
    getApp().setNavLayout(this)
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    this.load()
  },
  async load() {
    this.setData({ loading: true })
    try {
      const devices = await deviceApi.list({ page: 1, size: 100 })
      const events = await eventApi.search({ page: 1, size: 100 })
      const deviceRecords = devices.records || []
      const eventRecords = events.records || []
      this.setData({
        stats: {
          deviceTotal: deviceRecords.length,
          onlineTotal: deviceRecords.filter((item) => (item.online_status || item.onlineStatus) === 'ONLINE').length,
          todayEvents: eventRecords.filter((item) => time.isToday(item.event_time || item.eventTime)).length,
          unreadEvents: eventRecords.filter((item) => (item.read_status || item.readStatus) === 'UNREAD').length
        }
      })
    } finally {
      this.setData({ loading: false })
    }
  },
  onRealtimeDeviceStatus() {
    this.load()
  },
  onRealtimeNewEvent() {
    this.load()
    getApp().refreshUnreadEventBadge()
  }
})
