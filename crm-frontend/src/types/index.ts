export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  userId: string
  email: string
  fullName: string
  userType: 'ADMIN' | 'REGULAR'
  tenantSchema: string
}

export interface MeResponse {
  id: string
  email: string
  firstName: string
  lastName: string
  middleName?: string
  fullName: string
  phone: string
  avatarUrl?: string
  userType: 'ADMIN' | 'REGULAR'
  tenantId: string
  tenantSchema: string
  tenantPlan: 'FREE' | 'STANDARD'
  permissions: string[]
  roles: RoleInfo[]
  enabledModules: string[]
}

export interface RoleInfo { id: string; code: string; name: string }

export interface RegisterRequest {
  email: string; password: string; firstName: string; lastName: string
  middleName?: string; phone: string; userType: 'ADMIN' | 'REGULAR'
  plan?: 'FREE' | 'STANDARD'; adminEmail?: string
}

export interface LoginRequest { email: string; password: string }

export interface ApiResponse<T> {
  success: boolean; data?: T; error?: ApiError; timestamp: string
}

export interface ApiError {
  code: string; message: string; fields?: Record<string, string>
}

export type CustomerType = 'INDIVIDUAL' | 'LEGAL_ENTITY' | 'SOLE_TRADER'

export interface Customer {
  id: string; customerType: CustomerType; status: string
  createdAt: string; updatedAt: string
  personalData?: PersonalData; orgData?: OrgData
}

export interface PersonalData {
  firstName: string; lastName: string; middleName?: string
  phone: string; address?: string; position?: string
}

export interface OrgData {
  orgName: string; legalFormId?: string; inn: string
  kpp?: string; ogrn: string; address?: string
}

export interface Task {
  id: string; title: string; description?: string
  taskTypeId: string; statusId: string; authorId: string
  assigneeId?: string; customerId?: string
  scheduledAt?: string; completedAt?: string
  createdAt: string; updatedAt: string
}

/** Единый тип статуса — используется для задач, заказов и любых других сущностей.
 *  Соответствует StatusResponse DTO на бэке. */
export interface Status {
  id: string
  code: string
  name: string
  color: string
  sortOrder: number
  isFinal: boolean
  isSystem?: boolean
}

export interface Product { id: string; name: string; description: string; isActive: boolean }
export interface Role { id: string; code: string; name: string; description?: string; isSystem: boolean }
export interface Permission { id: string; code: string; name: string; description?: string; module: string }
export interface DictionaryValue { id: string; dictTypeCode: string; code: string; name: string; sortOrder: number }
export interface Page<T> { content: T[]; totalElements: number; totalPages: number; size: number; number: number }

// Алиасы для совместимости
export type PageResponse<T> = Page<T>
export type BigDecimal = number
