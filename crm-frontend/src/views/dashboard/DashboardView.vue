<template>
  <div class="dashboard animate-fade-in">

    <!-- ── Приветствие ─────────────────────────────────── -->
    <div class="greeting">
      <div class="greeting__left">
        <h1>{{ greeting }}, {{ auth.user?.firstName || 'Коллега' }} 👋</h1>
        <p class="text-muted">{{ todayFormatted }}</p>
      </div>
      <div class="greeting__actions">
        <Button
          v-if="auth.can('ORDER_CREATE') && auth.hasModule('ORDERS')"
          icon="pi pi-plus" label="Новый заказ" size="small"
          @click="$router.push('/orders')"
        />
        <Button
          v-if="auth.can('CUSTOMER_CREATE')"
          icon="pi pi-user-plus" label="Клиент" size="small" outlined
          @click="$router.push('/customers')"
        />
      </div>
    </div>

    <!-- ── Стат-карточки ────────────────────────────────── -->
    <div class="stats-row">
      <div
        v-for="card in statCards" :key="card.key"
        class="stat-card surface-card"
        :style="{ '--c': card.color }"
      >
        <div class="stat-card__icon"><i :class="card.icon" /></div>
        <div class="stat-card__body">
          <div class="stat-card__val font-mono">
            <span v-if="statsLoading" class="skel skel--val" />
            <span v-else>{{ card.value }}</span>
          </div>
          <div class="stat-card__label">{{ card.label }}</div>
        </div>
        <div v-if="card.trend !== null" class="stat-card__trend"
             :class="card.trend >= 0 ? 'trend--up' : 'trend--down'">
          <i :class="card.trend >= 0 ? 'pi pi-arrow-up' : 'pi pi-arrow-down'" />
          {{ Math.abs(card.trend) }}%
        </div>
      </div>
    </div>

    <!-- ── Основная сетка ────────────────────────────────── -->
    <div class="main-grid">

      <!-- Воронка продаж -->
      <div v-if="auth.hasModule('ORDERS') && auth.can('ORDER_VIEW')"
           class="widget widget--funnel surface-card">
        <div class="widget__header">
          <div class="widget__title">
            <i class="pi pi-filter" /> Воронка продаж
          </div>
          <RouterLink to="/orders" class="widget__link">
            Все заказы <i class="pi pi-arrow-right" />
          </RouterLink>
        </div>

        <div v-if="funnelLoading" class="funnel-skeleton">
          <div v-for="i in 5" :key="i" class="skel" style="height:28px;margin-bottom:8px" />
        </div>

        <div v-else class="funnel">
          <div v-for="(stage, idx) in funnel" :key="stage.statusCode" class="funnel-row">
            <!-- Лейбл слева -->
            <div class="funnel-row__label">{{ stage.statusName }}</div>
            <!-- Бар -->
            <div class="funnel-row__track">
              <div
                class="funnel-row__fill"
                :style="{ width: `${Math.max(stage.pct, 3)}%`, background: stage.color }"
              />
              <span class="funnel-row__count font-mono">{{ stage.orderCount }}</span>
              <span class="funnel-row__amount font-mono">{{ fmtMoney(stage.totalAmount) }}</span>
            </div>
            <!-- Конверсия -->
            <div v-if="idx > 0" class="funnel-row__conv"
                 :class="stage.conversionPct >= 50 ? 'conv--good' : 'conv--low'">
              ↓{{ stage.conversionPct }}%
            </div>
            <div v-else class="funnel-row__conv" />
          </div>

          <div class="funnel-total">
            <span class="text-muted">Конверсия NEW → DONE</span>
            <span class="font-mono" :class="totalConversion >= 30 ? 'c-success' : 'c-warning'">
              {{ totalConversion }}%
            </span>
          </div>
        </div>
      </div>

      <!-- График выручки -->
      <div v-if="auth.hasModule('ORDERS') && auth.can('ORDER_VIEW')"
           class="widget widget--revenue surface-card">
        <div class="widget__header">
          <div class="widget__title"><i class="pi pi-chart-bar" /> Выручка по месяцам</div>
          <div class="revenue-total font-mono">{{ fmtMoney(revenueTotal) }}</div>
        </div>

        <div v-if="revenueLoading" class="skel" style="height:160px;border-radius:8px" />

        <div v-else class="revenue-chart">
          <div class="revenue-bars">
            <div
              v-for="(pt, i) in revenue" :key="pt.month"
              class="revenue-bar-col"
              :title="`${pt.label}: ${fmtMoney(pt.revenue)} (${pt.orderCount} заказов)`"
            >
              <div class="revenue-bar-col__val font-mono">
                {{ i === revenue.length - 1 ? fmtMoney(pt.revenue) : '' }}
              </div>
              <div class="revenue-bar-col__bar-wrap">
                <div
                  class="revenue-bar-col__bar"
                  :class="{ 'bar--current': i === revenue.length - 1 }"
                  :style="{ height: `${Math.max((pt.revenue / revenueMax) * 100, 4)}%` }"
                />
              </div>
              <div class="revenue-bar-col__label">{{ pt.label }}</div>
            </div>
          </div>

          <div class="revenue-meta">
            <div class="revenue-meta__item">
              <span class="text-muted">Ср. заказ</span>
              <span class="font-mono">{{ fmtMoney(stats?.orders?.avgOrderAmount ?? 0) }}</span>
            </div>
            <div class="revenue-meta__item">
              <span class="text-muted">Заказов/мес.</span>
              <span class="font-mono">{{ avgOrdersPerMonth }}</span>
            </div>
            <div class="revenue-meta__item">
              <span class="text-muted">Рост м/м</span>
              <span class="font-mono" :class="momGrowth >= 0 ? 'c-success' : 'c-danger'">
                {{ momGrowth >= 0 ? '+' : '' }}{{ momGrowth }}%
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- Просроченные задачи -->
      <div v-if="auth.hasModule('TASKS') && auth.can('TASK_VIEW')"
           class="widget widget--overdue surface-card">
        <div class="widget__header">
          <div class="widget__title">
            <i class="pi pi-clock" style="color:var(--danger)" />
            Просроченные задачи
            <span v-if="(stats?.tasks?.overdue ?? 0) > 0" class="overdue-badge">
              {{ stats?.tasks?.overdue }}
            </span>
          </div>
          <RouterLink to="/tasks" class="widget__link">Все <i class="pi pi-arrow-right" /></RouterLink>
        </div>

        <div v-if="overdueLoading">
          <div v-for="i in 4" :key="i" class="skel" style="height:52px;margin-bottom:8px;border-radius:8px" />
        </div>

        <div v-else-if="overdueTasks.length === 0" class="empty-state">
          <i class="pi pi-check-circle" style="color:var(--success)" />
          Просроченных задач нет — всё под контролем!
        </div>

        <div v-else class="overdue-list">
          <div
            v-for="task in overdueTasks" :key="task.id"
            class="overdue-item"
            :class="`prio--${task.priority.toLowerCase()}`"
          >
            <i :class="priorityIcon(task.priority)" class="overdue-item__prio" />
            <div class="overdue-item__body">
              <span class="overdue-item__title">{{ task.title }}</span>
              <span class="overdue-item__meta text-muted">
                <span v-if="task.customerName">{{ task.customerName }} · </span>
                Просрочена на {{ task.daysOverdue }} {{ pluralDays(task.daysOverdue) }}
              </span>
            </div>
            <div class="overdue-item__badge font-mono"
                 :class="task.daysOverdue > 7 ? 'badge--crit' : 'badge--warn'">
              −{{ task.daysOverdue }}д
            </div>
          </div>
        </div>
      </div>

      <!-- Топ клиентов -->
      <div v-if="auth.can('CUSTOMER_VIEW')"
           class="widget widget--top surface-card">
        <div class="widget__header">
          <div class="widget__title"><i class="pi pi-star" /> Топ клиентов</div>
          <RouterLink to="/customers" class="widget__link">Все <i class="pi pi-arrow-right" /></RouterLink>
        </div>

        <div v-if="topLoading">
          <div v-for="i in 5" :key="i" class="skel" style="height:40px;margin-bottom:10px;border-radius:8px" />
        </div>

        <div v-else class="top-list">
          <div v-for="(c, idx) in topCustomers" :key="c.customerId" class="top-item">
            <span class="top-item__rank font-mono text-muted">{{ idx + 1 }}</span>
            <div class="top-item__avatar" :style="{ background: avatarColor(c.customerName) }">
              {{ initials(c.customerName) }}
            </div>
            <div class="top-item__body">
              <span class="top-item__name">{{ c.customerName }}</span>
              <span class="text-muted" style="font-size:0.75rem">{{ c.orderCount }} заказов</span>
            </div>
            <span class="top-item__rev font-mono">{{ fmtMoney(c.totalRevenue) }}</span>
          </div>
        </div>
      </div>

      <!-- Лента активности -->
      <div class="widget widget--activity surface-card" style="grid-column: span 2">
        <div class="widget__header">
          <div class="widget__title"><i class="pi pi-history" /> Последние события</div>
        </div>

        <div v-if="activityLoading" class="activity-feed">
          <div v-for="i in 6" :key="i" class="skel" style="height:36px;margin-bottom:12px;border-radius:8px" />
        </div>

        <div v-else class="activity-feed">
          <div v-for="item in activity" :key="item.id" class="activity-item">
            <div class="activity-item__dot" :style="{ background: activityColor(item.type) }">
              <i :class="activityIcon(item.type)" />
            </div>
            <div class="activity-item__body">
              <span class="activity-item__text">{{ item.description }}</span>
              <span class="activity-item__time text-muted">
                {{ fmtRelative(item.createdAt) }}
                <span v-if="item.userName"> · {{ item.userName }}</span>
              </span>
            </div>
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import Button from 'primevue/button'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ru'
import { useAuthStore } from '@/stores/auth'
import {
  dashboardApi,
  type DashboardStats, type FunnelStage, type RevenuePoint,
  type OverdueTask, type RecentActivity, type TopCustomer,
} from '@/api/dashboard'

dayjs.extend(relativeTime)
dayjs.locale('ru')

const auth = useAuthStore()

// ── Приветствие ───────────────────────────────────────────────────
const hour = new Date().getHours()
const greeting   = hour < 12 ? 'Доброе утро' : hour < 18 ? 'Добрый день' : 'Добрый вечер'
const todayFormatted = dayjs().format('dddd, D MMMM YYYY')

// ── Реактивные данные ─────────────────────────────────────────────
const stats        = ref<DashboardStats | null>(null)
const funnel       = ref<FunnelStage[]>([])
const revenue      = ref<RevenuePoint[]>([])
const overdueTasks = ref<OverdueTask[]>([])
const activity     = ref<RecentActivity[]>([])
const topCustomers = ref<TopCustomer[]>([])

const statsLoading    = ref(true)
const funnelLoading   = ref(true)
const revenueLoading  = ref(true)
const overdueLoading  = ref(true)
const activityLoading = ref(true)
const topLoading      = ref(true)

// ── Загрузка параллельно ──────────────────────────────────────────
onMounted(() => {
  dashboardApi.getStats()
    .then(d => { stats.value = d })
    .catch(useFallbackStats)
    .finally(() => { statsLoading.value = false })

  dashboardApi.getFunnel()
    .then(d => { funnel.value = d })
    .catch(() => { funnel.value = DEMO_FUNNEL })
    .finally(() => { funnelLoading.value = false })

  dashboardApi.getRevenue(6)
    .then(d => { revenue.value = d })
    .catch(() => { revenue.value = DEMO_REVENUE })
    .finally(() => { revenueLoading.value = false })

  dashboardApi.getOverdueTasks(6)
    .then(d => { overdueTasks.value = d })
    .catch(() => { overdueTasks.value = DEMO_OVERDUE })
    .finally(() => { overdueLoading.value = false })

  dashboardApi.getRecentActivity(8)
    .then(d => { activity.value = d })
    .catch(() => { activity.value = DEMO_ACTIVITY })
    .finally(() => { activityLoading.value = false })

  dashboardApi.getTopCustomers(5)
    .then(d => { topCustomers.value = d })
    .catch(() => { topCustomers.value = DEMO_TOP })
    .finally(() => { topLoading.value = false })
})

function useFallbackStats() {
  stats.value = {
    customers: { total: 60, newThisMonth: 5, growthPct: 12 },
    orders:    { total: 80, totalRevenue: 8_450_000, revenueThisMonth: 1_240_000, revenueGrowthPct: 18, avgOrderAmount: 105_625 },
    tasks:     { total: 50, overdue: 10, dueToday: 4, completedThisWeek: 7 },
    products:  { total: 25, active: 24 },
  }
}

// ── Стат-карточки ─────────────────────────────────────────────────
const statCards = computed(() => {
  const s = stats.value
  return [
    { key:'customers', label:'Клиентов',          value: s ? s.customers.total.toLocaleString('ru') : '—',    icon:'pi pi-users',        color:'#3b82f6', trend: s?.customers.growthPct ?? null },
    { key:'revenue',   label:'Выручка за месяц',  value: s ? fmtMoney(s.orders.revenueThisMonth) : '—',       icon:'pi pi-wallet',       color:'#22c55e', trend: s?.orders.revenueGrowthPct ?? null },
    { key:'orders',    label:'Заказов всего',      value: s ? s.orders.total.toLocaleString('ru') : '—',       icon:'pi pi-shopping-cart',color:'#f59e0b', trend: null },
    { key:'overdue',   label:'Просрочено задач',   value: s ? String(s.tasks.overdue) : '—',                  icon:'pi pi-clock',        color: s && s.tasks.overdue > 0 ? '#ef4444' : '#22c55e', trend: null },
  ].filter(c => {
    if (['revenue','orders'].includes(c.key) && !auth.hasModule('ORDERS')) return false
    if (c.key === 'overdue' && !auth.hasModule('TASKS')) return false
    return true
  })
})

// ── Воронка ───────────────────────────────────────────────────────
const totalConversion = computed(() => {
  const first = funnel.value.find(s => s.statusCode === 'NEW')
  const done  = funnel.value.find(s => s.statusCode === 'DONE')
  if (!first || !done || first.orderCount === 0) return 0
  return Math.round((done.orderCount / first.orderCount) * 100)
})

// ── График ────────────────────────────────────────────────────────
const revenueMax = computed(() => Math.max(...revenue.value.map(p => p.revenue), 1))
const revenueTotal = computed(() => revenue.value.reduce((s, p) => s + p.revenue, 0))
const avgOrdersPerMonth = computed(() => {
  if (!revenue.value.length) return 0
  return Math.round(revenue.value.reduce((s, p) => s + p.orderCount, 0) / revenue.value.length)
})
const momGrowth = computed(() => {
  if (revenue.value.length < 2) return 0
  const prev = revenue.value[revenue.value.length - 2].revenue
  const curr = revenue.value[revenue.value.length - 1].revenue
  if (prev === 0) return 0
  return Math.round(((curr - prev) / prev) * 100)
})

// ── Хелперы ───────────────────────────────────────────────────────
function fmtMoney(v: number) {
  if (v >= 1_000_000) return `₽${(v / 1_000_000).toFixed(1)}М`
  if (v >= 1_000)     return `₽${Math.round(v / 1_000)}К`
  return `₽${v}`
}
function fmtRelative(iso: string) { return dayjs(iso).fromNow() }
function pluralDays(n: number) {
  if (n % 10 === 1 && n % 100 !== 11) return 'день'
  if ([2,3,4].includes(n % 10) && ![12,13,14].includes(n % 100)) return 'дня'
  return 'дней'
}
function priorityIcon(p: string) {
  return { CRITICAL:'pi pi-exclamation-triangle', HIGH:'pi pi-arrow-up', MEDIUM:'pi pi-minus', LOW:'pi pi-arrow-down' }[p] ?? 'pi pi-minus'
}
function activityIcon(type: string) {
  return { ORDER_CREATED:'pi pi-shopping-cart', ORDER_STATUS_CHANGED:'pi pi-refresh', CUSTOMER_CREATED:'pi pi-user-plus', TASK_COMPLETED:'pi pi-check', TASK_CREATED:'pi pi-calendar-plus' }[type] ?? 'pi pi-info-circle'
}
function activityColor(type: string) {
  return { ORDER_CREATED:'#22c55e', ORDER_STATUS_CHANGED:'#f59e0b', CUSTOMER_CREATED:'#3b82f6', TASK_COMPLETED:'#8b5cf6', TASK_CREATED:'#06b6d4' }[type] ?? '#6b7280'
}
const AVATAR_COLORS = ['#3b82f6','#8b5cf6','#ec4899','#22c55e','#f59e0b','#06b6d4']
function avatarColor(name: string) {
  let h = 0; for (const c of name) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return AVATAR_COLORS[Math.abs(h) % AVATAR_COLORS.length]
}
function initials(name: string) {
  return name.split(/\s+/).slice(0, 2).map(w => w[0]).join('').toUpperCase()
}

// ── Демо-данные (fallback) ────────────────────────────────────────
const DEMO_FUNNEL: FunnelStage[] = [
  { statusCode:'NEW',         statusName:'Новый',    color:'#3b82f6', orderCount:18, totalAmount:2_150_000, pct:100, conversionPct:0   },
  { statusCode:'IN_PROGRESS', statusName:'В работе', color:'#f59e0b', orderCount:22, totalAmount:3_480_000, pct:82,  conversionPct:122 },
  { statusCode:'WAITING',     statusName:'Ожидает',  color:'#8b5cf6', orderCount:12, totalAmount:1_640_000, pct:55,  conversionPct:55  },
  { statusCode:'DONE',        statusName:'Выполнен', color:'#22c55e', orderCount:20, totalAmount:3_180_000, pct:38,  conversionPct:63  },
  { statusCode:'CANCELLED',   statusName:'Отменён',  color:'#ef4444', orderCount:8,  totalAmount:980_000,   pct:14,  conversionPct:29  },
]
const now = dayjs()
const DEMO_REVENUE: RevenuePoint[] = [
  { month:'', label:'Сен', revenue:780_000,  orderCount:9  },
  { month:'', label:'Окт', revenue:920_000,  orderCount:11 },
  { month:'', label:'Ноя', revenue:850_000,  orderCount:10 },
  { month:'', label:'Дек', revenue:1_120_000,orderCount:14 },
  { month:'', label:'Янв', revenue:980_000,  orderCount:12 },
  { month:'', label:'Фев', revenue:1_240_000,orderCount:15 },
]
const DEMO_OVERDUE: OverdueTask[] = [
  { id:'1', title:'СРОЧНО: устранить уязвимость CVE-2024-1234', priority:'CRITICAL', daysOverdue:2,  customerName:undefined },
  { id:'2', title:'Обновить контракт с ООО АвтоПласт',          priority:'CRITICAL', daysOverdue:5,  customerName:'ООО АвтоПласт' },
  { id:'3', title:'Исправить баг #891 в продакшене',             priority:'CRITICAL', daysOverdue:1,  customerName:undefined },
  { id:'4', title:'Отправить отчёт по проекту ФинГрупп',         priority:'HIGH',     daysOverdue:3,  customerName:'ООО ФинГрупп' },
  { id:'5', title:'Позвонить по неоплаченному счёту',            priority:'HIGH',     daysOverdue:4,  customerName:'ООО СофтЛаб' },
  { id:'6', title:'Провести демо для ИП Громов',                 priority:'MEDIUM',   daysOverdue:2,  customerName:'ИП Громов В.А.' },
]
const DEMO_ACTIVITY: RecentActivity[] = [
  { id:'1', type:'ORDER_CREATED',        description:'Создан заказ: Мобильное приложение (₽480К)',  entityId:'71', entityType:'ORDER',    createdAt:now.subtract(30,'minute').toISOString(), userName:'И. Петров' },
  { id:'2', type:'CUSTOMER_CREATED',     description:'Новый клиент: Тамара Лукьянова',             entityId:'60', entityType:'CUSTOMER', createdAt:now.subtract(2,'hour').toISOString(),    userName:'А. Козлов' },
  { id:'3', type:'ORDER_STATUS_CHANGED', description:'Заказ #042 переведён → В работе',            entityId:'42', entityType:'ORDER',    createdAt:now.subtract(3,'hour').toISOString(),    userName:'Е. Соколова' },
  { id:'4', type:'TASK_COMPLETED',       description:'Задача выполнена: Настройка CI/CD',          entityId:'12', entityType:'TASK',     createdAt:now.subtract(4,'hour').toISOString(),    userName:'И. Петров' },
  { id:'5', type:'ORDER_CREATED',        description:'Создан заказ: Сервер HPE ProLiant (₽890К)', entityId:'63', entityType:'ORDER',    createdAt:now.subtract(1,'day').toISOString(),     userName:'М. Крылова' },
  { id:'6', type:'CUSTOMER_CREATED',     description:'Новый клиент: ООО НейроТех',                entityId:'37', entityType:'CUSTOMER', createdAt:now.subtract(3,'day').toISOString(),     userName:'А. Новиков' },
  { id:'7', type:'TASK_CREATED',         description:'Задача: Провести встречу с НейроТех',        entityId:'49', entityType:'TASK',     createdAt:now.subtract(1,'day').toISOString(),     userName:'И. Петров' },
  { id:'8', type:'ORDER_STATUS_CHANGED', description:'Заказ #005 завершён (₽580К)',               entityId:'5',  entityType:'ORDER',    createdAt:now.subtract(7,'day').toISOString(),     userName:'Е. Соколова' },
]
const DEMO_TOP: TopCustomer[] = [
  { customerId:'27', customerName:'ФГУП «Гос. Технологии»',customerType:'LEGAL', orderCount:3, totalRevenue:1_160_000 },
  { customerId:'15', customerName:'ООО «ФинГрупп»',         customerType:'LEGAL', orderCount:2, totalRevenue:925_000   },
  { customerId:'10', customerName:'ЗАО «МашПром»',          customerType:'LEGAL', orderCount:3, totalRevenue:750_000   },
  { customerId:'2',  customerName:'АО «Цифровые Решения»',  customerType:'LEGAL', orderCount:2, totalRevenue:600_000   },
  { customerId:'22', customerName:'ЗАО «СилаТок»',          customerType:'LEGAL', orderCount:2, totalRevenue:505_000   },
]
</script>

<style scoped>
.dashboard { display:flex; flex-direction:column; gap:20px; }

/* Приветствие */
.greeting { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; }
.greeting h1 { font-size:1.5rem; font-weight:700; color:var(--text-primary); letter-spacing:-0.02em; margin-bottom:3px; }
.greeting p  { font-size:0.875rem; text-transform:capitalize; }
.greeting__actions { display:flex; gap:8px; }

/* Стат-карточки */
.stats-row { display:grid; grid-template-columns:repeat(auto-fit, minmax(185px,1fr)); gap:12px; }
.stat-card {
  display:flex; align-items:center; gap:14px;
  padding:18px 20px;
  border-left:3px solid var(--c);
  transition:box-shadow var(--transition-fast);
}
.stat-card:hover { box-shadow:var(--shadow-md); }
.stat-card__icon {
  width:42px; height:42px; flex-shrink:0; border-radius:11px;
  background:color-mix(in srgb,var(--c) 12%,transparent);
  border:1px solid color-mix(in srgb,var(--c) 22%,transparent);
  display:flex; align-items:center; justify-content:center;
  color:var(--c); font-size:1.1rem;
}
.stat-card__body { flex:1; min-width:0; }
.stat-card__val  { font-size:1.375rem; font-weight:700; color:var(--text-primary); letter-spacing:-0.02em; margin-bottom:2px; }
.stat-card__label{ font-size:0.8rem; color:var(--text-muted); }
.stat-card__trend{ font-size:0.8rem; font-weight:600; display:flex; align-items:center; gap:2px; white-space:nowrap; }
.trend--up   { color:var(--success); }
.trend--down { color:var(--danger); }

/* Сетка */
.main-grid { display:grid; grid-template-columns:1fr 1fr; gap:14px; }

/* Виджет */
.widget { padding:20px; }
.widget__header { display:flex; align-items:center; justify-content:space-between; margin-bottom:18px; }
.widget__title  { display:flex; align-items:center; gap:8px; font-size:0.9375rem; font-weight:600; color:var(--text-primary); }
.widget__title .pi { font-size:14px; color:var(--accent-400); }
.widget__link   { font-size:0.8125rem; color:var(--accent-400); text-decoration:none; display:flex; align-items:center; gap:4px; }

/* Воронка */
.funnel { display:flex; flex-direction:column; gap:6px; }
.funnel-row { display:grid; grid-template-columns:90px 1fr 52px; align-items:center; gap:8px; }
.funnel-row__label { font-size:0.8rem; color:var(--text-secondary); white-space:nowrap; }
.funnel-row__track {
  position:relative; height:28px;
  background:var(--bg-elevated); border-radius:6px; overflow:hidden;
  display:flex; align-items:center;
}
.funnel-row__fill {
  position:absolute; left:0; top:0; bottom:0;
  border-radius:6px; opacity:0.75;
  transition:width 600ms cubic-bezier(.34,1.56,.64,1);
}
.funnel-row__count {
  position:relative; z-index:1;
  font-size:0.75rem; font-weight:600;
  padding:0 6px;
  color:var(--text-primary);
  text-shadow:0 1px 2px rgba(0,0,0,0.4);
}
.funnel-row__amount {
  position:relative; z-index:1;
  font-size:0.7rem; color:var(--text-secondary);
  margin-left:4px;
}
.funnel-row__conv {
  font-size:0.75rem; font-weight:600; text-align:center;
  padding:2px 6px; border-radius:10px; white-space:nowrap;
}
.conv--good { background:rgba(34,197,94,0.12); color:#22c55e; }
.conv--low  { background:rgba(239,68,68,0.10);  color:#ef4444; }

.funnel-total {
  display:flex; justify-content:space-between; align-items:center;
  padding-top:12px; border-top:1px solid var(--border-subtle);
  font-size:0.8125rem; margin-top:4px;
}

/* График */
.revenue-total { font-size:1rem; font-weight:700; color:var(--text-primary); }
.revenue-chart { display:flex; flex-direction:column; gap:12px; }
.revenue-bars {
  display:flex; align-items:flex-end; gap:6px;
  height:140px; padding-bottom:0;
}
.revenue-bar-col {
  flex:1; display:flex; flex-direction:column;
  align-items:center; gap:4px; height:100%;
  cursor:pointer;
}
.revenue-bar-col__val { font-size:0.65rem; color:var(--text-muted); font-family:var(--font-mono); min-height:14px; }
.revenue-bar-col__bar-wrap {
  flex:1; width:100%; display:flex; align-items:flex-end;
}
.revenue-bar-col__bar {
  width:100%; border-radius:5px 5px 0 0;
  background:color-mix(in srgb,var(--accent-500) 55%,transparent);
  transition:height 500ms cubic-bezier(.34,1.56,.64,1), background 200ms;
}
.revenue-bar-col__bar.bar--current { background:var(--accent-500); }
.revenue-bar-col:hover .revenue-bar-col__bar { background:var(--accent-400); }
.revenue-bar-col__label { font-size:0.7rem; color:var(--text-muted); }

.revenue-meta {
  display:flex; gap:16px; padding-top:12px;
  border-top:1px solid var(--border-subtle);
}
.revenue-meta__item { display:flex; flex-direction:column; gap:2px; }
.revenue-meta__item span:first-child { font-size:0.75rem; }
.revenue-meta__item span:last-child  { font-size:0.875rem; font-weight:600; color:var(--text-primary); }

/* Просрочено */
.overdue-badge {
  background:rgba(239,68,68,0.15); color:#ef4444;
  font-size:0.75rem; font-weight:700;
  padding:1px 7px; border-radius:10px;
  border:1px solid rgba(239,68,68,0.25);
}
.overdue-list { display:flex; flex-direction:column; gap:8px; }
.overdue-item {
  display:flex; align-items:center; gap:10px;
  padding:10px 12px;
  background:var(--bg-elevated);
  border-radius:var(--radius-md);
  border:1px solid var(--border-subtle);
  border-left:3px solid transparent;
}
.prio--critical { border-left-color:#ef4444; }
.prio--high     { border-left-color:#f97316; }
.prio--medium   { border-left-color:#f59e0b; }
.prio--low      { border-left-color:#6b7280; }

.overdue-item__prio { font-size:14px; flex-shrink:0; }
.prio--critical .overdue-item__prio { color:#ef4444; }
.prio--high     .overdue-item__prio { color:#f97316; }
.prio--medium   .overdue-item__prio { color:#f59e0b; }
.prio--low      .overdue-item__prio { color:#6b7280; }

.overdue-item__body { flex:1; min-width:0; display:flex; flex-direction:column; gap:2px; }
.overdue-item__title { font-size:0.875rem; font-weight:500; color:var(--text-primary); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.overdue-item__meta  { font-size:0.75rem; }
.overdue-item__badge { font-size:0.75rem; font-weight:700; padding:2px 8px; border-radius:10px; white-space:nowrap; }
.badge--warn { background:rgba(245,158,11,0.12); color:#f59e0b; }
.badge--crit { background:rgba(239,68,68,0.12);  color:#ef4444; }

/* Топ */
.top-list { display:flex; flex-direction:column; gap:0; }
.top-item { display:flex; align-items:center; gap:10px; padding:9px 0; border-bottom:1px solid var(--border-subtle); }
.top-item:last-child { border-bottom:none; }
.top-item__rank { font-size:0.8rem; width:18px; text-align:right; flex-shrink:0; }
.top-item__avatar {
  width:32px; height:32px; border-radius:8px; flex-shrink:0;
  display:flex; align-items:center; justify-content:center;
  font-size:0.6875rem; font-weight:700; color:white;
}
.top-item__body { flex:1; min-width:0; display:flex; flex-direction:column; gap:1px; }
.top-item__name { font-size:0.875rem; font-weight:500; color:var(--text-primary); white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.top-item__rev  { font-size:0.875rem; font-weight:600; color:var(--text-primary); white-space:nowrap; }

/* Активность — горизонтально */
.widget--activity { grid-column:span 2; }
.activity-feed {
  display:grid;
  grid-template-columns:repeat(auto-fill, minmax(260px,1fr));
  gap:12px;
}
.activity-item { display:flex; align-items:flex-start; gap:10px; }
.activity-item__dot {
  width:28px; height:28px; border-radius:8px; flex-shrink:0;
  display:flex; align-items:center; justify-content:center;
  font-size:12px; color:white; opacity:0.9;
}
.activity-item__body { flex:1; min-width:0; }
.activity-item__text { font-size:0.875rem; color:var(--text-primary); line-height:1.4; display:block; }
.activity-item__time { font-size:0.75rem; margin-top:2px; }

/* Пустое */
.empty-state { display:flex; align-items:center; justify-content:center; gap:8px; padding:24px; color:var(--text-muted); font-size:0.875rem; }

/* Скелетон */
.skel {
  background:linear-gradient(90deg, var(--bg-elevated) 25%, color-mix(in srgb,var(--bg-elevated) 60%,var(--border-subtle)) 50%, var(--bg-elevated) 75%);
  background-size:200% 100%;
  animation:shimmer 1.4s infinite;
  border-radius:6px;
}
.skel--val { display:block; height:1.5rem; width:80px; }
@keyframes shimmer { to { background-position:-200% 0; } }

/* Цвета */
.c-success { color:var(--success); }
.c-warning { color:var(--warning); }
.c-danger  { color:var(--danger);  }

/* Адаптив */
@media (max-width:1100px) { .main-grid { grid-template-columns:1fr; } .widget--activity { grid-column:span 1; } }
@media (max-width:640px)  { .stats-row { grid-template-columns:1fr 1fr; } .greeting h1 { font-size:1.25rem; } }
</style>
