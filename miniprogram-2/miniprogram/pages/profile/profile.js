// pages/profile/profile.js
Page({

  /**
   * 页面的初始数据
   */
  data: {
    userInfo: {},
    nickname: '微信用户',
    avatar: '/images/我的1.png'
  },
  toPersonDetail(e){
    wx.navigateTo({
      url:'/pages/persondetail/persondetail',
    })
  },
  toSetting(e){
    wx.navigateTo({
      url:'/pages/setting/setting',
    })
  },
  toService(e){
    wx.navigateTo({
      url:'/pages/service/service',
    })
  },
  toHistoryData(e){
    wx.navigateTo({
      url:'/pages/historydata/historydata',
    })
  },
  toAddDevice(e){
    wx.navigateTo({
      url:'/pages/add/add',
    })
  },
  toSetWifi(e){
    wx.navigateTo({
      url:'/pages/wifi/wifi',
    })
  },

  /**
   * 生命周期函数--监听页面加载
   */
  onLoad: function (options) {
    
  },

  resolveAvatar(avatar) {
    if (!avatar || avatar.indexOf('cloud://') !== 0) {
      return Promise.resolve(avatar || '/images/我的1.png')
    }

    return new Promise((resolve) => {
      wx.cloud.getTempFileURL({
        fileList: [avatar],
        success: (res) => {
          const file = res.fileList && res.fileList[0]
          resolve(file && file.tempFileURL ? file.tempFileURL : '/images/我的1.png')
        },
        fail: () => {
          resolve('/images/我的1.png')
        }
      })
    })
  },

  async loadUserInfo() {
    const userInfo = wx.getStorageSync('userinfo') || {}
    const nickname = userInfo.nickname || '微信用户'
    const avatar = await this.resolveAvatar(userInfo.avatar)

    this.setData({
      userInfo,
      nickname,
      avatar
    })

    if (!userInfo.openid) {
      return
    }

    const db = wx.cloud.database()
    db.collection('login').where({
      openid: userInfo.openid
    }).get({
      success: (res) => {
        if (!res.data || !res.data.length) {
          return
        }

        const latestUserInfo = {
          ...userInfo,
          ...res.data[0],
          nickname: res.data[0].nickname || nickname,
          avatar: res.data[0].avatar || userInfo.avatar || '/images/我的1.png'
        }

        wx.setStorageSync('userinfo', latestUserInfo)
        this.resolveAvatar(latestUserInfo.avatar).then((avatarUrl) => {
          this.setData({
            userInfo: latestUserInfo,
            nickname: latestUserInfo.nickname,
            avatar: avatarUrl
          })
        })
      },
      fail: (err) => {
        console.error('获取用户资料失败', err)
      },
      complete: () => {
        wx.stopPullDownRefresh()
      }
    })
  },
 
  /**
   * 生命周期函数--监听页面初次渲染完成
   */
  onReady: function () {
    
  },

  /**
   * 生命周期函数--监听页面显示
   */
  onShow: function () {
    this.loadUserInfo()
  },

  /**
   * 生命周期函数--监听页面隐藏
   */
  onHide: function () {
    
  },

  /**
   * 生命周期函数--监听页面卸载
   */
  onUnload: function () {
    
  },

  /**
   * 页面相关事件处理函数--监听用户下拉动作
   */
  onPullDownRefresh: function () {
    this.loadUserInfo()
  },

  /**
   * 页面上拉触底事件的处理函数
   */
  onReachBottom: function () {
    
  },

  /**
   * 用户点击右上角分享
   */
  onShareAppMessage: function () {
    
  }
})