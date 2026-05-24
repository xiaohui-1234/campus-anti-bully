const deviceApi = require('../../services/device-api')
const eventApi = require('../../services/event-api')
const websocket = require('../../services/websocket')

Page({
  data: {
    loading: false,
    navStyle: '',
    contentStyle: '',
    devices: [],
    events: []
  },
  onLoad() {
    getApp().setNavLayout(this)
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    this.bootstrap()
  },
  async bootstrap() {
    this.setData({ loading: true })
    try {
      const cachedEvents = getApp().getPushedEvents()
      if (cachedEvents.length) {
        this.setData({ events: cachedEvents })
      }
      const devicePage = await deviceApi.list({ page: 1, size: 100 })
      const devices = devicePage.records || []
      const pulledEvents = await eventApi.unpulled()
      getApp().setPushedEvents(pulledEvents)
      const events = getApp().consumePushedEvents()
      this.setData({ devices, events })
      getApp().refreshUnreadEventBadge()
      this.bindWebSocket(devices)
    } catch (err) {
      this.setData({ events: getApp().consumePushedEvents() })
    } finally {
      this.setData({ loading: false })
    }
  },
  bindWebSocket(devices) {
    websocket.connect()
    websocket.subscribeEvents(devices.map((item) => item.device_id || item.deviceId))
  },
  onRealtimeNewEvent(event) {
    const eventId = event.event_id || event.eventId
    const exists = this.data.events.some((item) => (item.event_id || item.eventId) === eventId)
    if (!exists) {
      this.setData({ events: [event].concat(this.data.events) })
    }
    getApp().consumePushedEvents()
  },
  onRealtimeDeviceStatus(status) {
    const devices = this.data.devices.map((item) => {
      const deviceId = item.device_id || item.deviceId
      if (deviceId === status.device_id) {
        return Object.assign({}, item, {
          online_status: status.online_status,
          last_online_time: status.last_online_time
        })
      }
      return item
    })
    this.setData({ devices })
  },
  openEvent(event) {
    wx.switchTab({ url: '/pages/events/index' })
  },
  async playAudio(event) {
    const target = event && event.detail
    const eventId = target && (target.event_id || target.eventId)
    if (!eventId) return
    const data = await eventApi.refreshUrl(eventId)
    const audio = wx.createInnerAudioContext()
    audio.src = data.file_url || data.fileUrl
    audio.play()
  }
})
