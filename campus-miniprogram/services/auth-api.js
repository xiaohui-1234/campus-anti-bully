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

function sendEmailCode(data) {
  return request({ url: '/auth/email-code/send', method: 'POST', data })
}

function activateAccount(data) {
  return request({ url: '/auth/account/activate', method: 'POST', data })
}

function verifyOldSecurityEmail(data) {
  return request({ url: '/auth/security-email/change/verify-old', method: 'POST', data })
}

function confirmSecurityEmailChange(data) {
  return request({ url: '/auth/security-email/change/confirm', method: 'POST', data })
}

function changePassword(data, options = {}) {
  return request(Object.assign({ url: '/auth/password/change', method: 'POST', data }, options))
}

function resetPassword(data, options = {}) {
  return request(Object.assign({ url: '/auth/password/reset', method: 'POST', data }, options))
}

module.exports = {
  loginByWxCode,
  logout,
  sendEmailCode,
  activateAccount,
  verifyOldSecurityEmail,
  confirmSecurityEmailChange,
  changePassword,
  resetPassword
}
