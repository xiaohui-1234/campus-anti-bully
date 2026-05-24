import axios from 'axios'

const authApi = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

const api = axios.create({
  baseURL: '/backend/v1',
  timeout: 10000
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let refreshing = false
let refreshWaiters = []

api.interceptors.response.use((response) => {
  const body = response.data
  if (body.code !== 0) {
    return Promise.reject(new Error(body.message || '请求失败'))
  }
  return body.data
}, async (error) => {
  const originalConfig = error.config || {}
  if (error.response && error.response.status === 401 && !originalConfig.__retry) {
    try {
      await refreshAccessToken()
      originalConfig.__retry = true
      originalConfig.headers = originalConfig.headers || {}
      originalConfig.headers.Authorization = `Bearer ${localStorage.getItem('admin_access_token')}`
      return api(originalConfig)
    } catch (refreshError) {
      clearAdminTokens()
      window.location.href = '/login'
      return Promise.reject(refreshError)
    }
  }
  return Promise.reject(error)
})

export async function loginByOpenid(openid) {
  const response = await authApi.post('/auth/openid/admin-login', { openid })
  const body = response.data
  if (body.code !== 0) {
    throw new Error(body.message || '登录失败')
  }
  setAdminTokens(body.data)
  return body.data
}

function setAdminTokens(data) {
  localStorage.setItem('admin_access_token', data.access_token || data.accessToken)
  localStorage.setItem('admin_refresh_token', data.refresh_token || data.refreshToken)
}

function clearAdminTokens() {
  localStorage.removeItem('admin_access_token')
  localStorage.removeItem('admin_refresh_token')
}

function refreshAccessToken() {
  if (refreshing) {
    return new Promise((resolve, reject) => {
      refreshWaiters.push({ resolve, reject })
    })
  }
  refreshing = true
  return authApi.post('/auth/refresh', {
    refresh_token: localStorage.getItem('admin_refresh_token')
  }).then((response) => {
    const body = response.data
    if (body.code !== 0) {
      throw new Error(body.message || '刷新登录状态失败')
    }
    localStorage.setItem('admin_access_token', body.data.access_token || body.data.accessToken)
    flushWaiters(null, body.data)
    return body.data
  }).catch((error) => {
    flushWaiters(error)
    throw error
  }).finally(() => {
    refreshing = false
  })
}

function flushWaiters(error, data) {
  refreshWaiters.forEach((waiter) => {
    if (error) waiter.reject(error)
    else waiter.resolve(data)
  })
  refreshWaiters = []
}

export function getConfig() {
  return api.get('/config')
}

export function updateConfig(data) {
  return api.put('/config', data)
}

export function getSystemInfo() {
  return api.get('/system/info')
}
