import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import type { ApiResponse } from '@/types'

const client: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// Request interceptor — добавляем JWT токен
client.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// Response interceptor — обработка 401 и refresh
client.interceptors.response.use(
  response => response,
  async error => {
    const original = error.config
    if (error.response?.status === 401 && !original._retry) {
      original._retry = true
      try {
        const refreshToken = localStorage.getItem('refreshToken')
        if (!refreshToken) throw new Error('No refresh token')

        const { data } = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
          '/api/v1/auth/refresh',
          { refreshToken }
        )
        if (data.data) {
          localStorage.setItem('accessToken', data.data.accessToken)
          localStorage.setItem('refreshToken', data.data.refreshToken)
          original.headers.Authorization = `Bearer ${data.data.accessToken}`
          return client(original)
        }
      } catch {
        localStorage.clear()
        window.location.href = '/auth/login'
      }
    }
    return Promise.reject(error)
  }
)

export default client
