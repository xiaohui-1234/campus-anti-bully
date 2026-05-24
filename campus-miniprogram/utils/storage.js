const ACCESS_TOKEN = 'access_token'
const REFRESH_TOKEN = 'refresh_token'

function getAccessToken() {
  return wx.getStorageSync(ACCESS_TOKEN) || ''
}

function getRefreshToken() {
  return wx.getStorageSync(REFRESH_TOKEN) || ''
}

function setTokens(tokens) {
  if (tokens.access_token || tokens.accessToken) {
    wx.setStorageSync(ACCESS_TOKEN, tokens.access_token || tokens.accessToken)
  }
  if (tokens.refresh_token || tokens.refreshToken) {
    wx.setStorageSync(REFRESH_TOKEN, tokens.refresh_token || tokens.refreshToken)
  }
}

function clearTokens() {
  wx.removeStorageSync(ACCESS_TOKEN)
  wx.removeStorageSync(REFRESH_TOKEN)
}

module.exports = {
  getAccessToken,
  getRefreshToken,
  setTokens,
  clearTokens
}
