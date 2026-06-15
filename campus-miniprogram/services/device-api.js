const request = require('./request')

function list(params = {}, options = {}) {
  return request({
    url: `/devices?page=${params.page || 1}&size=${params.size || 10}`,
    showError: options.showError
  })
}

function search(params = {}, options = {}) {
  const query = buildQuery(params)
  return request({ url: `/devices/search${query}`, showError: options.showError })
}

async function listAll(options = {}) {
  return fetchAllPages((page, requestOptions) => list({ page, size: 100 }, requestOptions), options)
}

async function searchAll(params = {}, options = {}) {
  return fetchAllPages((page, requestOptions) => search(Object.assign({}, params, {
    page,
    size: 100
  }), requestOptions), options)
}

async function fetchAllPages(fetchPage, options) {
  const first = await fetchPage(1, options)
  const records = first.records || []
  const total = Number(first.total) || records.length
  const size = Number(first.size) || 100
  const pageTotal = Math.ceil(total / size)
  if (pageTotal <= 1) {
    return { records, total }
  }
  const rest = []
  for (let page = 2; page <= pageTotal; page += 1) {
    rest.push(await fetchPage(page, { showError: false }))
  }
  return {
    records: records.concat(...rest.map((item) => item.records || [])),
    total
  }
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
  listAll,
  search,
  searchAll,
  bind,
  updateInfo,
  unbind
}
