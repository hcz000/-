import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建 axios 实例
const http = axios.create({
  baseURL: 'http://localhost:8080', // 后端 API 地址
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截器
http.interceptors.request.use(
  (config) => {
    // 添加 token（Sa-Token 不需要 Bearer 前缀）
    const token = localStorage.getItem('adminToken')
    if (token) {
      config.headers['Authorization'] = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
http.interceptors.response.use(
  (response) => {
    const res = response.data
    // 根据实际后端响应格式调整
    if (res.code !== 200 && res.code !== 0) {
      // 处理字段级别的验证错误（格式: { email: [{ message: '...' }] }）
      if (res.data && typeof res.data === 'object') {
        const errors = res.data
        // 提取所有错误信息
        const messages = []
        for (const field in errors) {
          if (Array.isArray(errors[field])) {
            errors[field].forEach(err => {
              if (err.message) {
                messages.push(err.message)
              }
            })
          }
        }
        if (messages.length > 0) {
          ElMessage.error(messages.join('，'))
          return Promise.reject(new Error(messages.join('，')))
        }
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res.data || res
  },
  (error) => {
    if (error.response) {
      switch (error.response.status) {
        case 401:
          ElMessage.error('未授权，请重新登录')
          localStorage.removeItem('adminToken')
          window.location.href = '/login'
          break
        case 403:
          ElMessage.error('拒绝访问')
          break
        case 404:
          ElMessage.error('请求资源不存在')
          break
        case 500:
          ElMessage.error('服务器错误')
          break
        default:
          ElMessage.error(error.response.data?.message || error.message)
      }
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export { http }
