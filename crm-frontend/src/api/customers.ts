import api from './client'
import type { ApiResponse, Customer, Page } from '@/types'

export const customersApi = {
  search: (params: {
    query?: string; customerType?: string
    page?: number; size?: number
  }) => api.get<ApiResponse<Page<Customer>>>('/customers', { params }),

  getById: (id: string) =>
    api.get<ApiResponse<Customer>>(`/customers/${id}`),

  create: (data: Partial<Customer>) =>
    api.post<ApiResponse<Customer>>('/customers', data),

  update: (id: string, data: Partial<Customer>) =>
    api.put<ApiResponse<Customer>>(`/customers/${id}`, data),

  delete: (id: string) =>
    api.delete<ApiResponse<void>>(`/customers/${id}`)
}
