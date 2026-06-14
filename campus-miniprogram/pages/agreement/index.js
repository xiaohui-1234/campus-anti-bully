Page({
  data: {
    type: 'user',
    title: '用户协议'
  },
  onLoad(options) {
    const type = options.type === 'privacy' ? 'privacy' : 'user'
    const title = type === 'privacy' ? '隐私政策' : '用户协议'
    this.setData({ type, title })
    wx.setNavigationBarTitle({ title })
  }
})
