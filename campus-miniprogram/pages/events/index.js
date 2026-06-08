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
    events: [],
    page: 1,
    size: 10,
    hasMore: false,
    detailVisible: false,
    currentEvent: null,
    filtersExpanded: false,
    filters: {},
    devices: [],
    deviceOptions: ['全部设备'],
    deviceValues: [''],
    deviceLabel: '全部设备',
    sizeOptions: ['10', '20', '50', '100'],
    sizeLabel: '每页 10 条',
    readOptions: ['全部阅读状态', '未读', '已读'],
    readValues: ['', 'UNREAD', 'READ'],
    fileOptions: ['全部文件状态', '上传中', '上传成功', '上传失败'],
    fileValues: ['', 'UPLOADING', 'SUCCESS', 'FAILED'],
    pushOptions: ['全部推送状态', '待推送', '已推送', '推送失败'],
    pushValues: ['', 'PENDING', 'PUSHED', 'FAILED'],
    readLabel: '全部阅读状态',
    fileLabel: '全部文件状态',
    pushLabel: '全部推送状态',
    startDate: '',
    startTime: '',
    endDate: '',
    endTime: '',
    audioState: {
      event_id: '',
      playing: false,
      progress: 0
    }
  },
  onLoad(options) {
    getApp().setNavLayout(this)
    if (options.event_id) {
      this.setData({ filters: { keyword: options.event_id } })
    }
    this.loadDevices()
    this.reload()
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    getApp().refreshUnreadEventBadge()
    this.reload()
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
  async reload() {
    this.setData({ page: 1 })
    await this.load()
  },
  async loadMore() {
    if (!this.data.hasMore) return
    this.setData({ page: this.data.page + 1 })
    await this.load()
  },
  async load() {
    this.setData({ loading: true })
    try {
      const data = await eventApi.search(Object.assign({}, this.data.filters, {
        page: this.data.page,
        size: this.data.size
      }))
      const records = (data.records || []).map(eventLabels.formatEvent)
      const events = this.data.page === 1 ? records : this.data.events.concat(records)
      this.setData({
        events,
        hasMore: data.page * data.size < data.total
      })
    } finally {
      this.setData({ loading: false })
    }
  },
  onKeyword(event) {
    this.setData({ 'filters.keyword': event.detail.value })
  },
  toggleFilters() {
    this.setData({ filtersExpanded: !this.data.filtersExpanded })
  },
  onEventType(event) {
    this.setData({ 'filters.event_type': event.detail.value })
  },
  onDevice(event) {
    const index = Number(event.detail.value)
    const deviceId = this.data.deviceValues[index] || ''
    this.setData({
      deviceLabel: this.data.deviceOptions[index],
      'filters.device_id': deviceId
    })
  },
  onSize(event) {
    const size = Number(this.data.sizeOptions[event.detail.value])
    this.setData({
      size,
      sizeLabel: `每页 ${size} 条`
    }, () => this.reload())
  },
  onReadStatus(event) {
    const index = Number(event.detail.value)
    this.setData({
      readLabel: this.data.readOptions[index],
      'filters.read_status': this.data.readValues[index]
    })
  },
  onFileStatus(event) {
    const index = Number(event.detail.value)
    this.setData({
      fileLabel: this.data.fileOptions[index],
      'filters.file_status': this.data.fileValues[index]
    })
  },
  onPushStatus(event) {
    const index = Number(event.detail.value)
    this.setData({
      pushLabel: this.data.pushOptions[index],
      'filters.push_status': this.data.pushValues[index]
    })
  },
  onStartDate(event) {
    const startDate = event.detail.value
    this.setData({ startDate }, this.updateTimeRange)
  },
  onStartTime(event) {
    const startTime = event.detail.value
    this.setData({ startTime }, this.updateTimeRange)
  },
  onEndDate(event) {
    const endDate = event.detail.value
    this.setData({ endDate }, this.updateTimeRange)
  },
  onEndTime(event) {
    const endTime = event.detail.value
    this.setData({ endTime }, this.updateTimeRange)
  },
  updateTimeRange() {
    this.setData({
      'filters.start_time': this.composeDateTime(this.data.startDate, this.data.startTime, '00:00'),
      'filters.end_time': this.composeDateTime(this.data.endDate, this.data.endTime, '23:59')
    })
  },
  composeDateTime(date, time, fallbackTime) {
    if (!date) return ''
    return `${date} ${time || fallbackTime}:00`
  },
  resetFilters() {
    this.setData({
      filters: {},
      deviceLabel: '全部设备',
      readLabel: '全部阅读状态',
      fileLabel: '全部文件状态',
      pushLabel: '全部推送状态',
      startDate: '',
      startTime: '',
      endDate: '',
      endTime: '',
      size: 10,
      sizeLabel: '每页 10 条'
    }, () => this.reload())
  },
  async loadDevices() {
    try {
      const data = await deviceApi.list({ page: 1, size: 100 })
      const devices = data.records || []
      this.setData({
        devices,
        deviceOptions: ['全部设备'].concat(devices.map((item) => item.device_name || item.deviceName || item.device_id || item.deviceId)),
        deviceValues: [''].concat(devices.map((item) => item.device_id || item.deviceId))
      })
      this.bindWebSocket(devices)
    } catch (err) {
      this.setData({
        devices: [],
        deviceOptions: ['全部设备'],
        deviceValues: ['']
      })
    }
  },
  bindWebSocket(devices) {
    websocket.connect()
    websocket.subscribeEvents(devices.map((item) => item.device_id || item.deviceId))
  },
  onRealtimeNewEvent() {
    this.reload()
    getApp().refreshUnreadEventBadge()
  },
  onRealtimeDeviceStatus() {
    this.loadDevices()
  },
  openDetail(event) {
    this.setData({ currentEvent: event.detail, detailVisible: true })
  },
  closeDetail() {
    this.setData({ detailVisible: false, currentEvent: null })
  },
  async markRead() {
    const eventId = this.data.currentEvent.event_id || this.data.currentEvent.eventId
    await eventApi.markRead(eventId)
    wx.showToast({ title: '已读' })
    getApp().refreshUnreadEventBadge()
    this.closeDetail()
    this.reload()
  },
  deleteEvent() {
    const currentEvent = this.data.currentEvent
    const eventId = currentEvent && (currentEvent.event_id || currentEvent.eventId)
    if (!eventId) return
    wx.showModal({
      title: '删除事件',
      content: '删除后事件列表中将不再显示该报警记录。',
      confirmText: '删除',
      confirmColor: '#d84f45',
      success: async (res) => {
        if (!res.confirm) return
        await eventApi.remove(eventId)
        wx.showToast({ title: '已删除' })
        getApp().refreshUnreadEventBadge()
        this.closeDetail()
        this.reload()
      }
    })
  },
  async playAudio(event) {
    const target = event && event.detail ? event.detail : this.data.currentEvent
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
