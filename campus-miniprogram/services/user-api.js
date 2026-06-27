const env = require('../config/env')
const request = require('./request')
const storage = require('../utils/storage')

function me() {
  return request({ url: '/users/me' })
}

function updateMe(data) {
  return request({ url: '/users/me', method: 'PUT', data })
}

function uploadAvatar(filePath, retried = false) {
  return new Promise((resolve, reject) => {
    const token = storage.getAccessToken()
    if (!token) {
      if (!retried && storage.getRefreshToken()) {
        request.refreshAccessToken()
          .then(() => uploadAvatar(filePath, true))
          .then(resolve)
          .catch((err) => {
            redirectToLogin()
            reject(err)
          })
        return
      }
      redirectToLogin()
      reject({ code: 401, message: '登录状态已失效，请重新登录' })
      return
    }
    wx.uploadFile({
      url: `${env.baseUrl}/users/me/avatar`,
      filePath,
      name: 'file',
      header: {
        Authorization: `Bearer ${token}`
      },
      success: async (res) => {
        let body
        try {
          body = JSON.parse(res.data || '{}')
        } catch (err) {
          wx.showToast({ title: '头像上传响应异常', icon: 'none' })
          reject(err)
          return
        }
        if ((res.statusCode === 401 || body.code === 401) && !retried) {
          try {
            await request.refreshAccessToken()
            resolve(await uploadAvatar(filePath, true))
          } catch (err) {
            const app = typeof getApp === 'function' ? getApp() : null
            if (app && typeof app.redirectToLogin === 'function') {
              app.redirectToLogin()
            }
            reject(err)
          }
          return
        }
        if (res.statusCode === 401 || body.code === 401) {
          redirectToLogin()
          reject(body)
          return
        }
        if (body.code === 0) {
          resolve(body.data)
        } else {
          wx.showToast({ title: body.message || '头像上传失败', icon: 'none' })
          reject(body)
        }
      },
      fail: (err) => {
        wx.showToast({ title: '头像上传失败，请检查网络', icon: 'none' })
        reject(err)
      }
    })
  })
}

function redirectToLogin() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (app && typeof app.redirectToLogin === 'function') {
    app.redirectToLogin()
  } else {
    storage.clearTokens()
    wx.reLaunch({ url: '/pages/login/index' })
  }
}

module.exports = {
  me,
  updateMe,
  uploadAvatar
}
