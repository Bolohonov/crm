import client from './client'

// ── Types ─────────────────────────────────────────────────────────

export interface DashboardStats {
  customers: {
    total: number
    newThisMonth: number
    growthPct: number
  }
  orders: {
    total: number
    totalRevenue: number
    revenueThisMonth: number
    revenueGrowthPct: number
    avgOrderAmount: number
  }
  tasks: {
    total: number
    overdue: number
    dueToday: number
    completedThisWeek: number
  }
  products: {
    total: number
    active: number
  }
}

export interface FunnelStage {
  statusCode: string
  statusName: string
  color: string
  orderCount: number
  totalAmount: number
  pct: number          // % от первого этапа (NEW)
  conversionPct: number // % конверсии из предыдущего этапа
}

export interface RevenuePoint {
  month: string     // "2024-10"
  label: string     // "Окт 2024"
  revenue: number
  orderCount: number
}

export interface OverdueTask {
  id: string
  title: string
  priority: string
  daysOverdue: number
  customerName?: string
  assigneeName?: string
}

export interface RecentActivity {
  id: string
  type: 'ORDER_CREATED' | 'ORDER_STATUS_CHANGED' | 'CUSTOMER_CREATED' | 'TASK_COMPLETED' | 'TASK_CREATED'
  description: string
  entityId: string
  entityType: string
  createdAt: string
  userName?: string
}

export interface TopCustomer {
  customerId: string
  customerName: string
  customerType: string
  orderCount: number
  totalRevenue: number
}

// ── API ───────────────────────────────────────────────────────────

export const dashboardApi = {
  /** Сводные метрики */
  getStats(): Promise<DashboardStats> {
    return client.get('/dashboard/stats').then(r => r.data)
  },

  /** Воронка продаж по статусам заказов */
  getFunnel(): Promise<FunnelStage[]> {
    return client.get('/dashboard/funnel').then(r => r.data)
  },

  /** Выручка по месяцам (последние N месяцев) */
  getRevenue(months = 6): Promise<RevenuePoint[]> {
    return client.get('/dashboard/revenue', { params: { months } }).then(r => r.data)
  },

  /** Просроченные задачи */
  getOverdueTasks(limit = 5): Promise<OverdueTask[]> {
    return client.get('/dashboard/tasks/overdue', { params: { limit } }).then(r => r.data)
  },

  /** Лента активности */
  getRecentActivity(limit = 8): Promise<RecentActivity[]> {
    return client.get('/dashboard/activity', { params: { limit } }).then(r => r.data)
  },

  /** Топ клиентов по выручке */
  getTopCustomers(limit = 5): Promise<TopCustomer[]> {
    return client.get('/dashboard/customers/top', { params: { limit } }).then(r => r.data)
  },
}
