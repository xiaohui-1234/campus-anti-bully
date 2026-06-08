Component({
  properties: {
    event: {
      type: Object,
      value: {}
    },
    audioState: {
      type: Object,
      value: {}
    }
  },
  observers: {
    'event,audioState': function(event, audioState) {
      const readStatus = event.read_status || event.readStatus
      const fileStatus = event.file_status || event.fileStatus
      const eventId = event.event_id || event.eventId
      const isPlaying = !!(audioState && audioState.playing && audioState.event_id === eventId)
      const progress = isPlaying ? Math.max(0, Math.min(100, Number(audioState.progress) || 0)) : 0
      this.setData({
        readText: readStatus === 'READ' ? '已读' : '未读',
        fileText: this.fileStatusText(fileStatus),
        canPlay: fileStatus === 'SUCCESS',
        isPlaying,
        playProgress: progress,
        progressStyle: `width: ${progress}%`,
        playText: isPlaying ? '停止播放' : '播放语音'
      })
    }
  },
  data: {
    readText: '未读',
    fileText: '上传成功',
    canPlay: false,
    isPlaying: false,
    playProgress: 0,
    progressStyle: 'width: 0%',
    playText: '播放语音'
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
