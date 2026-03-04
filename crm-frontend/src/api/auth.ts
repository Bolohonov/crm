import client from './client'
import type { ApiResponse, AuthResponse, LoginRequest, MeResponse, RegisterRequest } from '@/types'

export const authApi = {
  register: (data: RegisterRequest) =>
    client.post<ApiResponse<{ message: string; email: string }>>('/auth/register', data),

  login: (data: LoginRequest) =>
    client.post<ApiResponse<AuthResponse>>('/auth/login', data),

  refresh: (refreshToken: string) =>
    client.post<ApiResponse<AuthResponse>>('/auth/refresh', { refreshToken }),

  logout: () =>
    client.post<ApiResponse<void>>('/auth/logout'),

  me: () =>
    client.get<ApiResponse<MeResponse>>('/auth/me'),

  verifyEmail: (token: string) =>
    client.get<ApiResponse<void>>(`/auth/verify?token=${token}`)
}
