Component({
  properties: {
    event: {
      type: Object,
      value: {}
    }
  },
  observers: {
    event(value) {
      const readStatus = value.read_status || value.readStatus
      const fileStatus = value.file_status || value.fileStatus
      this.setData({
        readText: readStatus === 'READ' ? '已读' : '未读',
        fileText: this.fileStatusText(fileStatus),
        canPlay: fileStatus === 'SUCCESS'
      })
    }
  },
  data: {
    readText: '未读',
    fileText: '上传成功',
    canPlay: false
  },
  methods: {
    fileStatusText(status) {
      const labels = {
        UPLOADING: '上传中',
        SUCCESS: '上传成功',
        FAILED: '上传失败'
      }
      return labels[status] || '上传成功'
    },
    handleTap() {
      this.triggerEvent('open', this.properties.event)
    },
    handlePlay() {
      if (!this.data.canPlay) {
        wx.showToast({ title: this.data.fileText, icon: 'none' })
        return
      }
      this.triggerEvent('play', this.properties.event)
    }
  }
})
