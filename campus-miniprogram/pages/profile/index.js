const authApi = require('../../services/auth-api')
const userApi = require('../../services/user-api')
const storage = require('../../utils/storage')

Page({
  data: {
    loading: false,
    initialized: false,
    loadError: false,
    navStyle: '',
    contentStyle: '',
    form: {},
    roleLabel: '普通用户',
    actionLoading: false,
    formDirty: false
  },
  onLoad() {
    getApp().setNavLayout(this)
  },
  onShow() {
    getApp().setNavLayout(this)
    if (!storage.getAccessToken()) {
      wx.reLaunch({ url: '/pages/login/index' })
      return
    }
    getApp().deferEnsureEventRealtime()
    this.load()
  },
  async onPullDownRefresh() {
    try {
      await this.load(true)
    } finally {
      wx.stopPullDownRefresh()
    }
  },
  async load(force = false) {
    if (!force && this.data.formDirty) return
    if (!force && this.lastLoadedAt && Date.now() - this.lastLoadedAt < 15000) return
    this.setData({ loading: true, loadError: false })
    try {
      const user = await userApi.me()
      this.setData({
        form: user || {},
        roleLabel: this.roleText(user && user.role),
        formDirty: false,
        initialized: true
      })
      this.lastLoadedAt = Date.now()
    } catch (err) {
      this.setData({ loadError: true })
    } finally {
      this.setData({ loading: false })
    }
  },
  login() {
    this.endSession('切换账号', '切换账号后需要重新使用微信登录。')
  },
  roleText(role) {
    const labels = {
      USER: '普通用户',
      ADMIN: '管理员'
    }
    return labels[role] || '普通用户'
  },
  async saveProfile() {
    if (this.data.actionLoading) return
    if (!this.data.formDirty) {
      wx.showToast({ title: '资料没有变化', icon: 'none' })
      return
    }
    if (!this.validateProfile()) return
    this.setData({ actionLoading: true })
    const form = this.data.form
    try {
      await userApi.updateMe({
        nickname: form.nickname,
        phone: form.phone,
        email: form.email
      })
      wx.showToast({ title: '已保存' })
      this.load(true)
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  validateProfile() {
    const form = this.data.form || {}
    const nickname = String(form.nickname || '').trim()
    const phone = String(form.phone || '').trim()
    const email = String(form.email || '').trim()
    if (!nickname) {
      wx.showToast({ title: '昵称不能为空', icon: 'none' })
      return false
    }
    if (phone && !/^1\d{10}$/.test(phone)) {
      wx.showToast({ title: '请输入正确的手机号', icon: 'none' })
      return false
    }
    if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      wx.showToast({ title: '请输入正确的邮箱', icon: 'none' })
      return false
    }
    return true
  },
  chooseAvatar() {
    if (this.data.actionLoading) return
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      success: async (res) => {
        const filePath = res.tempFiles[0].tempFilePath
        this.setData({ actionLoading: true })
        try {
          await userApi.uploadAvatar(filePath)
          wx.showToast({ title: '已上传' })
          this.load(true)
        } finally {
          this.setData({ actionLoading: false })
        }
      }
    })
  },
  logout() {
    this.endSession('退出登录', '退出后将停止接收当前账号的实时报警。')
  },
  endSession(title, content) {
    if (this.data.actionLoading) return
    wx.showModal({
      title,
      content,
      confirmText: title,
      confirmColor: '#d84f45',
      success: async (res) => {
        if (!res.confirm) return
        this.setData({ actionLoading: true })
        try {
          await authApi.logout()
        } finally {
          getApp().redirectToLogin()
        }
      }
    })
  },
  onNickname(event) {
    this.setData({ 'form.nickname': event.detail.value, formDirty: true })
  },
  onPhone(event) {
    this.setData({ 'form.phone': event.detail.value, formDirty: true })
  },
  onEmail(event) {
    this.setData({ 'form.email': event.detail.value, formDirty: true })
  }
})
