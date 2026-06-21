const authApi = require('../../services/auth-api')
const userApi = require('../../services/user-api')
const storage = require('../../utils/storage')

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
const CODE_SECONDS = 60

Page({
  data: {
    loading: false,
    initialized: false,
    loadError: false,
    actionLoading: false,
    navStyle: '',
    contentStyle: '',
    activeSection: 'email',
    passwordResetVisible: false,
    user: {},
    security: {
      user_id: '',
      security_email_masked: '',
      security_email_verified: false,
      password_enabled: false
    },
    needsActivation: false,
    hasPartialSecurity: false,
    countdowns: {
      activate: 0,
      old_email: 0,
      new_email: 0,
      reset_password: 0
    },
    activateForm: {
      security_email: '',
      verify_code: '',
      password: '',
      confirm_password: ''
    },
    emailForm: {
      old_verify_code: '',
      new_security_email: '',
      new_verify_code: '',
      change_ticket: '',
      ticket_expires_in: 0,
      old_verified: false
    },
    passwordForm: {
      old_password: '',
      new_password: '',
      confirm_password: ''
    },
    passwordResetForm: {
      security_email: '',
      verify_code: '',
      new_password: '',
      confirm_password: ''
    }
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
    this.load()
  },
  onUnload() {
    this.clearCountdowns()
  },
  async onPullDownRefresh() {
    try {
      await this.load(true)
    } finally {
      wx.stopPullDownRefresh()
    }
  },
  async load(force = false) {
    if (!force && this.lastLoadedAt && Date.now() - this.lastLoadedAt < 10000) return
    this.setData({ loading: true, loadError: false })
    try {
      const user = await userApi.me()
      const security = this.normalizeSecurity(user || {})
      const hasEmail = Boolean(security.security_email_masked && security.security_email_verified)
      const hasPassword = Boolean(security.password_enabled)
      this.setData({
        user: user || {},
        security,
        needsActivation: !hasEmail && !hasPassword,
        hasPartialSecurity: hasEmail !== hasPassword,
        initialized: true
      })
      this.lastLoadedAt = Date.now()
    } catch (err) {
      this.setData({ loadError: true })
    } finally {
      this.setData({ loading: false })
    }
  },
  normalizeSecurity(user) {
    return {
      user_id: user.user_id || user.userId || '',
      security_email_masked: user.security_email_masked || user.securityEmailMasked || '',
      security_email_verified: Boolean(user.security_email_verified || user.securityEmailVerified),
      password_enabled: Boolean(user.password_enabled || user.passwordEnabled)
    }
  },
  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack()
      return
    }
    wx.switchTab({ url: '/pages/profile/index' })
  },
  switchSection(event) {
    const section = event.currentTarget.dataset.section
    if (!section || section === this.data.activeSection) return
    this.setData({ activeSection: section })
  },
  showPasswordReset() {
    if (!this.data.security.security_email_verified || !this.data.security.password_enabled) {
      wx.showToast({ title: '当前账号暂不能通过邮箱重置密码', icon: 'none' })
      return
    }
    this.setData({ passwordResetVisible: true })
  },
  hidePasswordReset() {
    this.setData({ passwordResetVisible: false })
  },
  onActivateEmail(event) {
    this.setData({ 'activateForm.security_email': event.detail.value })
  },
  onActivateCode(event) {
    this.setData({ 'activateForm.verify_code': event.detail.value })
  },
  onActivatePassword(event) {
    this.setData({ 'activateForm.password': event.detail.value })
  },
  onActivateConfirm(event) {
    this.setData({ 'activateForm.confirm_password': event.detail.value })
  },
  onOldEmailCode(event) {
    this.setData({ 'emailForm.old_verify_code': event.detail.value })
  },
  onNewEmail(event) {
    this.setData({ 'emailForm.new_security_email': event.detail.value })
  },
  onNewEmailCode(event) {
    this.setData({ 'emailForm.new_verify_code': event.detail.value })
  },
  onOldPassword(event) {
    this.setData({ 'passwordForm.old_password': event.detail.value })
  },
  onNewPassword(event) {
    this.setData({ 'passwordForm.new_password': event.detail.value })
  },
  onConfirmPassword(event) {
    this.setData({ 'passwordForm.confirm_password': event.detail.value })
  },
  onResetEmail(event) {
    this.setData({ 'passwordResetForm.security_email': event.detail.value })
  },
  onResetCode(event) {
    this.setData({ 'passwordResetForm.verify_code': event.detail.value })
  },
  onResetPassword(event) {
    this.setData({ 'passwordResetForm.new_password': event.detail.value })
  },
  onResetConfirm(event) {
    this.setData({ 'passwordResetForm.confirm_password': event.detail.value })
  },
  async sendActivateCode() {
    if (this.data.actionLoading || this.data.countdowns.activate > 0) return
    const email = this.data.activateForm.security_email.trim()
    if (!this.validateEmail(email)) return
    this.setData({ actionLoading: true })
    try {
      await authApi.sendEmailCode({
        scene: 'ACTIVATE_ACCOUNT',
        security_email: email
      })
      this.startCountdown('activate')
      wx.showToast({ title: '验证码已发送', icon: 'none' })
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async activateAccount() {
    if (this.data.actionLoading) return
    const form = this.trimForm(this.data.activateForm)
    if (!this.validateEmail(form.security_email)) return
    if (!this.validateCode(form.verify_code)) return
    if (!this.validatePasswordPair(form.password, form.confirm_password)) return
    this.setData({ actionLoading: true })
    try {
      await authApi.activateAccount({
        security_email: form.security_email,
        verify_code: form.verify_code,
        password: form.password
      })
      wx.showToast({ title: '已完成设置' })
      this.clearCountdown('activate')
      this.setData({
        'countdowns.activate': 0,
        activateForm: {
          security_email: '',
          verify_code: '',
          password: '',
          confirm_password: ''
        }
      })
      this.lastLoadedAt = 0
      await this.load(true)
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async sendOldEmailCode() {
    if (this.data.actionLoading || this.data.countdowns.old_email > 0) return
    if (this.data.emailForm.old_verified) return
    if (!this.data.security.security_email_verified) {
      wx.showToast({ title: '请先设置安全邮箱', icon: 'none' })
      return
    }
    this.setData({ actionLoading: true })
    try {
      await authApi.sendEmailCode({ scene: 'CHANGE_SECURITY_EMAIL_OLD' })
      this.startCountdown('old_email')
      wx.showToast({ title: '验证码已发送', icon: 'none' })
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async verifyOldEmail() {
    if (this.data.actionLoading) return
    if (this.data.emailForm.old_verified) return
    const oldCode = this.data.emailForm.old_verify_code.trim()
    if (!this.validateCode(oldCode)) return
    this.setData({ actionLoading: true })
    try {
      const ticket = await authApi.verifyOldSecurityEmail({ old_verify_code: oldCode })
      this.setData({
        'emailForm.change_ticket': ticket.change_ticket || ticket.changeTicket || '',
        'emailForm.ticket_expires_in': ticket.expires_in || ticket.expiresIn || 0,
        'emailForm.old_verified': true
      })
      wx.showToast({ title: '旧邮箱已验证', icon: 'none' })
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async sendNewEmailCode() {
    if (this.data.actionLoading || this.data.countdowns.new_email > 0) return
    const form = this.trimForm(this.data.emailForm)
    if (!form.change_ticket || !form.old_verified) {
      wx.showToast({ title: '请先验证旧邮箱', icon: 'none' })
      return
    }
    if (!this.validateEmail(form.new_security_email)) return
    this.setData({ actionLoading: true })
    try {
      await authApi.sendEmailCode({
        scene: 'CHANGE_SECURITY_EMAIL_NEW',
        security_email: form.new_security_email,
        change_ticket: form.change_ticket
      })
      this.startCountdown('new_email')
      wx.showToast({ title: '验证码已发送', icon: 'none' })
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async confirmEmailChange() {
    if (this.data.actionLoading) return
    const form = this.trimForm(this.data.emailForm)
    if (!form.change_ticket || !form.old_verified) {
      wx.showToast({ title: '请先验证旧邮箱', icon: 'none' })
      return
    }
    if (!this.validateEmail(form.new_security_email)) return
    if (!this.validateCode(form.new_verify_code)) return
    this.setData({ actionLoading: true })
    try {
      await authApi.confirmSecurityEmailChange({
        change_ticket: form.change_ticket,
        new_security_email: form.new_security_email,
        new_verify_code: form.new_verify_code
      })
      wx.showToast({ title: '安全邮箱已修改' })
      this.clearCountdown('old_email')
      this.clearCountdown('new_email')
      this.setData({
        'countdowns.old_email': 0,
        'countdowns.new_email': 0,
        emailForm: {
          old_verify_code: '',
          new_security_email: '',
          new_verify_code: '',
          change_ticket: '',
          ticket_expires_in: 0,
          old_verified: false
        }
      })
      this.lastLoadedAt = 0
      await this.load(true)
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async changePassword() {
    if (this.data.actionLoading) return
    if (!this.data.security.password_enabled) {
      wx.showToast({ title: '当前账号未启用密码登录', icon: 'none' })
      return
    }
    const form = this.trimForm(this.data.passwordForm)
    if (!form.old_password) {
      wx.showToast({ title: '请输入原密码', icon: 'none' })
      return
    }
    if (!this.validatePasswordPair(form.new_password, form.confirm_password)) return
    if (form.old_password === form.new_password) {
      wx.showToast({ title: '新密码不能与原密码相同', icon: 'none' })
      return
    }
    this.setData({ actionLoading: true })
    try {
      await authApi.changePassword({
        old_password: form.old_password,
        new_password: form.new_password
      }, {
        showError: false
      })
      this.setData({
        passwordForm: {
          old_password: '',
          new_password: '',
          confirm_password: ''
        }
      })
      wx.showModal({
        title: '密码已修改',
        content: '为了保证账号安全，请重新登录。',
        confirmText: '重新登录',
        showCancel: false,
        success: () => {
          getApp().redirectToLogin()
        }
      })
    } catch (err) {
      wx.showToast({ title: this.passwordErrorMessage(err), icon: 'none' })
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async sendResetPasswordCode() {
    if (this.data.actionLoading || this.data.countdowns.reset_password > 0) return
    if (!this.data.security.security_email_verified || !this.data.security.password_enabled) {
      wx.showToast({ title: '当前账号暂不能通过邮箱重置密码', icon: 'none' })
      return
    }
    const email = this.data.passwordResetForm.security_email.trim()
    if (!this.validateEmail(email)) return
    this.setData({ actionLoading: true })
    try {
      await authApi.sendEmailCode({
        scene: 'RESET_PASSWORD',
        security_email: email
      })
      this.startCountdown('reset_password')
      wx.showToast({ title: '若邮箱匹配，验证码将发送', icon: 'none' })
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  async resetPasswordByEmail() {
    if (this.data.actionLoading) return
    if (!this.data.security.security_email_verified || !this.data.security.password_enabled) {
      wx.showToast({ title: '当前账号暂不能通过邮箱重置密码', icon: 'none' })
      return
    }
    const form = this.trimForm(this.data.passwordResetForm)
    if (!this.validateEmail(form.security_email)) return
    if (!this.validateCode(form.verify_code)) return
    if (!this.validatePasswordPair(form.new_password, form.confirm_password)) return
    this.setData({ actionLoading: true })
    try {
      await authApi.resetPassword({
        security_email: form.security_email,
        verify_code: form.verify_code,
        new_password: form.new_password
      }, {
        showError: false
      })
      this.clearCountdown('reset_password')
      this.setData({
        'countdowns.reset_password': 0,
        passwordResetForm: {
          security_email: '',
          verify_code: '',
          new_password: '',
          confirm_password: ''
        }
      })
      wx.showModal({
        title: '密码已重置',
        content: '为了保证账号安全，请重新登录。',
        confirmText: '重新登录',
        showCancel: false,
        success: () => {
          getApp().redirectToLogin()
        }
      })
    } catch (err) {
      wx.showToast({ title: this.passwordResetErrorMessage(err), icon: 'none' })
    } finally {
      this.setData({ actionLoading: false })
    }
  },
  passwordErrorMessage(err) {
    const message = err && err.message ? String(err.message) : ''
    if (!message || /account|password|incorrect|unauthorized/i.test(message) || /账号或密码错误/.test(message)) {
      return '原密码错误或账号状态异常'
    }
    if (/length/i.test(message) || /密码长度/.test(message)) {
      return '密码长度需为8到64位'
    }
    if (/letters|digits/i.test(message) || /字母和数字/.test(message)) {
      return '密码需包含字母和数字'
    }
    return message
  },
  passwordResetErrorMessage(err) {
    const message = err && err.message ? String(err.message) : ''
    if (/code|verify|invalid|expired|attempt/i.test(message) || /验证码|错误次数/.test(message)) {
      return '验证码错误或已过期'
    }
    if (!message || /account|password|incorrect|unauthorized/i.test(message) || /账号或密码错误/.test(message)) {
      return '邮箱或账号状态异常'
    }
    if (/length/i.test(message) || /密码长度/.test(message)) {
      return '密码长度需为8到64位'
    }
    if (/letters|digits/i.test(message) || /字母和数字/.test(message)) {
      return '密码需包含字母和数字'
    }
    return message
  },
  validateEmail(email) {
    if (!email) {
      wx.showToast({ title: '请输入安全邮箱', icon: 'none' })
      return false
    }
    if (!EMAIL_RE.test(email)) {
      wx.showToast({ title: '请输入正确的邮箱', icon: 'none' })
      return false
    }
    return true
  },
  validateCode(code) {
    if (!code) {
      wx.showToast({ title: '请输入验证码', icon: 'none' })
      return false
    }
    return true
  },
  validatePasswordPair(password, confirmPassword) {
    if (!password) {
      wx.showToast({ title: '请输入密码', icon: 'none' })
      return false
    }
    if (password.length < 8 || password.length > 64) {
      wx.showToast({ title: '密码长度需为8到64位', icon: 'none' })
      return false
    }
    if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) {
      wx.showToast({ title: '密码需包含字母和数字', icon: 'none' })
      return false
    }
    if (password !== confirmPassword) {
      wx.showToast({ title: '两次输入的密码不一致', icon: 'none' })
      return false
    }
    return true
  },
  trimForm(form) {
    const next = {}
    Object.keys(form || {}).forEach((key) => {
      const value = form[key]
      next[key] = typeof value === 'string' ? value.trim() : value
    })
    return next
  },
  startCountdown(key) {
    this.clearCountdown(key)
    this.setData({ [`countdowns.${key}`]: CODE_SECONDS })
    this.countdownTimers = this.countdownTimers || {}
    this.countdownTimers[key] = setInterval(() => {
      const current = this.data.countdowns[key] || 0
      if (current <= 1) {
        this.clearCountdown(key)
        this.setData({ [`countdowns.${key}`]: 0 })
        return
      }
      this.setData({ [`countdowns.${key}`]: current - 1 })
    }, 1000)
  },
  clearCountdown(key) {
    if (!this.countdownTimers || !this.countdownTimers[key]) return
    clearInterval(this.countdownTimers[key])
    delete this.countdownTimers[key]
  },
  clearCountdowns() {
    Object.keys(this.countdownTimers || {}).forEach((key) => this.clearCountdown(key))
  }
})
