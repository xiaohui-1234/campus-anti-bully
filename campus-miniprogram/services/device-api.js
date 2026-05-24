const request = require('./request')

function list(params = {}) {
  return request({ url: `/devices?page=${params.page || 1}&size=${params.size || 10}` })
}

function search(params = {}) {
  const query = buildQuery(params)
  return request({ url: `/devices/search${query}` })
}

function bind(data) {
  return request({ url: '/devices/bind', method: 'POST', data })
}

function updateInfo(deviceId, data) {
  return request({ url: `/devices/${deviceId}/info`, method: 'PUT', data })
}

function unbind(deviceId) {
  return request({ url: `/devices/${deviceId}/binding`, method: 'DELETE' })
}

function buildQuery(params) {
  const pairs = Object.keys(params)
    .filter((key) => params[key] !== undefined && params[key] !== '')
    .map((key) => `${key}=${encodeURIComponent(params[key])}`)
  return pairs.length ? `?${pairs.join('&')}` : ''
}

module.exports = {
  list,
  search,
  bind,
  updateInfo,
  unbind
}
