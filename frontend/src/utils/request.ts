/**
 * Axios 请求封装
 * 统一处理请求/响应拦截、Token注入、错误处理
 */
import axios from 'axios'
import { showToast } from './toast'

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器 - 添加Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.satoken = token
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器 - 统一错误处理
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 如果后端返回成功
    if (res.success || res.code === 200 || res.code === 0) {
      return res
    }
    // 业务错误
    showToast(res.message || '请求失败', 'warning')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // 网络错误或服务器错误
    if (error.response) {
      const { status } = error.response
      if (!error.config?.silent) {
        switch (status) {
          case 401:
            showToast('登录已过期，请重新登录', 'warning')
            localStorage.removeItem('token')
            localStorage.removeItem('userInfo')
            break
          case 403:
            showToast('没有操作权限', 'error')
            break
          case 404:
            showToast('请求的资源不存在', 'warning')
            break
          case 500:
            showToast('服务器错误，请稍后重试', 'error')
            break
          default:
            showToast('网络错误，请重试', 'error')
        }
      }
    } else if (error.code === 'ECONNABORTED') {
      if (!error.config?.silent) showToast('请求超时，请重试', 'warning')
    } else {
      if (!error.config?.silent) showToast('网络连接失败，请检查网络', 'error')
    }
    return Promise.reject(error)
  }
)

export default request
