const deviceApi = require('../../services/device-api')

const tabSwipe = require('../../utils/tab-swipe')

Page(tabSwipe.withTabSwipe({
  data: {
    loading: false,
    initialized: false,
    navStyle: '',
    contentStyle: '',
    devices: [],
    keyword: '',
    search_active: false,
    bindVisible: false,
    detailVisible: false,
    currentDevice: null,
    bindForm: {},
    editForm: {},
    actionLoading: false,
    loadError: false,
    hasMore: false
  },
  onLoad() {
    getApp().setNavLayout(this)
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    this.reload()
  },
  async onPullDownRefresh() {
    try {
      await this.reload(true)
    } finally {
      wx.stopPullDownRefresh()
    }
  },
  onReachBottom() {
    this.loadMore()
  },
  async reload(force = false) {
    const requestId = (this.loadRequestId || 0) + 1
    this.loadRequestId = requestId
    this.setData({ loading: true, loadError: false })
    try {
      const data = this.data.keyword
        ? await deviceApi.searchAll({ keyword: this.data.keyword })
        : await getApp().getBoundDevices(force)
      if (requestId !== this.loadRequestId) return
      this.allDevices = data.records || []
      const devices = this.allDevices.slice(0, 30)
      this.setData({
        devices,
        hasMore: devices.length < this.allDevices.length,
        initialized: true
      })
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
  onRealtimeDeviceStatus(status) {
    const deviceId = status.device_id || status.deviceId
    this.allDevices = (this.allDevices || []).map((item) => {
      if ((item.device_id || item.deviceId) !== deviceId) return item
      return Object.assign({}, item, {
        online_status: status.online_status || status.onlineStatus,
        last_online_time: status.last_online_time || status.lastOnlineTime
      })
    })
    const index = this.data.devices.findIndex((item) => (item.device_id || item.deviceId) === deviceId)
    if (index < 0) return
    this.setData({
      [`devices[${index}].online_status`]: status.online_status || status.onlineStatus,
      [`devices[${index}].last_online_time`]: status.last_online_time || status.lastOnlineTime
    })
  },
  loadMore() {
    if (!this.data.hasMore) return
    const devices = (this.allDevices || []).slice(0, this.data.devices.length + 30)
    this.setData({
      devices,
      hasMore: devices.length < this.allDevices.length
    })
  },
  onKeyword(event) {
    const keyword = event.detail.value
    this.setData({
      keyword,
      search_active: true
    })
  },
  onSearchFocus() {
    this.setData({ search_active: true })
  },
  onSearchBlur() {
    this.setData({ search_active: !!this.data.keyword })
  },
  openBind() {
    this.setData({ bindVisible: true, bindForm: {} })
  },
  handleEmptyAction() {
    if (this.data.loadError) {
      this.reload()
      return
    }
    if (this.data.keyword) {
      this.setData({ keyword: '', search_active: false }, () => this.reload())
      return
    }
    this.openBind()
  },
  openDetail(event) {
    const device = event.detail
    this.setData({
      currentDevice: device,
      detailVisible: true,
      editForm: {
        device_name: device.device_name || device.deviceName || '',
        location: device.location || '',
        note: device.note || ''
      }
    })
  },
  closePanels() {
    if (this.data.actionLoading) return
    this.setData({ bindVisible: false, detailVisible: false })
  },
  onBindDeviceId(event) {
    this.setData({ 'bindForm.device_id': event.detail.value })
  },
  onBindCode(event) {
    this.setData({ 'bindForm.bind_code': event.detail.value })
  },
  onBindName(event) {
    this.setData({ 'bindForm.device_name': event.detail.value })
  },
  async submitBind() {
    if (this.data.actionLoading) return
    const form = {
      device_id: String(this.data.bindForm.device_id || '').trim(),
      bind_code: String(this.data.bindForm.bind_code || '').trim(),
      device_name: String(this.data.bindForm.device_name || '').trim()
    }
    if (!form.device_id || !form.bind_code) {
      wx.showToast({ title: '请填写设备编号和绑定码', icon: 'none' })
      return
    }
    this.setData({ actionLoading: true })
    try {
      await deviceApi.bind(form)
      wx.showToast({ title: '绑定成功' })
      this.setData({ bindVisible: false })
      getApp().invalidateBoundDevices()
      getApp().deferEnsureEventRealtime(true)
      this.reload(true)
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  onEditName(event) {
    this.setData({ 'editForm.device_name': event.detail.value })
  },
  onEditLocation(event) {
    this.setData({ 'editForm.location': event.detail.value })
  },
  onEditNote(event) {
    this.setData({ 'editForm.note': event.detail.value })
  },
  async submitEdit() {
    if (this.data.actionLoading) return
    this.setData({ actionLoading: true })
    const deviceId = this.data.currentDevice.device_id || this.data.currentDevice.deviceId
    try {
      await deviceApi.updateInfo(deviceId, this.data.editForm)
      wx.showToast({ title: '已保存' })
      this.setData({ detailVisible: false })
      getApp().invalidateBoundDevices()
      this.reload(true)
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  submitUnbind() {
    if (this.data.actionLoading) return
    const deviceId = this.data.currentDevice.device_id || this.data.currentDevice.deviceId
    const deviceName = this.data.currentDevice.device_name || this.data.currentDevice.deviceName || deviceId
    wx.showModal({
      title: '确认解绑设备',
      content: `解绑“${deviceName}”后，你将不再接收该设备的报警。`,
      confirmText: '确认解绑',
      confirmColor: '#d84f45',
      success: async (res) => {
        if (!res.confirm) return
        this.setData({ actionLoading: true })
        try {
          await deviceApi.unbind(deviceId)
          wx.showToast({ title: '已解绑' })
          this.setData({ detailVisible: false })
          getApp().invalidateBoundDevices()
          getApp().deferEnsureEventRealtime(true)
          this.reload(true)
        } finally {
          this.setData({ actionLoading: false })
        }
      }
    })
  }
}))
