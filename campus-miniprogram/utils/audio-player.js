let currentAudio = null
let currentEventId = ''
let currentHandlers = null
let playToken = 0
let lastProgressEmittedAt = 0
let lastProgress = -1

function emitState(playing, progress, force = false) {
  const normalizedProgress = Math.round(Math.max(0, Math.min(100, progress || 0)))
  const now = Date.now()
  if (playing && !force) {
    if (normalizedProgress === lastProgress || now - lastProgressEmittedAt < 250) return
  }
  lastProgress = normalizedProgress
  lastProgressEmittedAt = now
  if (currentHandlers && currentHandlers.onState) {
    currentHandlers.onState({
      event_id: currentEventId,
      playing,
      progress: normalizedProgress
    })
  }
}

function destroyCurrentAudio(shouldEmit) {
  const eventId = currentEventId
  const handlers = currentHandlers
  if (!currentAudio) {
    currentEventId = ''
    currentHandlers = null
    return
  }
  try {
    currentAudio.stop()
    currentAudio.destroy()
  } catch (err) {
  }
  currentAudio = null
  currentEventId = ''
  currentHandlers = null
  lastProgress = -1
  lastProgressEmittedAt = 0
  if (shouldEmit && handlers && handlers.onState) {
    handlers.onState({
      event_id: eventId,
      playing: false,
      progress: 0
    })
  }
}

function stop() {
  playToken += 1
  destroyCurrentAudio(true)
}

function isPlaying(eventId) {
  return !!currentAudio && currentEventId === eventId
}

function play(src, options) {
  const eventId = options && options.event_id ? options.event_id : ''
  if (!src) {
    wx.showToast({ title: '录音地址无效', icon: 'none' })
    return
  }
  if (!/^https:\/\//i.test(src)) {
    wx.showToast({ title: '录音地址不是 HTTPS', icon: 'none' })
    return
  }

  playToken += 1
  const token = playToken
  destroyCurrentAudio(true)

  const audio = wx.createInnerAudioContext()
  audio.obeyMuteSwitch = false
  currentAudio = audio
  currentEventId = eventId
  currentHandlers = options || null
  audio.onPlay(() => {
    emitState(true, 0, true)
  })
  audio.onTimeUpdate(() => {
    const duration = audio.duration || 0
    const currentTime = audio.currentTime || 0
    emitState(true, duration > 0 ? (currentTime / duration) * 100 : 0)
  })
  audio.onEnded(() => {
    if (currentAudio === audio) {
      destroyCurrentAudio(true)
    }
  })
  audio.onError(() => {
    if (currentAudio === audio) {
      destroyCurrentAudio(true)
      wx.showToast({ title: '播放失败', icon: 'none' })
    }
  })
  audio.src = src

  if (token === playToken) {
    audio.play()
  }
}

module.exports = {
  isPlaying,
  play,
  stop
}
