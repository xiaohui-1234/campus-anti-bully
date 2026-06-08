const EVENT_TYPE_LABELS = {
  BUTTON: '按钮',
  VOICE: '声音',
  FIGHT: '打架',
  HELP: '求助',
  SOS: '紧急求助'
}

const ALARM_INFO_LABELS = {
  button_alarm: '按钮报警',
  voice_help: '语音求助',
  fire: '着火',
  kill: '杀人',
  fight: '打架',
  kidnap: '绑架',
  explosion: '爆炸',
  blood: '流血',
  faint: '晕倒',
  stop_hit: '停止殴打',
  robbery: '抢劫',
  help_me: '救命',
  call_people: '叫人',
  group_fight: '群殴',
  dont_move: '不许动',
  general_alarm: '一般报警'
}

function getLabel(labels, value, fallback) {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  return labels[value] || value
}

function formatEvent(event) {
  const value = event || {}
  const eventType = value.event_type || value.eventType
  const alarmInfo = value.alarm_info || value.alarmInfo

  return Object.assign({}, value, {
    event_type_text: getLabel(EVENT_TYPE_LABELS, eventType, '报警事件'),
    alarm_info_text: getLabel(ALARM_INFO_LABELS, alarmInfo, '暂无报警信息')
  })
}

module.exports = {
  EVENT_TYPE_LABELS,
  ALARM_INFO_LABELS,
  formatEvent
}
