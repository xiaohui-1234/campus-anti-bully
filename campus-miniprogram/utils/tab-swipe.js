const TAB_ROUTES = [
  '/pages/events/index',
  '/pages/devices/index',
  '/pages/home/index',
  '/pages/status/index',
  '/pages/profile/index'
]

const EXIT_DURATION = 100
const SWITCH_FALLBACK_DURATION = 360
const ENTER_DURATION = 170

let switching = false
let pendingEntry = null
let switchFallbackTimer = null

function getRoute(page) {
  if (page && page.route) {
    return `/${page.route}`
  }
  const pages = getCurrentPages()
  const currentPage = pages[pages.length - 1]
  return currentPage && `/${currentPage.route}`
}

function animateEntry(page) {
  const route = getRoute(page)
  if (!pendingEntry || pendingEntry.route !== route) {
    pendingEntry = null
    switching = false
    clearTimeout(switchFallbackTimer)
    switchFallbackTimer = null
    page.setData({ tabSwipeClass: '' })
    return
  }
  const direction = pendingEntry.direction
  pendingEntry = null
  switching = false
  clearTimeout(switchFallbackTimer)
  switchFallbackTimer = null
  page.setData({
    tabSwipeClass: direction > 0 ? 'tab-swipe-enter-right' : 'tab-swipe-enter-left'
  })
  clearTimeout(page._tabSwipeEntryTimer)
  page._tabSwipeEntryTimer = setTimeout(() => {
    page._tabSwipeEntryTimer = null
    page.setData({ tabSwipeClass: '' })
  }, ENTER_DURATION)
}

function clearPageTimers(page) {
  clearTimeout(page._tabSwipeEntryTimer)
  clearTimeout(page._tabSwipeSwitchTimer)
  page._tabSwipeEntryTimer = null
  page._tabSwipeSwitchTimer = null
}

function wrapLifecycle(options, name, before, after) {
  const original = options[name]
  options[name] = function wrappedLifecycle(...args) {
    if (before) {
      before(this)
    }
    const result = original && original.apply(this, args)
    if (after) {
      after(this)
    }
    return result
  }
}

function withTabSwipe(pageOptions) {
  const options = Object.assign({
    onTabSwipeCommit(event) {
      if (switching) {
        return
      }
      const currentIndex = TAB_ROUTES.indexOf(getRoute(this))
      const direction = Number(event.direction)
      const targetIndex = currentIndex + direction
      if (
        currentIndex < 0 ||
        (direction !== -1 && direction !== 1) ||
        targetIndex < 0 ||
        targetIndex >= TAB_ROUTES.length
      ) {
        return
      }

      const targetRoute = TAB_ROUTES[targetIndex]
      switching = true
      clearTimeout(switchFallbackTimer)
      switchFallbackTimer = null
      pendingEntry = { route: targetRoute, direction }
      clearTimeout(this._tabSwipeEntryTimer)
      this._tabSwipeEntryTimer = null
      this.setData({
        tabSwipeClass: direction > 0 ? 'tab-swipe-exit-left' : 'tab-swipe-exit-right'
      })
      this._tabSwipeSwitchTimer = setTimeout(() => {
        this._tabSwipeSwitchTimer = null
        wx.switchTab({
          url: targetRoute,
          fail: () => {
            pendingEntry = null
            switching = false
            clearTimeout(switchFallbackTimer)
            switchFallbackTimer = null
            this.setData({ tabSwipeClass: '' })
          },
          complete: () => {
            if (!switching || !pendingEntry || pendingEntry.route !== targetRoute) {
              return
            }
            clearTimeout(switchFallbackTimer)
            switchFallbackTimer = setTimeout(() => {
              switching = false
              pendingEntry = null
              switchFallbackTimer = null
            }, SWITCH_FALLBACK_DURATION)
          }
        })
      }, EXIT_DURATION)
    }
  }, pageOptions, {
    data: Object.assign({ tabSwipeClass: '' }, pageOptions.data)
  })
  wrapLifecycle(options, 'onShow', null, animateEntry)
  wrapLifecycle(options, 'onHide', clearPageTimers)
  wrapLifecycle(options, 'onUnload', clearPageTimers)
  return options
}

module.exports = {
  withTabSwipe
}
