const ACCESS_TOKEN = 'access_token'
const REFRESH_TOKEN = 'refresh_token'

let accessToken = ''
let refreshToken = ''

// JWT 只保存在当前小程序进程内，并清理旧版本遗留的明文持久化 token。
wx.removeStorageSync(ACCESS_TOKEN)
wx.removeStorageSync(REFRESH_TOKEN)

function getAccessToken() {
  return accessToken
}

function getRefreshToken() {
  return refreshToken
}

function setTokens(tokens) {
  if (tokens.access_token || tokens.accessToken) {
    accessToken = tokens.access_token || tokens.accessToken
  }
  if (tokens.refresh_token || tokens.refreshToken) {
    refreshToken = tokens.refresh_token || tokens.refreshToken
  }
}

function clearTokens() {
  accessToken = ''
  refreshToken = ''
}

module.exports = {
  getAccessToken,
  getRefreshToken,
  setTokens,
  clearTokens
}
