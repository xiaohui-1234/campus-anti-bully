// pages/login/login.js
Page({

  /**
   * 页面的初始数据
   */
  data: {
    avatar: '/images/我的1.png',
    nickname: '',
    showProfileModal: false,
    canSubmit: false,
    submitting: false
  },

  openProfileModal() {
    if (this.data.submitting) {
      return
    }

    this.setData({
      showProfileModal: true
    })
  },

  closeProfileModal() {
    if (this.data.submitting) {
      return
    }

    this.setData({
      showProfileModal: false
    })
  },

  noop() {},

  onChooseAvatar(e) {
    const avatar = e.detail.avatarUrl
    this.setData({
      avatar,
      canSubmit: !!avatar && avatar !== '/images/我的1.png' && !!this.data.nickname.trim()
    })
  },

  onNicknameInput(e) {
    const nickname = (e.detail.value || '').trim()
    this.setData({
      nickname,
      canSubmit: !!nickname && this.data.avatar !== '/images/我的1.png'
    })
  },

  uploadAvatar(filePath) {
    return new Promise((resolve, reject) => {
      if (!filePath || filePath === '/images/我的1.png') {
        reject(new Error('请选择头像'))
        return
      }

      if (filePath.indexOf('cloud://') === 0 || filePath.indexOf('http://') === 0 || filePath.indexOf('https://') === 0) {
        resolve(filePath)
        return
      }

      const extensionMatch = filePath.match(/\.[^.\\/?#]+(?=([?#].*)?$)/)
      const extension = extensionMatch ? extensionMatch[0] : '.png'
      const cloudPath = `user-avatar/${Date.now()}-${Math.random().toString(36).slice(2, 8)}${extension}`

      wx.cloud.uploadFile({
        cloudPath,
        filePath,
        success: (res) => {
          resolve(res.fileID)
        },
        fail: (err) => {
          reject(err)
        }
      })
    })
  },

  getLoginCode() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (res) => {
          if (res.code) {
            resolve(res.code)
            return
          }

          reject(new Error('获取登录凭证失败'))
        },
        fail: (err) => {
          reject(err)
        }
      })
    })
  },
 
  // 微信一键登录
  async wechatLogin() {
    if (this.data.submitting) {
      return
    }

    const nickname = this.data.nickname.trim()
    const avatar = this.data.avatar

    if (!avatar || avatar === '/images/我的1.png') {
      wx.showToast({ title: '请先选择头像', icon: 'none' })
      return
    }

    if (!nickname) {
      wx.showToast({ title: '请先填写昵称', icon: 'none' })
      return
    }

    this.setData({
      submitting: true
    })
    wx.showLoading({ title: '正在登录' })

    try {
      const avatarFileId = await this.uploadAvatar(avatar)
      const code = await this.getLoginCode()
      const userInfo = {
        nickName: nickname,
        avatarUrl: avatarFileId
      }

      const res = await new Promise((resolve, reject) => {
        wx.cloud.callFunction({
          name: 'login',
          data: {
            code,
            userInfo
          },
          success: resolve,
          fail: reject
        })
      })

      console.log('云函数登录结果:', res.result)
      if (res.result.success) {
        const loginUser = {
          ...res.result.user,
          nickname: res.result.user.nickname || nickname,
          avatar: res.result.user.avatar || avatarFileId
        }
        wx.setStorageSync('userinfo', loginUser)
        this.setData({
          showProfileModal: false
        })
        wx.switchTab({ url: '/pages/index/index' })
        return
      }

      console.error('云函数登录失败:', res.result.message)
      wx.showToast({
        title: res.result.message || '登录失败',
        icon: 'error'
      })
    } catch (err) {
      console.error('登录失败', err)
      wx.showToast({
        title: '登录失败，请重试',
        icon: 'error'
      })
    } finally {
      this.setData({
        submitting: false
      })
      wx.hideLoading()
    }
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad(options) {

  },

  /**
   * 生命周期函数--监听页面初次渲染完成
   */
  onReady() {

  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow() {

  },

  /**
   * 生命周期函数--监听页面隐藏
   */
  onHide() {

  },

  /**
   * 生命周期函数--监听页面卸载
   */
  onUnload() {

  },

  /**
   * 页面相关事件处理函数--监听用户下拉动作
   */
  onPullDownRefresh() {

  },

  /**
   * 页面上拉触底事件的处理函数
   */
  onReachBottom() {

  },

  /**
   * 用户点击右上角分享
   */
  onShareAppMessage() {

  }
})