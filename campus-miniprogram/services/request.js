const env = require('../config/env')
const storage = require('../utils/storage')

let refreshing = false
let refreshWaiters = []

function request(options) {
  return new Promise((resolve, reject) => {
    const token = storage.getAccessToken()
    if (!token && requiresLogin(options.url)) {
      if (!options.__retry && storage.getRefreshToken()) {
        refreshAccessToken()
          .then(() => request(Object.assign({}, options, { __retry: true })))
          .then(resolve)
          .catch((err) => {
            redirectToLogin()
            reject(err)
          })
        return
      }
      const err = { code: 401, message: '登录状态已失效，请重新登录' }
      redirectToLogin()
      reject(err)
      return
    }
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
        if (isUnauthorized(res, body)) {
          if (shouldReturnAuthError(options.url)) {
            reject(Object.assign({}, body, {
              code: body.code || res.statusCode,
              message: normalizeErrorMessage(body.message || '请求失败')
            }))
            return
          }
          if (options.__retry || skipAuthRefresh(options.url, options)) {
            redirectToLogin()
            reject(Object.assign({}, body, {
              code: body.code || res.statusCode,
              message: normalizeErrorMessage(body.message || '登录状态已失效，请重新登录')
            }))
            return
          }
          try {
            await refreshAccessToken()
            const retryResult = await request(Object.assign({}, options, { __retry: true }))
            resolve(retryResult)
          } catch (err) {
            redirectToLogin()
            reject(err)
          }
          return
        }
        if (body.code !== 0) {
          const message = normalizeErrorMessage(body.message || '请求失败')
          showError(options, message)
          reject(Object.assign({}, body, { message }))
          return
        }
        resolve(body.data)
      },
      fail: (err) => {
        showError(options, '网络连接失败，请稍后重试')
        reject(err)
      }
    })
  })
}

function normalizedPath(url) {
  return String(url || '').split('?')[0]
}

function isPublicEndpoint(url) {
  const path = normalizedPath(url)
  return [
    '/auth/wx/login',
    '/auth/openid/admin-login',
    '/auth/password/login',
    '/auth/password/register',
    '/auth/password/reset',
    '/auth/email-code/send'
  ].includes(path)
}

function requiresLogin(url) {
  return !isPublicEndpoint(url) && normalizedPath(url) !== '/auth/refresh'
}

function shouldReturnAuthError(url) {
  const path = normalizedPath(url)
  if (path === '/auth/email-code/send') {
    return !storage.getAccessToken() && !storage.getRefreshToken()
  }
  return isPublicEndpoint(url)
}

function skipAuthRefresh(url, options) {
  const path = normalizedPath(url)
  if (options.__skipAuthRefresh || path === '/auth/refresh') return true
  if ([
    '/auth/wx/login',
    '/auth/openid/admin-login',
    '/auth/password/login',
    '/auth/password/register',
    '/auth/password/reset'
  ].includes(path)) return true
  return path === '/auth/email-code/send' && !storage.getAccessToken() && !storage.getRefreshToken()
}

function isUnauthorized(res, body) {
  return res.statusCode === 401 || Number(body.code) === 401
}

function showError(options, message) {
  if (options.showError === false) return
  wx.showToast({ title: message, icon: 'none' })
}

function normalizeErrorMessage(message) {
  const text = message ? String(message) : ''
  if (!text) return '请求失败'
  if (/email code.*required|verify code.*required/i.test(text)) return '请输入验证码'
  if (/email code.*invalid|code.*invalid|code.*expired|email code or change ticket/i.test(text)) return '验证码错误或已过期'
  if (/attempts exceeded/i.test(text)) return '验证码错误次数过多，请重新获取'
  if (/sent too frequently|too frequently/i.test(text)) return '验证码发送过于频繁，请稍后再试'
  if (/security email.*required|email is required/i.test(text)) return '请输入安全邮箱'
  if (/security email.*already used|already used/i.test(text)) return '该安全邮箱已被其他账号使用'
  if (/security email.*not bound|not bound/i.test(text)) return '当前账号未绑定安全邮箱'
  if (/change ticket.*required/i.test(text)) return '请先验证旧邮箱'
  if (/change ticket.*invalid|ticket is invalid/i.test(text)) return '邮箱修改凭证无效，请重新验证旧邮箱'
  if (/new security email.*same/i.test(text)) return '新安全邮箱不能与当前邮箱相同'
  if (/account or password.*incorrect|password.*incorrect|unauthorized/i.test(text)) return '账号或密码错误'
  if (/password.*required/i.test(text)) return '请输入密码'
  if (/password length/i.test(text)) return '密码长度需为8到64位'
  if (/letters.*digits|digits.*letters/i.test(text)) return '密码需包含字母和数字'
  if (/refresh failed|token/i.test(text)) return '登录状态已失效，请重新登录'
  return text
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

function refreshAccessToken() {
  if (refreshing) {
    return new Promise((resolve, reject) => {
      refreshWaiters.push({ resolve, reject })
    })
  }
  const refreshToken = storage.getRefreshToken()
  if (!refreshToken) {
    return Promise.reject({ code: 401, message: 'missing refresh token' })
  }
  refreshing = true
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${env.baseUrl}/auth/refresh`,
      method: 'POST',
      data: { refresh_token: refreshToken },
      success: (res) => {
        const body = res.data || {}
        if (res.statusCode === 200 && body.code === 0 && body.data && body.data.access_token) {
          storage.setTokens(body.data)
          resolve(body.data)
          flushWaiters(null, body.data)
        } else {
          storage.clearTokens()
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
module.exports.refreshAccessToken = refreshAccessToken
