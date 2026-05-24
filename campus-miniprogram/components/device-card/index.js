Component({
  properties: {
    device: {
      type: Object,
      value: {}
    }
  },
  observers: {
    device(value) {
      const online = (value.online_status || value.onlineStatus) === 'ONLINE'
      this.setData({
        onlineText: online ? '在线' : '离线',
        onlineClass: online ? 'online' : ''
      })
    }
  },
  data: {
    onlineText: '离线',
    onlineClass: ''
  },
  methods: {
    handleTap() {
      this.triggerEvent('open', this.properties.device)
    }
  }
})
