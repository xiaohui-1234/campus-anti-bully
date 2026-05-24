const request = require('./request')

function unpulled() {
  return request({ url: '/events/unpulled' })
}

function search(params = {}) {
  const query = buildQuery(params)
  return request({ url: `/events/search${query}` })
}

function markRead(eventId) {
  return request({ url: `/events/${eventId}/read`, method: 'PUT' })
}

function remove(eventId) {
  return request({ url: `/events/${eventId}`, method: 'DELETE' })
}

async function countUnread() {
  const data = await search({ read_status: 'UNREAD', page: 1, size: 1 })
  return data.total || 0
}

function refreshUrl(eventId, expireSeconds = 3600) {
  return request({
    url: `/events/${eventId}/refresh-url`,
    method: 'POST',
    data: { expire_seconds: expireSeconds }
  })
}

function buildQuery(params) {
  const pairs = Object.keys(params)
    .filter((key) => params[key] !== undefined && params[key] !== '')
    .map((key) => `${key}=${encodeURIComponent(params[key])}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

module.exports = {
  unpulled,
  search,
  countUnread,
  markRead,
  remove,
  refreshUrl
}
