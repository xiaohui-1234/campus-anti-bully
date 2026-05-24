const env = require('../config/env')
const request = require('./request')
const storage = require('../utils/storage')

function me() {
  return request({ url: '/users/me' })
}

function updateMe(data) {
  return request({ url: '/users/me', method: 'PUT', data })
}

function uploadAvatar(filePath) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${env.baseUrl}/users/me/avatar`,
      filePath,
      name: 'file',
      header: {
        Authorization: `Bearer ${storage.getAccessToken()}`
      },
      success: (res) => {
        const body = JSON.parse(res.data || '{}')
        if (body.code === 0) resolve(body.data)
        else reject(body)
      },
      fail: reject
    })
  })
}

module.exports = {
  me,
  updateMe,
  uploadAvatar
}
