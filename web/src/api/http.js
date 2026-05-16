const API_BASE = import.meta.env.VITE_API_BASE || '/api'

// 获取存储的 token
function getToken() {
  return localStorage.getItem('kp:token') || sessionStorage.getItem('kp:token')
}

// 保存 token
function saveToken(token) {
  if (token) {
    localStorage.setItem('kp:token', token)
  }
}

// 检查是否需要登录提示（避免多次弹框）
let loginPromptShown = false

// 显示登录提示框
async function showLoginPrompt() {
  if (loginPromptShown) return
  loginPromptShown = true

  const { ElMessageBox } = await import('element-plus')
  try {
    await ElMessageBox.confirm(
      '该功能需要登录后才能使用，是否前往登录？',
      '登录提示',
      {
        confirmButtonText: '去登录',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    // 用户点击"去登录"，跳转到登录页
    window.location.href = '/login'
  } catch {
    // 用户点击取消，不做处理
  } finally {
    // 3秒后重置，允许再次弹框
    setTimeout(() => { loginPromptShown = false }, 3000)
  }
}

async function request(path, options = {}) {
  const token = getToken()

  // 处理查询参数
  let url = `${API_BASE}${path}`
  if (options.params) {
    const searchParams = new URLSearchParams()
    Object.entries(options.params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        searchParams.append(key, value)
      }
    })
    const queryString = searchParams.toString()
    if (queryString) {
      url += (path.includes('?') ? '&' : '?') + queryString
    }
  }

  const response = await fetch(url, {
    headers: {
      ...(options.body instanceof FormData || options.body instanceof URLSearchParams
        ? {}
        : { 'Content-Type': 'application/json' }),
      ...(options.headers || {}),
      // 如果存在 token，添加到请求头（使用 Authorization，与后端配置一致）
      ...(token ? { 'Authorization': token } : {})
    },
    credentials: 'include',
    ...options
  })

  // 从响应头中获取 token 并保存（登录/注册时后端会返回）
  // Sa-Token 可能返回在 satoken 或 Authorization header 中
  const newToken = response.headers.get('satoken') || response.headers.get('Authorization')
  if (newToken) {
    saveToken(newToken)
  }

  if (!response.ok) {
    const text = await response.text()
    throw new Error(text || `Request failed: ${response.status}`)
  }

  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json')
    ? await response.json()
    : await response.text()

  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (payload.code !== 200) {
      const errorMsg = payload.msg || `Request failed: ${response.status}`
      // 如果是"用户未登录"错误，弹出登录提示
      if (errorMsg.includes('未登录') || errorMsg.includes('login') || payload.code === 401) {
        await showLoginPrompt()
      }
      throw new Error(errorMsg)
    }
    return payload.data ?? null
  }

  return payload
}

export const http = {
  get(path, options = {}) {
    return request(path, { method: 'GET', ...options })
  },
  post(path, data, options = {}) {
    return request(path, {
      method: 'POST',
      body: data ? JSON.stringify(data) : null,
      ...options
    })
  },
  delete(path, options = {}) {
    return request(path, { method: 'DELETE', ...options })
  },
  postForm(path, data) {
    return request(path, {
      method: 'POST',
      body: new URLSearchParams(data),
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded'
      },
      credentials: 'include'
    })
  },
  upload(path, formData) {
    return request(path, {
      method: 'POST',
      body: formData
    })
  }
}



