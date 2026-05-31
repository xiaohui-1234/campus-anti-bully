const env = require('../config/env')
const storage = require('../utils/storage')
const request = require('./request')
const eventApi = require('./event-api')

let socketTask = null
let connected = false
let manualClose = false
let reconnectTimer = null
let reconnectCount = 0
let subscribedDeviceIds = []
const listeners = {}

function connect() {
  const token = storage.getAccessToken()
  if (!token || socketTask) return
  manualClose = false
  socketTask = wx.connectSocket({
    url: `${env.wsUrl}?token=${encodeURIComponent(token)}`
  })
  socketTask.onOpen(() => {
    connected = true
    reconnectCount = 0
    if (subscribedDeviceIds.length) {
      subscribeEvents(subscribedDeviceIds)
    }
  })
  socketTask.onMessage((message) => {
    const body = JSON.parse(message.data || '{}')
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
    if (!manualClose) reconnect()
  })
  socketTask.onError(() => {
    connected = false
  })
}

function close() {
  manualClose = true
  clearTimeout(reconnectTimer)
  if (socketTask) {
    socketTask.close()
  }
  socketTask = null
  connected = false
}

function subscribeEvents(deviceIds) {
  subscribedDeviceIds = Array.from(new Set(deviceIds || []))
  send({
    type: 'SUBSCRIBE_EVENTS',
    device_ids: subscribedDeviceIds
  })
}

function onMessage(type, callback) {
  listeners[type] = listeners[type] || []
  listeners[type].push(callback)
}

function sendPing() {
  send({ type: 'PING', timestamp: Date.now() })
}

function reconnect() {
  clearTimeout(reconnectTimer)
  const delay = Math.min(30000, 1000 * Math.pow(2, reconnectCount++))
  reconnectTimer = setTimeout(async () => {
    try {
      if (storage.getRefreshToken()) {
        await request.refreshAccessToken()
      }
    } catch (err) {
      storage.clearTokens()
      wx.navigateTo({ url: '/pages/login/index' })
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

module.exports = {
  connect,
  close,
  subscribeEvents,
  onMessage,
  sendPing,
  reconnect
}
