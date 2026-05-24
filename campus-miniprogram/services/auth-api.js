const request = require('./request')
const storage = require('../utils/storage')

function loginByWxCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: async (loginRes) => {
        try {
          const data = await request({
            url: '/auth/wx/login',
            method: 'POST',
            data: { code: loginRes.code }
          })
          storage.setTokens(data)
          resolve(data)
        } catch (err) {
          reject(err)
        }
      },
      fail: reject
    })
  })
}

async function logout() {
  try {
    await request({ url: '/auth/wx/logout', method: 'POST' })
  } finally {
    storage.clearTokens()
  }
}

module.exports = {
  loginByWxCode,
  logout
}
