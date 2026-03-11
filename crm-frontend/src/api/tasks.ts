import api from './client'
import type { ApiResponse, Status } from '@/types'

export type TaskStatus = Status   // алиас для обратной совместимости
export interface TaskType { id: string; code: string; name: string; color: string; icon?: string }
export interface TaskComment { id: string; taskId: string; content: string; authorId: string; authorName: string; createdAt: string }

export interface TaskResponse {
  id: string; title: string; description?: string; priority?: string
  taskTypeId?: string; taskTypeName?: string; taskTypeColor?: string
  statusId: string; statusName: string; statusCode: string; statusColor: string
  authorId: string; authorName: string
  assigneeId?: string; assigneeName?: string
  customerId?: string; customerName?: string
  scheduledAt?: string; completedAt?: string
  createdAt: string; updatedAt: string
  commentCount?: number
  comments?: TaskComment[]   // ← добавить
  overdue?: boolean          // ← добавить
}

export interface TaskPageResponse {
  content: TaskResponse[]
  totalElements: number; totalPages: number; page: number; size: number
}

export interface CalendarEvent {
  taskId: string; title: string; priority?: string
  statusCode: string; statusColor: string
  assigneeName?: string; customerName?: string
  start: string; end?: string
}

export interface CreateTaskRequest {
  title: string; description?: string; priority?: string
  taskTypeId?: string; statusId?: string
  assigneeId?: string; customerId?: string
  scheduledAt?: string
}

export const tasksApi = {
  list: (params: { assigneeId?: string; statusId?: string; typeId?: string; customerId?: string; page?: number; size?: number } = {}) =>
      api.get<ApiResponse<TaskPageResponse>>('/tasks', { params }),

  today: (assigneeId?: string) =>
      api.get<ApiResponse<TaskResponse[]>>('/tasks/today', { params: assigneeId ? { assigneeId } : {} }),

  calendar: (params: { from: string; to: string; assigneeId?: string }) =>
      api.get<ApiResponse<CalendarEvent[]>>('/tasks/calendar', { params }),

  getById: (id: string) =>
      api.get<ApiResponse<TaskResponse>>(`/tasks/${id}`),

  create: (data: CreateTaskRequest) =>
      api.post<ApiResponse<TaskResponse>>('/tasks', data),

  update: (id: string, data: Partial<CreateTaskRequest>) =>
      api.put<ApiResponse<TaskResponse>>(`/tasks/${id}`, data),

  delete: (id: string) =>
      api.delete<ApiResponse<void>>(`/tasks/${id}`),

  changeStatus: (id: string, statusId: string) =>
      api.patch<ApiResponse<void>>(`/tasks/${id}/status`, null, { params: { statusId } }),

  assign: (id: string, assigneeId: string) =>
      api.patch<ApiResponse<void>>(`/tasks/${id}/assign`, null, { params: { assigneeId } }),

  getComments: (id: string) =>
      api.get<ApiResponse<TaskComment[]>>(`/tasks/${id}/comments`),

  addComment: (id: string, content: string) =>
      api.post<ApiResponse<TaskComment>>(`/tasks/${id}/comments`, { content }),

  deleteComment: (taskId: string, commentId: string) =>
      api.delete<ApiResponse<void>>(`/tasks/${taskId}/comments/${commentId}`),

  getStatuses: () =>
      api.get<ApiResponse<TaskStatus[]>>('/statuses/tasks'),
}
