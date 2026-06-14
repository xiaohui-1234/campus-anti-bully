Component({
  properties: {
    title: {
      type: String,
      value: '暂无数据'
    },
    desc: {
      type: String,
      value: '稍后再看看'
    },
    actionText: {
      type: String,
      value: ''
    }
  },
  methods: {
    handleAction() {
      this.triggerEvent('action')
    }
  }
})
