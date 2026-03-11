import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { ApiResponse } from '@/types'

const API_BASE = import.meta.env.VITE_API_BASE_URL || '/crm/api/v1'

const client: AxiosInstance = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('crm_accessToken')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

client.interceptors.response.use(
    response => response,
    async error => {
      const original = error.config
      if (error.response?.status === 401 && !original._retry) {
        original._retry = true
        try {
          const refreshToken = localStorage.getItem('crm_refreshToken')
          if (!refreshToken) throw new Error('No refresh token')

          const { data } = await axios.post<ApiResponse<{ accessToken: string; refreshToken: string }>>(
              `${API_BASE}/auth/refresh`,
              { refreshToken }
          )
          if (data.data) {
            localStorage.setItem('crm_accessToken', data.data.accessToken)
            localStorage.setItem('crm_refreshToken', data.data.refreshToken)
            original.headers.Authorization = `Bearer ${data.data.accessToken}`
            return client(original)
          }
        } catch {
          localStorage.clear()
          window.location.href = '/crm/auth/login'
        }
      }
      return Promise.reject(error)
    }
)

export default client