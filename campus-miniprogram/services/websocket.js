const env = require('../config/env')
const storage = require('../utils/storage')
const request = require('./request')
const eventApi = require('./event-api')

let socketTask = null
let connected = false
let manualClose = false
let reconnectTimer = null
let reconnectCount = 0
let heartbeatTimer = null
let heartbeatTimeoutTimer = null
let subscribedDeviceIds = []
const listeners = {}
const statusListeners = []
let connectionStatus = 'OFFLINE'

function connect() {
  const token = storage.getAccessToken()
  if (!token || socketTask) return
  manualClose = false
  setStatus('CONNECTING')
  socketTask = wx.connectSocket({
    url: `${env.wsUrl}?token=${encodeURIComponent(token)}`
  })
  socketTask.onOpen(() => {
    connected = true
    reconnectCount = 0
    setStatus('CONNECTED')
    startHeartbeat()
    if (subscribedDeviceIds.length) {
      sendSubscription()
    }
  })
  socketTask.onMessage((message) => {
    let body
    try {
      body = JSON.parse(message.data || '{}')
    } catch (err) {
      return
    }
    if (body.type === 'PONG') {
      clearTimeout(heartbeatTimeoutTimer)
      heartbeatTimeoutTimer = null
    }
    const data = body.data || body
    const callbacks = listeners[body.type] || []
    if (!callbacks.length && (body.type === 'NEW_EVENT' || body.type === 'DEVICE_STATUS')) {
      const app = typeof getApp === 'function' ? getApp() : null
      if (app && typeof app.handleSocketMessage === 'function') {
        app.handleSocketMessage(body.type, data)
      }
    }
    callbacks.forEach((callback) => callback(data))
  })
  socketTask.onClose(() => {
    connected = false
    socketTask = null
    stopHeartbeat()
    setStatus(manualClose ? 'OFFLINE' : 'RECONNECTING')
    if (!manualClose) reconnect()
  })
  socketTask.onError(() => {
    connected = false
    setStatus('RECONNECTING')
  })
}

function close() {
  manualClose = true
  clearTimeout(reconnectTimer)
  stopHeartbeat()
  if (socketTask) {
    socketTask.close()
  }
  socketTask = null
  connected = false
  subscribedDeviceIds = []
  setStatus('OFFLINE')
}

function subscribeEvents(deviceIds) {
  const nextDeviceIds = Array.from(new Set(deviceIds || []))
  if (sameDeviceIds(nextDeviceIds, subscribedDeviceIds)) return
  subscribedDeviceIds = nextDeviceIds
  sendSubscription()
}

function sendSubscription() {
  send({
    type: 'SUBSCRIBE_EVENTS',
    device_ids: subscribedDeviceIds
  })
}

function sameDeviceIds(left, right) {
  if (left.length !== right.length) return false
  const rightSet = new Set(right)
  return left.every((item) => rightSet.has(item))
}

function onMessage(type, callback) {
  listeners[type] = listeners[type] || []
  listeners[type].push(callback)
}

function onStatus(callback) {
  if (typeof callback !== 'function') return
  statusListeners.push(callback)
  callback(connectionStatus)
}

function setStatus(status) {
  if (connectionStatus === status) return
  connectionStatus = status
  statusListeners.forEach((callback) => callback(status))
}

function getStatus() {
  return connectionStatus
}

function sendPing() {
  if (!connected || !socketTask) return
  send({ type: 'PING', timestamp: Date.now() })
  clearTimeout(heartbeatTimeoutTimer)
  heartbeatTimeoutTimer = setTimeout(handleHeartbeatTimeout, 10000)
}

function startHeartbeat() {
  stopHeartbeat()
  sendPing()
  heartbeatTimer = setInterval(sendPing, 25000)
}

function stopHeartbeat() {
  clearInterval(heartbeatTimer)
  clearTimeout(heartbeatTimeoutTimer)
  heartbeatTimer = null
  heartbeatTimeoutTimer = null
}

function handleHeartbeatTimeout() {
  if (!socketTask || manualClose) return
  const task = socketTask
  connected = false
  socketTask = null
  setStatus('RECONNECTING')
  try {
    task.close()
  } catch (err) {
  }
  reconnect()
}

function reconnect() {
  clearTimeout(reconnectTimer)
  setStatus('RECONNECTING')
  const delay = Math.min(30000, 1000 * Math.pow(2, reconnectCount++))
  reconnectTimer = setTimeout(async () => {
    try {
      if (storage.getRefreshToken()) {
        await request.refreshAccessToken()
      }
    } catch (err) {
      redirectToLogin()
      return
    }
    connect()
    try {
      const events = await eventApi.unpulled()
      ;(listeners.NEW_EVENT || []).forEach((callback) => {
        events.forEach((item) => callback(item))
      })
    } catch (err) {
      // 补拉失败时等待下一轮页面刷新或重连。
    }
  }, delay)
}

function send(payload) {
  if (!connected || !socketTask) return
  socketTask.send({ data: JSON.stringify(payload) })
}

function redirectToLogin() {
  const app = typeof getApp === 'function' ? getApp() : null
  if (app && typeof app.redirectToLogin === 'function') {
    app.redirectToLogin()
  } else {
    storage.clearTokens()
    close()
    wx.reLaunch({ url: '/pages/login/index' })
  }
}

module.exports = {
  connect,
  close,
  subscribeEvents,
  onMessage,
  onStatus,
  getStatus,
  sendPing,
  reconnect
}
