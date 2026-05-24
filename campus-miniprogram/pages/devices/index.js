const deviceApi = require('../../services/device-api')

Page({
  data: {
    loading: false,
    navStyle: '',
    contentStyle: '',
    devices: [],
    keyword: '',
    search_active: false,
    bindVisible: false,
    detailVisible: false,
    currentDevice: null,
    bindForm: {},
    editForm: {}
  },
  onLoad() {
    getApp().setNavLayout(this)
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    this.reload()
  },
  async reload() {
    this.setData({ loading: true })
    try {
      const data = await deviceApi.search({
        keyword: this.data.keyword,
        page: 1,
        size: 100
      })
      this.setData({ devices: data.records || [] })
    } finally {
      this.setData({ loading: false })
    }
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
  onRealtimeNewEvent() {
    getApp().refreshUnreadEventBadge()
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
    await deviceApi.bind(this.data.bindForm)
    wx.showToast({ title: '绑定成功' })
    this.closePanels()
    getApp().deferEnsureEventRealtime(true)
    this.reload()
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
    const deviceId = this.data.currentDevice.device_id || this.data.currentDevice.deviceId
    await deviceApi.updateInfo(deviceId, this.data.editForm)
    wx.showToast({ title: '已保存' })
    this.closePanels()
    this.reload()
  },
  async submitUnbind() {
    const deviceId = this.data.currentDevice.device_id || this.data.currentDevice.deviceId
    await deviceApi.unbind(deviceId)
    wx.showToast({ title: '已解绑' })
    this.closePanels()
    getApp().deferEnsureEventRealtime(true)
    this.reload()
  }
})
