/**
 * 封装 axios 实例，统一配置 base URL、请求超时、
 * 认证 token 注入以及响应错误全局处理
 */
import axios from 'axios'

// 创建 axios 实例
const request = axios.create({
  // 开发环境默认走 Vite 代理，生产环境可通过环境变量覆盖
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  // 默誄10s 超时（上传接口可通过请求配置定制覆盖）
  timeout: 10000,
})

// 开发环境启用 mock
if (import.meta.env.VITE_ENABLE_MOCK === 'true') {
  import('./mock.js').then(({ setupMock }) => setupMock(request))
}

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 从 localStorage 读取 token，注入 Authorization 请求头
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  // 直接返回 data 层，封装后调用方可直接使用 res.code / res.data
  response => response.data,
  error => {
    // 401 未授权时清除 token 并跳转登录页
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default request
