import api from './client'
import type { ApiResponse } from '@/types'

export interface UserResponse {
  id: string; email: string
  firstName: string; lastName: string; middleName?: string
  phone?: string; avatarUrl?: string
  userType: 'ADMIN' | 'REGULAR'
  status: 'ACTIVE' | 'BLOCKED' | 'PENDING'
  emailVerified: boolean; createdAt: string
  roles: { id: string; code: string; name: string; color?: string }[]
}

export interface UserPageResponse {
  content: UserResponse[]
  totalElements: number; totalPages: number; page: number; size: number
}

export const usersApi = {
  list: (params: { page?: number; size?: number; q?: string } = {}) =>
    api.get<ApiResponse<UserPageResponse>>('/users', { params }),

  getById: (id: string) =>
    api.get<ApiResponse<UserResponse>>(`/users/${id}`),

  update: (id: string, data: { firstName: string; lastName: string; middleName?: string; phone?: string }) =>
    api.put<ApiResponse<UserResponse>>(`/users/${id}`, data),

  setStatus: (id: string, status: 'ACTIVE' | 'BLOCKED') =>
    api.patch<ApiResponse<void>>(`/users/${id}/status`, { status }),

  changePassword: (id: string, newPassword: string) =>
    api.patch<ApiResponse<void>>(`/users/${id}/password`, { newPassword }),

  selfChangePassword: (currentPassword: string, newPassword: string) =>
    api.patch<ApiResponse<void>>('/users/me/password', { currentPassword, newPassword }),

  deactivate: (id: string) =>
    api.delete<ApiResponse<void>>(`/users/${id}`),

  // Назначение ролей через RBAC
  setRoles: (userId: string, roleIds: string[]) =>
    api.put<ApiResponse<void>>(`/roles/users/${userId}/roles`, { roleIds }),
}
