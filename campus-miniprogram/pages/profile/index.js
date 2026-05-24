const authApi = require('../../services/auth-api')
const userApi = require('../../services/user-api')

Page({
  data: {
    loading: false,
    navStyle: '',
    contentStyle: '',
    form: {},
    roleLabel: '普通用户'
  },
  onLoad() {
    getApp().setNavLayout(this)
  },
  onShow() {
    getApp().setNavLayout(this)
    getApp().deferEnsureEventRealtime()
    this.load()
  },
  async load() {
    this.setData({ loading: true })
    try {
      const user = await userApi.me()
      this.setData({
        form: user || {},
        roleLabel: this.roleText(user && user.role)
      })
    } catch (err) {
      this.setData({ form: {}, roleLabel: '普通用户' })
    } finally {
      this.setData({ loading: false })
    }
  },
  login() {
    wx.navigateTo({ url: '/pages/login/index' })
  },
  roleText(role) {
    const labels = {
      USER: '普通用户',
      ADMIN: '管理员'
    }
    return labels[role] || '普通用户'
  },
  async saveProfile() {
    const form = this.data.form
    await userApi.updateMe({
      nickname: form.nickname,
      phone: form.phone,
      email: form.email
    })
    wx.showToast({ title: '已保存' })
    this.load()
  },
  chooseAvatar() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      success: async (res) => {
        const filePath = res.tempFiles[0].tempFilePath
        await userApi.uploadAvatar(filePath)
        wx.showToast({ title: '已上传' })
        this.load()
      }
    })
  },
  async logout() {
    await authApi.logout()
    this.setData({ form: {} })
    wx.showToast({ title: '已退出' })
  },
  onNickname(event) {
    this.setData({ 'form.nickname': event.detail.value })
  },
  onPhone(event) {
    this.setData({ 'form.phone': event.detail.value })
  },
  onEmail(event) {
    this.setData({ 'form.email': event.detail.value })
  }
})
