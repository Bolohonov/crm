import client from './client'
import type { ApiResponse } from '@/types'

// ── Users ─────────────────────────────────────────────────────────

export interface AdminUser {
  id: string
  email: string
  firstName: string
  lastName: string
  middleName?: string
  phone?: string
  isActive: boolean
  isEmailVerified: boolean
  createdAt: string
  lastLoginAt?: string
  roles: UserRole[]
}

export interface UserRole {
  id: string
  name: string
  code: string
  color?: string
}

export interface InviteUserRequest {
  email: string
  firstName: string
  lastName: string
  middleName?: string
  phone?: string
  roleIds: string[]
}

export interface UpdateUserRequest {
  firstName?: string
  lastName?: string
  middleName?: string
  phone?: string
  isActive?: boolean
  roleIds?: string[]
}

// ── Roles ─────────────────────────────────────────────────────────

export interface Role {
  id: string
  name: string
  code: string
  description?: string
  color?: string
  isSystem: boolean
  permissionCount: number
  userCount: number
  permissions: Permission[]
}

export interface Permission {
  id: string
  code: string
  name: string
  group: string
  description?: string
}

export interface CreateRoleRequest {
  name: string
  code: string
  description?: string
  color?: string
  permissionIds: string[]
}

// ── Page ──────────────────────────────────────────────────────────

export interface UserPageResponse {
  content: AdminUser[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export const adminApi = {
  // Users — маппинг на /users
  listUsers: (params: { query?: string; roleId?: string; isActive?: boolean; page?: number; size?: number } = {}) =>
      client.get<ApiResponse<UserPageResponse>>('/users', { params: { ...params, q: params.query } }),

  getUser: (id: string) =>
      client.get<ApiResponse<AdminUser>>(`/users/${id}`),

  inviteUser: (data: InviteUserRequest) =>
      client.post<ApiResponse<AdminUser>>('/users/invite', data),

  updateUser: (id: string, data: UpdateUserRequest) =>
      client.put<ApiResponse<AdminUser>>(`/users/${id}`, data),

  // active: true/false → status: ACTIVE/BLOCKED
  setActive: (id: string, active: boolean) =>
      client.patch<ApiResponse<void>>(`/users/${id}/status`, { status: active ? 'ACTIVE' : 'BLOCKED' }),

  assignRoles: (id: string, roleIds: string[]) =>
      client.put<ApiResponse<void>>(`/rbac/users/${id}/roles`, { roleIds }),

  deleteUser: (id: string) =>
      client.delete<ApiResponse<void>>(`/users/${id}`),

  // Roles — маппинг на /rbac/roles
  listRoles: () =>
      client.get<ApiResponse<Role[]>>('/rbac/roles'),

  getRole: (id: string) =>
      client.get<ApiResponse<Role>>(`/rbac/roles/${id}`),

  createRole: (data: CreateRoleRequest) =>
      client.post<ApiResponse<Role>>('/rbac/roles', data),

  updateRole: (id: string, data: Partial<CreateRoleRequest>) =>
      client.put<ApiResponse<Role>>(`/rbac/roles/${id}/permissions`, data),

  deleteRole: (id: string) =>
      client.delete<ApiResponse<void>>(`/rbac/roles/${id}`),

  // Permissions — маппинг на /rbac/permissions
  listPermissions: () =>
      client.get<ApiResponse<Permission[]>>('/rbac/permissions'),
}

// Алиас для совместимости с UsersView
export const rolesApi = {
  getAll: () => adminApi.listRoles(),
}
