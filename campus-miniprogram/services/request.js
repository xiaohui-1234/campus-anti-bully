const env = require('../config/env')
const storage = require('../utils/storage')

let refreshing = false
let refreshWaiters = []

function request(options) {
  return new Promise((resolve, reject) => {
    const token = storage.getAccessToken()
    const header = Object.assign({}, options.header || {})
    if (token) {
      header.Authorization = `Bearer ${token}`
    }
    wx.request({
      url: `${env.baseUrl}${options.url}`,
      method: options.method || 'GET',
      data: options.data || {},
      timeout: options.timeout || 8000,
      header,
      success: async (res) => {
        const body = res.data || {}
        if ((res.statusCode === 401 || body.code === 401) && !options.__retry) {
          try {
            await refreshAccessToken()
            const retryResult = await request(Object.assign({}, options, { __retry: true }))
            resolve(retryResult)
          } catch (err) {
            storage.clearTokens()
            wx.navigateTo({ url: '/pages/login/index' })
            reject(err)
          }
          return
        }
        if (body.code !== 0) {
          wx.showToast({ title: body.message || '请求失败', icon: 'none' })
          reject(body)
          return
        }
        resolve(body.data)
      },
      fail: reject
    })
  })
}

function refreshAccessToken() {
  if (refreshing) {
    return new Promise((resolve, reject) => {
      refreshWaiters.push({ resolve, reject })
    })
  }
  refreshing = true
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${env.baseUrl}/auth/refresh`,
      method: 'POST',
      data: { refresh_token: storage.getRefreshToken() },
      success: (res) => {
        const body = res.data || {}
        if (body.code === 0) {
          storage.setTokens(body.data)
          resolve(body.data)
          flushWaiters(null, body.data)
        } else {
          reject(body)
          flushWaiters(body)
        }
      },
      fail: (err) => {
        reject(err)
        flushWaiters(err)
      },
      complete: () => {
        refreshing = false
      }
    })
  })
}

function flushWaiters(err, data) {
  refreshWaiters.forEach((waiter) => {
    if (err) waiter.reject(err)
    else waiter.resolve(data)
  })
  refreshWaiters = []
}

module.exports = request
