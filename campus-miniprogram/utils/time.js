function todayStart() {
  const now = new Date()
  now.setHours(0, 0, 0, 0)
  return now
}

function isToday(value) {
  if (!value) return false
  const date = new Date(String(value).replace(/-/g, '/'))
  return date >= todayStart()
}

function formatDateTime(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return [
    date.getFullYear(),
    pad(date.getMonth() + 1),
    pad(date.getDate())
  ].join('-') + ` ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function todayRange() {
  const start = todayStart()
  const end = new Date(start)
  end.setHours(23, 59, 59, 999)
  return {
    start_time: formatDateTime(start),
    end_time: formatDateTime(end)
  }
}

module.exports = {
  isToday,
  todayRange
}
