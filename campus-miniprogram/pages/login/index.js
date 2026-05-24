const authApi = require('../../services/auth-api')
const userApi = require('../../services/user-api')

Page({
  data: {
    loading: false,
    avatarUrl: '',
    nickname: '',
    canSubmit: false,
    profileVisible: false,
    navStyle: '',
    contentStyle: '',
    modalStyle: '',
    dialogStyle: ''
  },
  onLoad() {
    this.setNavLayout()
  },
  setNavLayout() {
    const systemInfo = wx.getSystemInfoSync()
    const menuButton = typeof wx.getMenuButtonBoundingClientRect === 'function'
      ? wx.getMenuButtonBoundingClientRect()
      : {}
    const statusBarHeight = systemInfo.statusBarHeight || 0
    const menuTop = menuButton.top || statusBarHeight + 4
    const menuHeight = menuButton.height || 32
    const navHeight = menuTop + menuHeight + 10
    const titleTop = menuTop

    this.setData({
      navStyle: `height:${navHeight}px;padding-top:${titleTop}px;`,
      contentStyle: `padding-top:${navHeight + 28}px;`,
      modalStyle: `padding-top:${navHeight + 24}px;`,
      dialogStyle: `max-height:calc(100vh - ${navHeight + 96}px);`
    })
  },
  openProfileDialog() {
    if (this.data.loading) return
    this.login()
  },
  closeProfileDialog() {
    if (this.data.loading) return
    this.setData({ profileVisible: false })
  },
  onChooseAvatar(event) {
    const avatarUrl = event.detail.avatarUrl
    this.setData({
      avatarUrl,
      canSubmit: Boolean(avatarUrl && this.data.nickname.trim())
    })
  },
  onNickname(event) {
    const nickname = event.detail.value
    this.setData({
      nickname,
      canSubmit: Boolean(this.data.avatarUrl && nickname.trim())
    })
  },
  async submit() {
    if (!this.data.canSubmit) {
      wx.showToast({ title: '请先完成头像与微信名称填写', icon: 'none' })
      return
    }
    await this.saveProfileAndEnter()
  },
  async login() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const loginData = await authApi.loginByWxCode()
      const userInfo = loginData.user_info || loginData.userInfo || {}
      const isNewUser = userInfo.is_new_user || userInfo.isNewUser
      if (isNewUser) {
        this.setData({ profileVisible: true })
        return
      }
      wx.showToast({ title: '登录成功' })
      getApp().deferEnsureEventRealtime(true)
      wx.switchTab({ url: '/pages/home/index' })
    } catch (err) {
      wx.showToast({ title: '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  async saveProfileAndEnter() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const nickname = this.data.nickname && this.data.nickname.trim()
      if (nickname) {
        await userApi.updateMe({ nickname })
      }
      if (this.data.avatarUrl) {
        await userApi.uploadAvatar(this.data.avatarUrl)
      }
      wx.showToast({ title: '登录成功' })
      getApp().deferEnsureEventRealtime(true)
      wx.switchTab({ url: '/pages/home/index' })
    } catch (err) {
      wx.showToast({ title: '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  }
})
