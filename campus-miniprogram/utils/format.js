function text(value, fallback = '-') {
  return value === null || value === undefined || value === '' ? fallback : value
}

function statusText(value) {
  const map = {
    ONLINE: '在线',
    OFFLINE: '离线',
    UNREAD: '未读',
    READ: '已读',
    UPLOADING: '上传中',
    SUCCESS: '已完成',
    FAILED: '失败',
    PENDING: '待送达',
    PUSHED: '已送达'
  }
  return map[value] || text(value)
}

module.exports = {
  text,
  statusText
}
