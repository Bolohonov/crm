<template>
  <div class="customer-detail animate-fade-in" v-if="customer">

    <!-- ── Шапка ──────────────────────────────────────────────────── -->
    <div class="detail-header">
      <Button icon="pi pi-arrow-left" text @click="$router.back()" class="back-btn" />

      <div class="detail-header__avatar" :style="{ background: avatarColor }">
        {{ avatarLetter }}
      </div>

      <div class="detail-header__info">
        <h1 class="detail-header__name">{{ displayName }}</h1>
        <div class="detail-header__meta">
          <Tag :value="typeLabel" :severity="typeSeverity" />
          <div class="status-dot" :class="customer.isActive ? 'status-dot--active' : 'status-dot--inactive'">
            <span class="status-dot__pulse" v-if="customer.isActive" />
            {{ customer.isActive ? 'Активен' : 'Архив' }}
          </div>
          <span class="text-muted">Создан {{ fmtDate(customer.createdAt) }}</span>
        </div>
      </div>

      <div class="detail-header__actions">
        <Button
          v-if="can('CUSTOMER_EDIT')"
          icon="pi pi-pencil" label="Редактировать"
          outlined @click="editVisible = true"
        />
        <Button
          v-if="can('TASK_CREATE')"
          icon="pi pi-plus" label="Задача"
          outlined @click="taskVisible = true"
        />
      </div>
    </div>

    <!-- ── Сводка: KPI ─────────────────────────────────────────────── -->
    <div class="kpi-row">
      <div class="kpi-card">
        <div class="kpi-card__value font-mono">{{ orders.length }}</div>
        <div class="kpi-card__label">Заказов</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card__value font-mono">{{ fmtMoney(totalRevenue) }}</div>
        <div class="kpi-card__label">Выручка</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card__value font-mono">{{ tasks.length }}</div>
        <div class="kpi-card__label">Задач</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-card__value font-mono" :class="{ 'kpi-danger': overdueTasks > 0 }">{{ overdueTasks }}</div>
        <div class="kpi-card__label">Просрочено</div>
      </div>
    </div>

    <!-- ── Вкладки ────────────────────────────────────────────────── -->
    <TabView v-model:active-index="activeTab" class="detail-tabs">

      <!-- ── Информация ───────────────────────────────────────────── -->
      <TabPanel header="Информация">
        <div class="info-grid">
          <!-- Контактная информация -->
          <div class="info-section card">
            <div class="info-section__title">Контакты</div>
            <div class="info-row" v-if="customer.email">
              <i class="pi pi-envelope info-row__icon" />
              <a :href="`mailto:${customer.email}`" class="info-row__value info-link">{{ customer.email }}</a>
            </div>
            <div class="info-row" v-if="customer.phone">
              <i class="pi pi-phone info-row__icon" />
              <a :href="`tel:${customer.phone}`" class="info-row__value info-link">{{ customer.phone }}</a>
            </div>
            <div class="info-row" v-if="customer.website">
              <i class="pi pi-globe info-row__icon" />
              <a :href="customer.website" target="_blank" rel="noopener" class="info-row__value info-link">{{ customer.website }}</a>
            </div>
            <div class="info-row" v-if="customer.address">
              <i class="pi pi-map-marker info-row__icon" />
              <span class="info-row__value">{{ customer.address }}</span>
            </div>
            <div v-if="!customer.email && !customer.phone" class="no-data">Нет контактов</div>
          </div>

          <!-- Реквизиты (для юрлиц) -->
          <div class="info-section card" v-if="customer.type === 'LEGAL' || customer.type === 'SOLE_TRADER'">
            <div class="info-section__title">Реквизиты</div>
            <div class="info-row" v-if="customer.inn">
              <span class="info-row__label">ИНН</span>
              <span class="info-row__value font-mono">{{ customer.inn }}</span>
            </div>
            <div class="info-row" v-if="customer.kpp">
              <span class="info-row__label">КПП</span>
              <span class="info-row__value font-mono">{{ customer.kpp }}</span>
            </div>
            <div class="info-row" v-if="customer.ogrn">
              <span class="info-row__label">ОГРН</span>
              <span class="info-row__value font-mono">{{ customer.ogrn }}</span>
            </div>
            <div class="info-row" v-if="customer.legalAddress">
              <span class="info-row__label">Юр. адрес</span>
              <span class="info-row__value">{{ customer.legalAddress }}</span>
            </div>
          </div>

          <!-- Дополнительно -->
          <div class="info-section card">
            <div class="info-section__title">Дополнительно</div>
            <div class="info-row" v-if="customer.responsibleName">
              <span class="info-row__label">Ответственный</span>
              <span class="info-row__value">{{ customer.responsibleName }}</span>
            </div>
            <div class="info-row" v-if="customer.source">
              <span class="info-row__label">Источник</span>
              <span class="info-row__value">{{ customer.source }}</span>
            </div>
            <div class="info-row">
              <span class="info-row__label">Обновлён</span>
              <span class="info-row__value">{{ fmtDate(customer.updatedAt) }}</span>
            </div>
          </div>

          <!-- Комментарий -->
          <div class="info-section card" v-if="customer.comment">
            <div class="info-section__title">Комментарий</div>
            <p class="comment-text">{{ customer.comment }}</p>
          </div>
        </div>
      </TabPanel>

      <!-- ── Заказы ────────────────────────────────────────────────── -->
      <TabPanel :header="`Заказы (${orders.length})`">
        <div v-if="loadingOrders" class="loading-tab">
          <ProgressSpinner style="width:32px;height:32px" />
        </div>
        <div v-else-if="orders.length === 0" class="empty-tab">
          <i class="pi pi-shopping-cart" />
          <p>У клиента нет заказов</p>
          <Button v-if="can('ORDER_CREATE')" label="Создать заказ" icon="pi pi-plus" text @click="orderVisible = true" />
        </div>
        <div v-else class="orders-list">
          <div
            v-for="order in orders"
            :key="order.id"
            class="order-row card-row"
            @click="openOrder(order)"
          >
            <div class="order-row__status">
              <div class="status-dot-sm" :style="{ background: order.statusColor }" />
              {{ order.statusName }}
            </div>
            <div class="order-row__amount font-mono">{{ fmtMoney(order.totalAmount) }}</div>
            <div class="order-row__items text-muted">{{ order.items?.length ?? 0 }} позиций</div>
            <div class="order-row__date text-muted">{{ fmtDate(order.createdAt) }}</div>
            <i class="pi pi-chevron-right text-muted" />
          </div>
        </div>
      </TabPanel>

      <!-- ── Задачи ─────────────────────────────────────────────────── -->
      <TabPanel :header="`Задачи (${tasks.length})`">
        <div v-if="loadingTasks" class="loading-tab">
          <ProgressSpinner style="width:32px;height:32px" />
        </div>
        <div v-else-if="tasks.length === 0" class="empty-tab">
          <i class="pi pi-calendar" />
          <p>У клиента нет задач</p>
          <Button v-if="can('TASK_CREATE')" label="Создать задачу" icon="pi pi-plus" text @click="taskVisible = true" />
        </div>
        <div v-else class="tasks-list">
          <div
            v-for="task in tasks"
            :key="task.id"
            class="task-row card-row"
            :class="{ 'task-row--overdue': isOverdue(task) }"
            @click="openTask(task)"
          >
            <div class="priority-dot-sm" :style="{ background: priorityColor(task.priority) }" />
            <div class="task-row__title">{{ task.title }}</div>
            <div class="task-row__status">
              <div class="status-badge" :style="{ '--c': task.statusColor }">
                <span class="status-badge__dot" />{{ task.statusName }}
              </div>
            </div>
            <div class="task-row__assignee text-muted" v-if="task.assigneeName">
              {{ task.assigneeName }}
            </div>
            <div class="task-row__date text-muted" :class="{ 'text-danger': isOverdue(task) }" v-if="task.scheduledAt">
              <i class="pi pi-clock" />{{ fmtDate(task.scheduledAt) }}
            </div>
          </div>
        </div>
      </TabPanel>

      <!-- ── Аудит ──────────────────────────────────────────────────── -->
      <TabPanel header="История">
        <div v-if="loadingAudit" class="loading-tab">
          <ProgressSpinner style="width:32px;height:32px" />
        </div>
        <div v-else-if="auditLog.length === 0" class="empty-tab">
          <i class="pi pi-history" />
          <p>История изменений пуста</p>
        </div>
        <div v-else class="audit-timeline">
          <div v-for="entry in auditLog" :key="entry.id" class="timeline-item">
            <div class="timeline-dot" :class="`timeline-dot--${entry.action.toLowerCase()}`" />
            <div class="timeline-body">
              <div class="timeline-action">{{ entry.actionLabel }}</div>
              <div class="timeline-meta">
                <span>{{ entry.actorName }}</span>
                <span>{{ fmtDateTime(entry.createdAt) }}</span>
              </div>
              <div class="timeline-comment" v-if="entry.comment">{{ entry.comment }}</div>
            </div>
          </div>
        </div>
      </TabPanel>
    </TabView>

    <!-- ── Диалоги ────────────────────────────────────────────────── -->
    <CustomerFormDialog
      v-model:visible="editVisible"
      :customer="customer"
      @saved="onCustomerSaved"
    />

    <TaskFormDialog
      v-model:visible="taskVisible"
      :initial-customer-id="customerId"
      :statuses="taskStatuses"
      @saved="loadTasks"
    />

    <OrderDetailDrawer
      v-model:visible="orderDetailVisible"
      :order-id="selectedOrderId"
      :statuses="orderStatuses"
      @status-changed="loadOrders"
    />
  </div>

  <!-- Загрузка страницы -->
  <div v-else-if="loading" class="page-loading">
    <ProgressSpinner style="width:48px;height:48px" />
  </div>

  <!-- Не найдено -->
  <div v-else class="page-not-found">
    <i class="pi pi-exclamation-circle" />
    <h2>Клиент не найден</h2>
    <Button label="Назад" text @click="$router.back()" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { customersApi } from '@/api/customers'
import { ordersApi } from '@/api/orders'
import { tasksApi } from '@/api/tasks'
import { usePermission } from '@/composables/usePermission'
import CustomerFormDialog from '@/components/customers/CustomerFormDialog.vue'
import TaskFormDialog from '@/components/tasks/TaskFormDialog.vue'
import OrderDetailDrawer from '@/components/orders/OrderDetailDrawer.vue'

const route = useRoute()
const { can } = usePermission()

const customerId = computed(() => route.params.id as string)

// ── Состояние ────────────────────────────────────────────────────
const customer    = ref<any>(null)
const orders      = ref<any[]>([])
const tasks       = ref<any[]>([])
const auditLog    = ref<any[]>([])
const taskStatuses  = ref<any[]>([])
const orderStatuses = ref<any[]>([])

const loading       = ref(false)
const loadingOrders = ref(false)
const loadingTasks  = ref(false)
const loadingAudit  = ref(false)

const activeTab          = ref(0)
const editVisible        = ref(false)
const taskVisible        = ref(false)
const orderVisible       = ref(false)
const orderDetailVisible = ref(false)
const selectedOrderId    = ref<string | null>(null)

// ── Computed ─────────────────────────────────────────────────────
const displayName = computed(() => {
  if (!customer.value) return ''
  if (customer.value.companyName) return customer.value.companyName
  return [customer.value.lastName, customer.value.firstName, customer.value.middleName].filter(Boolean).join(' ')
})

const totalRevenue = computed(() =>
  orders.value.reduce((s, o) => s + (o.totalAmount || 0), 0)
)

const overdueTasks = computed(() => tasks.value.filter(isOverdue).length)

const avatarLetter = computed(() => displayName.value.charAt(0).toUpperCase() || '?')
const COLORS = ['#3b82f6','#8b5cf6','#ec4899','#14b8a6','#f59e0b','#ef4444']
const avatarColor = computed(() => {
  const idx = (customerId.value?.charCodeAt(0) ?? 0) % COLORS.length
  return COLORS[idx]
})

const typeLabel = computed(() => ({
  INDIVIDUAL: 'Физлицо', LEGAL: 'Юрлицо', SOLE_TRADER: 'ИП',
}[customer.value?.type] ?? '—'))

const typeSeverity = computed(() => ({
  INDIVIDUAL: 'info', LEGAL: 'success', SOLE_TRADER: 'warning',
}[customer.value?.type] ?? 'secondary'))

// ── Загрузка ─────────────────────────────────────────────────────
async function loadCustomer() {
  loading.value = true
  try {
    const { data: res } = await customersApi.getById(customerId.value)
    customer.value = res.data
  } finally { loading.value = false }
}

async function loadOrders() {
  loadingOrders.value = true
  try {
    const { data: res } = await ordersApi.list({ customerId: customerId.value, size: 50 })
    orders.value = res.data?.content ?? []
  } finally { loadingOrders.value = false }
}

async function loadTasks() {
  loadingTasks.value = true
  try {
    const { data: res } = await tasksApi.list({ customerId: customerId.value, size: 50 })
    tasks.value = res.data?.content ?? []
  } finally { loadingTasks.value = false }
}

async function loadAudit() {
  loadingAudit.value = true
  try {
    const api = await import('@/api/client')
    const { data: res } = await api.default.get(`/audit/customers/${customerId.value}/timeline`)
    auditLog.value = res.data?.timeline ?? []
  } catch { auditLog.value = [] }
  finally { loadingAudit.value = false }
}

async function loadStatuses() {
  try {
    const [t, o] = await Promise.all([
      tasksApi.getStatuses(),
      ordersApi.list({ size: 1 }).then(() => import('@/api/client').then(m =>
        m.default.get('/statuses/orders').then((r: any) => r.data?.data ?? [])
      )),
    ])
    taskStatuses.value  = t.data?.data ?? []
    orderStatuses.value = Array.isArray(o) ? o : []
  } catch {}
}

// ── Helpers ───────────────────────────────────────────────────────
function isOverdue(t: any): boolean {
  if (!t.scheduledAt || t.statusCode === 'DONE') return false
  return new Date(t.scheduledAt) < new Date()
}

function priorityColor(p?: string): string {
  return { CRITICAL: '#ef4444', HIGH: '#f59e0b', MEDIUM: '#3b82f6', LOW: '#22c55e' }[p ?? 'MEDIUM'] ?? '#94a3b8'
}

function fmtDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

function fmtDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ru-RU', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function fmtMoney(n: number): string {
  if (!n) return '—'
  if (n >= 1_000_000) return `₽${(n/1_000_000).toFixed(1)}М`
  if (n >= 1_000)     return `₽${(n/1_000).toFixed(0)}К`
  return `₽${n}`
}

function openOrder(o: any) { selectedOrderId.value = o.id; orderDetailVisible.value = true }
function openTask(t: any)  { /* открыть drawer задачи */ }

function onCustomerSaved() { editVisible.value = false; loadCustomer() }

onMounted(() => {
  loadCustomer()
  loadOrders()
  loadTasks()
  loadAudit()
  loadStatuses()
})
</script>

<style scoped>
/* ── Шапка ───────────────────────────────────────────────────────── */
.customer-detail { display: flex; flex-direction: column; gap: 20px; }
.back-btn { margin-right: 4px; }

.detail-header { display: flex; align-items: center; gap: 16px; }
.detail-header__avatar { width: 56px; height: 56px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-size: 1.5rem; font-weight: 700; }
.detail-header__info { flex: 1; min-width: 0; }
.detail-header__name { font-size: 1.5rem; font-weight: 800; margin: 0 0 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.detail-header__meta { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.detail-header__actions { display: flex; gap: 8px; flex-shrink: 0; }

/* ── KPI ──────────────────────────────────────────────────────────── */
.kpi-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
@media (max-width: 640px) { .kpi-row { grid-template-columns: repeat(2, 1fr); } }

.kpi-card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; padding: 16px; text-align: center; }
.kpi-card__value { font-size: 1.625rem; font-weight: 800; color: var(--color-text); line-height: 1; margin-bottom: 4px; }
.kpi-card__label { font-size: .78rem; color: var(--color-text-muted); }
.kpi-danger      { color: var(--color-danger) !important; }

/* ── Вкладки ──────────────────────────────────────────────────────── */
.detail-tabs { background: transparent; }
:deep(.p-tabview-panels) { padding: 0; background: transparent; }
:deep(.p-tabview-nav-link) { padding: 12px 18px; font-size: .875rem; }
:deep(.p-tabview-nav-container) { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px 12px 0 0; }

/* ── Информация ───────────────────────────────────────────────────── */
.info-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; padding-top: 16px; }
.info-section { padding: 16px; }
.info-section__title { font-size: .75rem; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; color: var(--color-text-muted); margin-bottom: 12px; }
.info-row { display: flex; align-items: flex-start; gap: 10px; padding: 5px 0; border-bottom: 1px solid var(--color-border); }
.info-row:last-child { border-bottom: none; }
.info-row__icon  { font-size: .875rem; color: var(--color-primary); margin-top: 2px; width: 18px; flex-shrink: 0; }
.info-row__label { font-size: .78rem; color: var(--color-text-muted); min-width: 80px; flex-shrink: 0; }
.info-row__value { font-size: .875rem; color: var(--color-text); flex: 1; }
.info-link { color: var(--color-primary); text-decoration: none; }
.info-link:hover { text-decoration: underline; }
.no-data { font-size: .875rem; color: var(--color-text-muted); padding: 8px 0; }
.comment-text { font-size: .875rem; line-height: 1.6; margin: 0; }

/* ── Строки ──────────────────────────────────────────────────────── */
.card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; }

.card-row { display: flex; align-items: center; gap: 14px; padding: 12px 16px; cursor: pointer; border-bottom: 1px solid var(--color-border); transition: background .12s; }
.card-row:last-child { border-bottom: none; }
.card-row:hover { background: var(--color-bg-hover); }

.orders-list, .tasks-list { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; overflow: hidden; margin-top: 16px; }

.order-row__status { display: flex; align-items: center; gap: 6px; font-size: .875rem; font-weight: 500; min-width: 120px; }
.order-row__amount { font-weight: 700; min-width: 100px; }
.order-row__items  { flex: 1; }
.order-row__date   { min-width: 80px; text-align: right; }
.status-dot-sm     { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }

.task-row--overdue { background: color-mix(in srgb, #ef4444 4%, var(--color-bg-card)); }
.task-row__title    { flex: 1; font-weight: 500; }
.task-row__status   { min-width: 120px; }
.task-row__assignee { min-width: 120px; font-size: .8rem; }
.task-row__date     { font-size: .8rem; display: flex; align-items: center; gap: 4px; }
.task-row__date .pi { font-size: .75rem; }
.text-danger { color: var(--color-danger); }

.priority-dot-sm { width: 9px; height: 9px; border-radius: 50%; flex-shrink: 0; }

.status-badge { display: flex; align-items: center; gap: 5px; font-size: .8rem; font-weight: 500; color: var(--c); }
.status-badge__dot { width: 6px; height: 6px; border-radius: 50%; background: var(--c); }

/* ── Статус-точки ──────────────────────────────────────────────────── */
.status-dot { display: flex; align-items: center; gap: 6px; font-size: .82rem; font-weight: 500; }
.status-dot--active { color: var(--color-success); }
.status-dot--inactive { color: var(--color-text-muted); }
.status-dot__pulse { width: 7px; height: 7px; border-radius: 50%; background: currentColor; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:.4 } }

/* ── Аудит ────────────────────────────────────────────────────────── */
.audit-timeline { display: flex; flex-direction: column; gap: 0; padding-top: 16px; }
.timeline-item { display: flex; gap: 14px; padding: 10px 0; position: relative; }
.timeline-item:not(:last-child)::before { content: ''; position: absolute; left: 7px; top: 26px; bottom: -10px; width: 1px; background: var(--color-border); }
.timeline-dot { width: 16px; height: 16px; border-radius: 50%; flex-shrink: 0; background: var(--color-primary); margin-top: 2px; }
.timeline-dot--created        { background: var(--color-success); }
.timeline-dot--updated        { background: var(--color-primary); }
.timeline-dot--status_changed { background: var(--color-warning); }
.timeline-dot--deleted        { background: var(--color-danger); }
.timeline-body { flex: 1; }
.timeline-action { font-weight: 500; font-size: .875rem; }
.timeline-meta { display: flex; gap: 12px; font-size: .78rem; color: var(--color-text-muted); margin-top: 2px; }
.timeline-comment { font-size: .8rem; color: var(--color-text-muted); margin-top: 4px; font-style: italic; }

/* ── Утилиты ──────────────────────────────────────────────────────── */
.text-muted  { color: var(--color-text-muted); font-size: .875rem; }
.font-mono   { font-family: 'JetBrains Mono', monospace; }
.loading-tab { display: flex; justify-content: center; padding: 40px; }
.empty-tab   { padding: 48px 20px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; color: var(--color-text-muted); }
.empty-tab .pi { font-size: 2.5rem; }

.page-loading, .page-not-found { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 16px; min-height: 300px; color: var(--color-text-muted); }
.page-not-found .pi { font-size: 3rem; }
.page-not-found h2 { font-size: 1.25rem; margin: 0; color: var(--color-text); }

.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
</style>
