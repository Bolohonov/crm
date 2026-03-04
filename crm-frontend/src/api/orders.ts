import client from './client'
import type { ApiResponse } from '@/types'
import type { BigDecimal } from '@/types'

export interface OrderItem { id?: string; productId: string; quantity: number; price?: number }
export interface CreateOrderRequest { customerId: string; statusId?: string; comment?: string; items: OrderItem[] }
export interface OrderItemResponse { id: string; productId: string; productName: string; productSku?: string; productUnit?: string; quantity: number; price: number; totalPrice: number }
export interface OrderResponse { id: string; customerId: string; customerName?: string; authorId: string; authorName?: string; statusId: string; statusName?: string; statusCode?: string; statusColor?: string; comment?: string; totalAmount: number; items?: OrderItemResponse[]; createdAt: string; updatedAt: string }
export interface OrderPageResponse { content: OrderResponse[]; totalElements: number; totalPages: number; page: number; size: number }
export interface OrderStatsResponse { totalOrders: number; totalRevenue: number; newOrders: number; completedOrders: number }

export const ordersApi = {
  list:   (params = {}) => client.get<ApiResponse<OrderPageResponse>>('/orders', { params }),
  stats:  ()            => client.get<ApiResponse<OrderStatsResponse>>('/orders/stats'),
  getById:(id: string)  => client.get<ApiResponse<OrderResponse>>(`/orders/${id}`),
  create: (data: CreateOrderRequest) => client.post<ApiResponse<OrderResponse>>('/orders', data),
  update: (id: string, data: Partial<CreateOrderRequest>) => client.put<ApiResponse<OrderResponse>>(`/orders/${id}`, data),
  changeStatus: (id: string, statusId: string) => client.patch<ApiResponse<void>>(`/orders/${id}/status`, null, { params: { statusId } }),
  delete: (id: string) => client.delete<ApiResponse<void>>(`/orders/${id}`),
}
