import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 10000,
})

if (import.meta.env.VITE_ENABLE_MOCK === 'true') {
  import('./mock.js').then(({ setupMock }) => setupMock(request))
}

request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

request.interceptors.response.use(
  response => {
    const payload = response.data

    if (payload && typeof payload === 'object' && 'code' in payload && payload.code !== 0) {
      if (payload.code === 40101) {
        localStorage.removeItem('token')
        window.location.href = '/login'
      }

      const businessError = new Error(payload.message || '请求失败')
      businessError.code = payload.code
      businessError.data = payload.data
      businessError.response = response
      return Promise.reject(businessError)
    }

    return payload
  },
  error => {
    if (error.response?.status === 401 || error.response?.data?.code === 40101) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }

    const message = error.response?.data?.message || error.message || '网络错误，请稍后重试'
    const normalizedError = error instanceof Error ? error : new Error(message)
    normalizedError.message = message
    normalizedError.code = error.response?.data?.code || error.response?.status
    return Promise.reject(normalizedError)
  }
)

export default request
