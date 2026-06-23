/**
 * 全局 WebSocket 单例管理
 * - App.vue 登录后连接，登出断开
 * - 支持多组件注册消息处理器（NOTIFICATION / CHAT / INFO / ERROR）
 */

let ws = null
const handlers = new Map()   // type -> Set<Function>
let _onStatusChange = null   // 连接状态变化回调 (connected: boolean) => void

const getToken = () =>
  localStorage.getItem('kp:token') || sessionStorage.getItem('kp:token') || ''

const resolveUrl = () => {
  const envUrl = import.meta.env.VITE_WS_URL || ''
  let baseUrl = ''
  if (envUrl) {
    if (envUrl.startsWith('ws')) {
      baseUrl = envUrl
    } else if (envUrl.startsWith('http')) {
      const url = new URL(envUrl)
      const scheme = url.protocol === 'https:' ? 'wss:' : 'ws:'
      const basePath = url.pathname && url.pathname !== '/' ? url.pathname : ''
      baseUrl = `${scheme}//${url.host}${basePath}/ws/chat`
    } else if (envUrl.startsWith('/')) {
      const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
      baseUrl = `${scheme}://${window.location.host}${envUrl}`
    }
  } else {
    const apiBase = import.meta.env.VITE_API_BASE || ''
    if (apiBase && apiBase.startsWith('http')) {
      const url = new URL(apiBase)
      const scheme = url.protocol === 'https:' ? 'wss:' : 'ws:'
      baseUrl = `${scheme}//${url.host}/ws/chat`
    } else {
      const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
      baseUrl = `${scheme}://${window.location.host}/ws/chat`
    }
  }
  const token = getToken()
  if (token) {
    const separator = baseUrl.includes('?') ? '&' : '?'
    baseUrl = `${baseUrl}${separator}token=${encodeURIComponent(token)}`
  }
  return baseUrl
}

/**
 * 建立全局 WebSocket 连接（幂等，重复调用不会创建多个连接）
 */
export function connect() {
  if (ws) return
  try {
    const url = resolveUrl()
    ws = new WebSocket(url)

    ws.onopen = () => {
      _onStatusChange?.(true)
    }

    ws.onclose = () => {
      ws = null
      _onStatusChange?.(false)
    }

    ws.onerror = () => {
      _onStatusChange?.(false)
    }

    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data)
        const type = payload?.type
        if (!type) return
        const fns = handlers.get(type)
        if (fns) {
          for (const fn of fns) {
            try { fn(payload.data) } catch { /* handler error */ }
          }
        }
      } catch {
        // ignore malformed payloads
      }
    }
  } catch {
    ws = null
  }
}

/**
 * 断开全局 WebSocket 连接
 */
export function disconnect() {
  if (ws) {
    try { ws.close() } catch { /* ignore */ }
    ws = null
  }
  _onStatusChange?.(false)
}

/**
 * 通过全局 WebSocket 发送消息
 */
export function send(data) {
  if (!ws || ws.readyState !== WebSocket.OPEN) return false
  try {
    ws.send(JSON.stringify(data))
    return true
  } catch {
    return false
  }
}

/**
 * 注册消息处理器（返回取消注册函数）
 * @param {string} type  消息类型，如 'CHAT' / 'NOTIFICATION'
 * @param {Function} fn  处理函数，参数为 payload.data
 */
export function on(type, fn) {
  if (!handlers.has(type)) handlers.set(type, new Set())
  handlers.get(type).add(fn)
  return () => handlers.get(type)?.delete(fn)
}

/**
 * 监听连接状态变化（返回取消监听函数）
 */
export function onStatusChange(fn) {
  _onStatusChange = fn
  return () => { if (_onStatusChange === fn) _onStatusChange = null }
}

/**
 * 当前是否已连接
 */
export function isConnected() {
  return ws !== null && ws.readyState === WebSocket.OPEN
}
