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

module.exports = {
  isToday
}
