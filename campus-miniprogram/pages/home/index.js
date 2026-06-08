const deviceApi = require('../../services/device-api')
const eventApi = require('../../services/event-api')
const websocket = require('../../services/websocket')
const audioPlayer = require('../../utils/audio-player')
const eventLabels = require('../../utils/event-labels')

Page({
  data: {
    loading: false,
    navStyle: '',
    contentStyle: '',
    devices: [],
    onlineTotal: 0,
    events: [],
    audioState: {
      event_id: '',
      playing: false,
      progress: 0
    }
  },
  onLoad() {
    getApp().setNavLayout(this)
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    this.bootstrap()
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
  async bootstrap() {
    this.setData({ loading: true })
    try {
      const cachedEvents = getApp().getPushedEvents()
      if (cachedEvents.length) {
        this.setData({ events: cachedEvents.map(eventLabels.formatEvent) })
      }
      const devicePage = await deviceApi.list({ page: 1, size: 100 })
      const devices = devicePage.records || []
      const pulledEvents = await eventApi.unpulled()
      getApp().setPushedEvents(pulledEvents)
      const events = getApp().consumePushedEvents().map(eventLabels.formatEvent)
      this.setData({
        devices,
        onlineTotal: this.countOnlineDevices(devices),
        events
      })
      getApp().refreshUnreadEventBadge()
      this.bindWebSocket(devices)
    } catch (err) {
      this.setData({ events: getApp().consumePushedEvents().map(eventLabels.formatEvent) })
    } finally {
      this.setData({ loading: false })
    }
  },
  bindWebSocket(devices) {
    websocket.connect()
    websocket.subscribeEvents(devices.map((item) => item.device_id || item.deviceId))
  },
  countOnlineDevices(devices) {
    return devices.filter((item) => (item.online_status || item.onlineStatus) === 'ONLINE').length
  },
  onRealtimeNewEvent(event) {
    const eventId = event.event_id || event.eventId
    const exists = this.data.events.some((item) => (item.event_id || item.eventId) === eventId)
    if (!exists) {
      this.setData({ events: [eventLabels.formatEvent(event)].concat(this.data.events) })
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
    this.setData({
      devices,
      onlineTotal: this.countOnlineDevices(devices)
    })
  },
  openEvent(event) {
    wx.switchTab({ url: '/pages/events/index' })
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
      throw err
    }
    if (this.pendingAudioEventId !== eventId) return
    this.pendingAudioEventId = ''
    audioPlayer.play(data.file_url || data.fileUrl, {
      event_id: eventId,
      onState: (state) => this.updateAudioState(state)
    })
  }
})
