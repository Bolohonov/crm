import client from './client'
import type { ApiResponse } from '@/types'

export interface ProductResponse { id: string; name: string; description?: string; sku?: string; price: number; unit?: string; categoryId?: string; categoryName?: string; isActive: boolean; createdAt: string }
export interface ProductPageResponse { content: ProductResponse[]; totalElements: number; totalPages: number; page: number; size: number }
export interface CreateProductRequest { name: string; description?: string; sku?: string; price: number; unit?: string; categoryId?: string; isActive?: boolean }

export const productsApi = {
  list:    (params = {}) => client.get<ApiResponse<ProductPageResponse>>('/products', { params }),
  getById: (id: string)  => client.get<ApiResponse<ProductResponse>>(`/products/${id}`),
  create:  (data: CreateProductRequest) => client.post<ApiResponse<ProductResponse>>('/products', data),
  update:  (id: string, data: Partial<CreateProductRequest>) => client.put<ApiResponse<ProductResponse>>(`/products/${id}`, data),
  setActive: (id: string, active: boolean) => client.patch<ApiResponse<void>>(`/products/${id}/active`, null, { params: { active } }),
  delete:  (id: string) => client.delete<ApiResponse<void>>(`/products/${id}`),
}
