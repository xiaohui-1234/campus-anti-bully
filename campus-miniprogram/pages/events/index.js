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
    events: [],
    page: 1,
    size: 10,
    total: 0,
    pageTotal: 1,
    pageItems: [{ key: 'page-1', page: 1, label: '1', active: true }],
    pageInput: '',
    detailVisible: false,
    currentEvent: null,
    filtersExpanded: false,
    filters: {},
    devices: [],
    deviceOptions: ['全部设备'],
    deviceValues: [''],
    deviceLabel: '全部设备',
    eventTypeOptions: ['全部事件类型', '按钮报警', '声音报警', '打架报警', '求助报警', '紧急求助'],
    eventTypeValues: ['', 'BUTTON', 'VOICE', 'FIGHT', 'HELP', 'SOS'],
    eventTypeLabel: '全部事件类型',
    readOptions: ['全部阅读状态', '未读', '已读'],
    readValues: ['', 'UNREAD', 'READ'],
    fileOptions: ['全部录音状态', '上传中', '可播放', '上传失败'],
    fileValues: ['', 'UPLOADING', 'SUCCESS', 'FAILED'],
    pushOptions: ['全部通知状态', '待通知', '已通知', '通知失败'],
    pushValues: ['', 'PENDING', 'PUSHED', 'FAILED'],
    readLabel: '全部阅读状态',
    fileLabel: '全部录音状态',
    pushLabel: '全部通知状态',
    startDate: '',
    startTime: '',
    endDate: '',
    endTime: '',
    actionLoading: false,
    filtersActive: false,
    activeFilterCount: 0,
    loadError: false,
    realtimeHintCount: 0,
    audioState: {
      event_id: '',
      playing: false,
      progress: 0
    }
  },
  onLoad(options) {
    getApp().setNavLayout(this)
    if (options.event_id) {
      this.pendingEventId = options.event_id
    }
    this.loadDevices()
  },
  onShow() {
    this.pageVisible = true
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    if (this.applyListPreset()) return
    this.refreshCurrentPage()
  },
  async onPullDownRefresh() {
    try {
      await Promise.all([this.refreshCurrentPage(), this.loadDevices(true)])
    } finally {
      wx.stopPullDownRefresh()
    }
  },
  onHide() {
    this.pageVisible = false
    clearTimeout(this.realtimeReloadTimer)
    clearTimeout(this.keywordTimer)
    this.pendingAudioEventId = ''
    audioPlayer.stop()
  },
  onUnload() {
    clearTimeout(this.realtimeReloadTimer)
    clearTimeout(this.deviceReloadTimer)
    clearTimeout(this.keywordTimer)
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
    if (!this.validateTimeRange()) return
    const requestId = (this.loadRequestId || 0) + 1
    this.loadRequestId = requestId
    this.setData({
      page: 1,
      loading: true,
      loadError: false,
      filtersActive: this.hasActiveFilters()
    })
    try {
      await this.load(requestId, 1)
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
  async changePage(targetPage) {
    const page = Number(targetPage)
    if (this.data.loading || page < 1 || page > this.data.pageTotal || page === this.data.page) return
    return this.requestPage(page, true)
  },
  async refreshCurrentPage() {
    if (!this.validateTimeRange() || this.data.loading) return
    return this.requestPage(this.data.page, false)
  },
  async requestPage(page, scrollToTop) {
    const requestId = (this.loadRequestId || 0) + 1
    this.loadRequestId = requestId
    this.setData({ loading: true, loadError: false })
    try {
      await this.load(requestId, page)
      if (scrollToTop) {
        wx.pageScrollTo({
          scrollTop: 0,
          duration: 180,
          fail() {}
        })
      }
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
  previousPage() {
    return this.changePage(this.data.page - 1)
  },
  nextPage() {
    return this.changePage(this.data.page + 1)
  },
  tapPage(event) {
    return this.changePage(Number(event.currentTarget.dataset.page))
  },
  onPageInput(event) {
    this.setData({ pageInput: String(event.detail.value || '').replace(/\D/g, '') })
  },
  jumpToPage() {
    if (this.data.loading) return
    const page = Number(this.data.pageInput)
    if (!Number.isInteger(page) || page < 1 || page > this.data.pageTotal) {
      wx.showToast({ title: `请输入 1-${this.data.pageTotal} 页`, icon: 'none' })
      return
    }
    if (page === this.data.page) {
      this.setData({ pageInput: '' })
      return new Promise((resolve) => {
        wx.pageScrollTo({
          scrollTop: 0,
          duration: 180,
          success: resolve,
          fail: resolve
        })
      })
    }
    this.setData({ pageInput: '' })
    return this.changePage(page)
  },
  async load(requestId, page) {
    const data = await eventApi.search(Object.assign({}, this.data.filters, {
      page,
      size: this.data.size
    }))
    if (requestId !== this.loadRequestId) return
    const total = Number(data.total) || 0
    const pageSize = Number(data.size) || this.data.size
    const pageTotal = Math.max(1, Math.ceil(total / pageSize))
    if (page > pageTotal) {
      return this.load(requestId, pageTotal)
    }
    const updates = {
      page,
      total,
      pageTotal,
      initialized: true,
      realtimeHintCount: page === 1 ? 0 : this.data.realtimeHintCount,
      pageItems: this.buildPageItems(page, pageTotal),
      events: (data.records || []).map((item) => this.formatEvent(item))
    }
    this.setData(updates)
    await this.openPendingEvent()
  },
  buildPageItems(page, pageTotal) {
    const pages = pageTotal <= 7
      ? Array.from({ length: pageTotal }, (_, index) => index + 1)
      : Array.from(new Set([1, page - 1, page, page + 1, pageTotal]))
        .filter((item) => item >= 1 && item <= pageTotal)
        .sort((left, right) => left - right)
    const items = []
    pages.forEach((item, index) => {
      if (index > 0 && item - pages[index - 1] > 1) {
        items.push({ key: `ellipsis-${item}`, ellipsis: true, label: '...' })
      }
      items.push({
        key: `page-${item}`,
        page: item,
        label: String(item),
        active: item === page
      })
    })
    return items
  },
  hasActiveFilters() {
    return Object.keys(this.data.filters).some((key) => this.data.filters[key] !== '')
  },
  activeFilterCount(filters = this.data.filters) {
    return Object.keys(filters || {}).filter((key) => filters[key] !== undefined && filters[key] !== '').length
  },
  syncFilterState(filters = this.data.filters) {
    const count = this.activeFilterCount(filters)
    this.setData({
      filtersActive: count > 0,
      activeFilterCount: count
    })
  },
  applyListPreset() {
    const preset = getApp().consumeEventListPreset()
    if (!preset) return false
    const filters = Object.assign({}, preset.filters || {})
    const labels = preset.labels || {}
    this.setData({
      filters,
      deviceLabel: labels.deviceLabel || '全部设备',
      eventTypeLabel: labels.eventTypeLabel || '全部事件类型',
      readLabel: labels.readLabel || (filters.read_status === 'UNREAD' ? '未读' : '全部阅读状态'),
      fileLabel: labels.fileLabel || '全部录音状态',
      pushLabel: labels.pushLabel || '全部通知状态',
      startDate: '',
      startTime: '',
      endDate: '',
      endTime: '',
      filtersExpanded: !!preset.expand,
      page: 1
    }, () => {
      this.syncFilterState(filters)
      this.reload()
    })
    return true
  },
  validateTimeRange() {
    const start = this.data.filters.start_time
    const end = this.data.filters.end_time
    if (start && end && start > end) {
      wx.showToast({ title: '开始时间不能晚于结束时间', icon: 'none' })
      return false
    }
    return true
  },
  onKeyword(event) {
    const filters = Object.assign({}, this.data.filters, { keyword: event.detail.value })
    this.setData({ filters })
    this.syncFilterState(filters)
    clearTimeout(this.keywordTimer)
    this.keywordTimer = setTimeout(() => this.reload(), 500)
  },
  clearKeyword() {
    clearTimeout(this.keywordTimer)
    const filters = Object.assign({}, this.data.filters, { keyword: '' })
    this.setData({ filters }, () => {
      this.syncFilterState(filters)
      this.reload()
    })
  },
  toggleFilters() {
    this.setData({ filtersExpanded: !this.data.filtersExpanded })
  },
  onEventType(event) {
    const index = Number(event.detail.value)
    this.setData({
      eventTypeLabel: this.data.eventTypeOptions[index],
      'filters.event_type': this.data.eventTypeValues[index]
    }, () => this.syncFilterState())
  },
  onDevice(event) {
    const index = Number(event.detail.value)
    const deviceId = this.data.deviceValues[index] || ''
    this.setData({
      deviceLabel: this.data.deviceOptions[index],
      'filters.device_id': deviceId
    }, () => this.syncFilterState())
  },
  onReadStatus(event) {
    const index = Number(event.detail.value)
    this.setData({
      readLabel: this.data.readOptions[index],
      'filters.read_status': this.data.readValues[index]
    }, () => this.syncFilterState())
  },
  onFileStatus(event) {
    const index = Number(event.detail.value)
    this.setData({
      fileLabel: this.data.fileOptions[index],
      'filters.file_status': this.data.fileValues[index]
    }, () => this.syncFilterState())
  },
  onPushStatus(event) {
    const index = Number(event.detail.value)
    this.setData({
      pushLabel: this.data.pushOptions[index],
      'filters.push_status': this.data.pushValues[index]
    }, () => this.syncFilterState())
  },
  onStartDate(event) {
    const startDate = event.detail.value
    this.setData({ startDate }, this.updateTimeRange)
  },
  onStartTime(event) {
    if (!this.data.startDate) {
      wx.showToast({ title: '请先选择开始日期', icon: 'none' })
      return
    }
    const startTime = event.detail.value
    this.setData({ startTime }, this.updateTimeRange)
  },
  onEndDate(event) {
    const endDate = event.detail.value
    this.setData({ endDate }, this.updateTimeRange)
  },
  onEndTime(event) {
    if (!this.data.endDate) {
      wx.showToast({ title: '请先选择结束日期', icon: 'none' })
      return
    }
    const endTime = event.detail.value
    this.setData({ endTime }, this.updateTimeRange)
  },
  updateTimeRange() {
    const filters = Object.assign({}, this.data.filters, {
      start_time: this.composeDateTime(this.data.startDate, this.data.startTime, '00:00'),
      end_time: this.composeDateTime(this.data.endDate, this.data.endTime, '23:59')
    })
    this.setData({ filters }, () => {
      this.syncFilterState(filters)
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
      eventTypeLabel: '全部事件类型',
      readLabel: '全部阅读状态',
      fileLabel: '全部录音状态',
      pushLabel: '全部通知状态',
      startDate: '',
      startTime: '',
      endDate: '',
      endTime: '',
      size: 10,
      filtersActive: false,
      activeFilterCount: 0
    }, () => this.reload())
  },
  async loadDevices(force = false) {
    try {
      const data = await getApp().getBoundDevices(force)
      const devices = data.records || []
      this.setData({
        devices,
        deviceOptions: ['全部设备'].concat(devices.map((item) => item.device_name || item.deviceName || item.device_id || item.deviceId)),
        deviceValues: [''].concat(devices.map((item) => item.device_id || item.deviceId)),
        events: this.data.events.map((item) => this.formatEvent(item, devices))
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
    if (this.pageVisible) {
      this.setData({ realtimeHintCount: this.data.realtimeHintCount + 1 })
    }
  },
  refreshRealtimeEvents() {
    this.setData({ page: 1, realtimeHintCount: 0 }, () => this.reload())
  },
  handleEmptyAction() {
    if (this.data.loadError) {
      this.reload()
      return
    }
    this.resetFilters()
  },
  scheduleReload() {
    clearTimeout(this.realtimeReloadTimer)
    this.realtimeReloadTimer = setTimeout(() => this.refreshCurrentPage(), 500)
  },
  openDetail(event) {
    this.setData({ currentEvent: this.formatDetailEvent(event.detail), detailVisible: true })
  },
  formatDetailEvent(event) {
    return this.formatEvent(event)
  },
  formatEvent(event, devices = this.data.devices) {
    const value = eventLabels.formatEvent(event)
    const deviceId = value.device_id || value.deviceId
    const device = devices.find((item) => (item.device_id || item.deviceId) === deviceId)
    return Object.assign({}, value, {
      device_name_text: device && (device.device_name || device.deviceName) || deviceId || '未知设备'
    })
  },
  async openPendingEvent() {
    let pendingEvent = getApp().consumePendingEvent()
    const pendingId = pendingEvent && (pendingEvent.event_id || pendingEvent.eventId) || this.pendingEventId
    if (!pendingId) return
    this.pendingEventId = ''
    const matched = this.data.events.find((item) => (item.event_id || item.eventId) === pendingId)
    if (!matched) {
      try {
        pendingEvent = await eventApi.get(pendingId, { showError: false })
      } catch (err) {
        wx.showToast({ title: '事件不存在或无权访问', icon: 'none' })
        return
      }
    }
    this.setData({
      currentEvent: this.formatDetailEvent(matched || pendingEvent),
      detailVisible: true
    })
  },
  closeDetail() {
    this.setData({ detailVisible: false, currentEvent: null })
  },
  async markRead() {
    if (this.data.actionLoading || !this.data.currentEvent) return
    const readStatus = this.data.currentEvent.read_status || this.data.currentEvent.readStatus
    if (readStatus === 'READ') {
      this.closeDetail()
      return
    }
    this.setData({ actionLoading: true })
    const eventId = this.data.currentEvent.event_id || this.data.currentEvent.eventId
    try {
      await eventApi.markRead(eventId)
      wx.showToast({ title: '已读' })
      getApp().refreshUnreadEventBadge(true)
      this.closeDetail()
      this.refreshCurrentPage()
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  deleteEvent() {
    if (this.data.actionLoading) return
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
        this.setData({ actionLoading: true })
        try {
          await eventApi.remove(eventId)
          wx.showToast({ title: '已删除' })
          getApp().refreshUnreadEventBadge(true)
          this.closeDetail()
          this.refreshCurrentPage()
        } finally {
          this.setData({ actionLoading: false })
        }
      }
    })
  },
  async playAudio(event) {
    const target = event && event.detail ? event.detail : this.data.currentEvent
    const eventId = target && (target.event_id || target.eventId)
    if (!eventId) return
    const fileStatus = target.file_status || target.fileStatus
    if (fileStatus !== 'SUCCESS') {
      wx.showToast({ title: target.file_status_text || '录音暂不可播放', icon: 'none' })
      return
    }
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
