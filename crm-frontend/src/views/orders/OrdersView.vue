<template>
  <div class="orders-view animate-fade-in">

    <!-- ── Заголовок ─────────────────────────────────────────────── -->
    <div class="page-header">
      <div class="page-header__left">
        <h1 class="page-title">
          <span class="page-title__icon"><i class="pi pi-shopping-cart" /></span>
          Заказы
        </h1>
        <p class="page-subtitle">{{ total }} заказов · {{ fmtMoney(totalRevenue) }}</p>
      </div>
      <div class="page-header__actions">
        <Button icon="pi pi-sliders-h" label="Воронка" text @click="$router.push('/funnel')" />
        <Button
          v-if="can('ORDER_CREATE')"
          icon="pi pi-plus"
          label="Новый заказ"
          @click="openCreate"
        />
      </div>
    </div>

    <!-- ── Метрики статусов ───────────────────────────────────────── -->
    <div class="status-chips" v-if="statuses.length">
      <button
        v-for="s in statuses"
        :key="s.id"
        class="status-chip"
        :class="{ 'status-chip--active': statusFilter === s.id }"
        :style="{ '--sc': s.color }"
        @click="toggleStatus(s.id)"
      >
        <span class="status-chip__dot" />
        {{ s.name }}
        <span class="status-chip__count">{{ statusCount(s.code) }}</span>
      </button>
    </div>

    <!-- ── Фильтры ────────────────────────────────────────────────── -->
    <div class="filters card">
      <IconField class="filters__search">
        <InputIcon class="pi pi-search" />
        <InputText v-model="query" placeholder="Поиск по клиенту или комментарию…" @input="onSearchDebounced" />
      </IconField>
      <DatePicker v-model="dateFrom" placeholder="От" date-format="dd.mm.yy" show-button-bar style="width:140px" @date-select="loadPage(0)" />
      <DatePicker v-model="dateTo"   placeholder="До" date-format="dd.mm.yy" show-button-bar style="width:140px" @date-select="loadPage(0)" />
      <Button icon="pi pi-filter-slash" text :disabled="!hasFilters" @click="clearFilters" v-tooltip="'Сбросить фильтры'" />
    </div>

    <!-- ── Таблица ────────────────────────────────────────────────── -->
    <div class="card table-card">
      <DataTable
        :value="orders"
        :loading="loading"
        lazy
        row-hover
        striped-rows
        @row-click="openDetail"
      >
        <template #empty>
          <div class="empty-state">
            <i class="pi pi-shopping-cart empty-state__icon" />
            <p>Заказы не найдены</p>
          </div>
        </template>

        <!-- Номер -->
        <Column header="№" style="width: 80px">
          <template #body="{ data, index }">
            <span class="order-num font-mono">#{{ String(index + 1 + currentPage * pageSize).padStart(4, '0') }}</span>
          </template>
        </Column>

        <!-- Клиент -->
        <Column field="customerName" header="Клиент" style="min-width: 200px">
          <template #body="{ data }">
            <div class="cell-primary">{{ data.customerName || '—' }}</div>
          </template>
        </Column>

        <!-- Сумма -->
        <Column field="totalAmount" header="Сумма" style="width: 140px">
          <template #body="{ data }">
            <span class="amount font-mono">{{ fmtMoney(data.totalAmount) }}</span>
          </template>
        </Column>

        <!-- Статус -->
        <Column field="statusName" header="Статус" style="width: 160px">
          <template #body="{ data }">
            <div class="status-badge" :style="{ '--c': data.statusColor }">
              <span class="status-badge__dot" />
              {{ data.statusName }}
            </div>
          </template>
        </Column>

        <!-- Ответственный -->
        <Column field="authorName" header="Создал" style="width: 160px">
          <template #body="{ data }">
            <span class="text-muted">{{ data.authorName || '—' }}</span>
          </template>
        </Column>

        <!-- Дата -->
        <Column field="createdAt" header="Дата" style="width: 110px">
          <template #body="{ data }">
            <span class="text-muted">{{ fmtDate(data.createdAt) }}</span>
          </template>
        </Column>

        <!-- Действия -->
        <Column style="width: 60px">
          <template #body="{ data }">
            <Button icon="pi pi-ellipsis-v" text rounded size="small" @click.stop="openMenu($event, data)" />
          </template>
        </Column>
      </DataTable>

      <div class="pagination" v-if="totalPages > 1">
        <Paginator
          :rows="pageSize"
          :total-records="total"
          :first="currentPage * pageSize"
          @page="onPage"
        />
      </div>
    </div>

    <!-- ── Контекстное меню ───────────────────────────────────────── -->
    <ContextMenu ref="menu" :model="menuItems" />

    <!-- ── Drawer детали ──────────────────────────────────────────── -->
    <OrderDetailDrawer
        v-model:visible="detailVisible"
        :order-id="selectedOrderId"
        :statuses="statuses"
        @status-change="onStatusChange"
        @status-changed="loadPage"
        @deleted="onDeleted"
    />

    <!-- ── Диалог создания ────────────────────────────────────────── -->
    <OrderFormDialog
      v-model:visible="formVisible"
      :statuses="statuses"
      @saved="onSaved"
    />

    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { ordersApi } from '@/api/orders'
import { tasksApi } from '@/api/tasks'
import { usePermission } from '@/composables/usePermission'
import { useServerEvents } from '@/composables/useServerEvents'
import OrderDetailDrawer from '@/components/orders/OrderDetailDrawer.vue'
import OrderFormDialog from '@/components/orders/OrderFormDialog.vue'

const toast   = useToast()
const confirm = useConfirm()
const { can } = usePermission()

// ── Состояние ────────────────────────────────────────────────────
const orders       = ref<any[]>([])
const statuses     = ref<any[]>([])
const statsMap     = ref<Record<string, number>>({})
const loading      = ref(false)
const total        = ref(0)
const totalPages   = ref(0)
const totalRevenue = ref(0)
const currentPage  = ref(0)
const pageSize     = ref(20)

const query        = ref('')
const statusFilter = ref<string | null>(null)
const dateFrom     = ref<Date | null>(null)
const dateTo       = ref<Date | null>(null)

const detailVisible    = ref(false)
const selectedOrderId  = ref<string | null>(null)
const formVisible      = ref(false)
const menu             = ref()
const activeRow        = ref<any>(null)

const hasFilters = computed(() =>
  !!(query.value || statusFilter.value || dateFrom.value || dateTo.value)
)

// ── Меню действий ─────────────────────────────────────────────────
const menuItems = ref([
  { label: 'Открыть',       icon: 'pi pi-external-link', command: () => { selectedOrderId.value = activeRow.value?.id; detailVisible.value = true } },
  { separator: true },
  { label: 'Удалить',       icon: 'pi pi-trash', class: 'menu-danger',
    command: () => confirmDelete(activeRow.value),
    visible: () => can('ORDER_EDIT') },
])

// ── Загрузка ─────────────────────────────────────────────────────
async function loadPage(page = currentPage.value) {
  loading.value = true
  try {
    const params: any = { page, size: pageSize.value }
    if (statusFilter.value) params.statusId = statusFilter.value
    const { data: res } = await ordersApi.list(params)
    if (res.data) {
      orders.value      = res.data.content
      total.value       = res.data.totalElements
      totalPages.value  = res.data.totalPages
      currentPage.value = page
      totalRevenue.value = orders.value.reduce((s, o) => s + (o.totalAmount || 0), 0)
    }
  } finally {
    loading.value = false
  }
}

async function onStatusChange(order: any, statusId: string) {
  try {
    await ordersApi.changeStatus(order.id, statusId)
    toast.add({ severity: 'success', summary: 'Статус обновлён', life: 2500 })
    loadPage()
  } catch {
    toast.add({ severity: 'error', summary: 'Ошибка смены статуса', life: 3000 })
  }
}

async function loadStatuses() {
  try {
    const { data: res } = await ordersApi.getStatuses?.() ?? { data: { data: [] } }
    statuses.value = res.data ?? []
  } catch {}
}

// ── Helpers ───────────────────────────────────────────────────────
function statusCount(code: string) { return statsMap.value[code] ?? '' }
function toggleStatus(id: string)  { statusFilter.value = statusFilter.value === id ? null : id; loadPage(0) }
function clearFilters() { query.value = ''; statusFilter.value = null; dateFrom.value = null; dateTo.value = null; loadPage(0) }

function onPage(e: any) { pageSize.value = e.rows; loadPage(e.page) }

let searchTimer: ReturnType<typeof setTimeout>
function onSearchDebounced() { clearTimeout(searchTimer); searchTimer = setTimeout(() => loadPage(0), 400) }

function openDetail({ data }: any) { selectedOrderId.value = data.id; detailVisible.value = true }
function openCreate() { formVisible.value = true }
function openMenu(event: MouseEvent, row: any) { activeRow.value = row; menu.value.show(event) }

function onSaved() { formVisible.value = false; loadPage(); toast.add({ severity: 'success', summary: 'Заказ создан', life: 2500 }) }
function onDeleted() { detailVisible.value = false; loadPage() }

function confirmDelete(o: any) {
  confirm.require({
    message: `Удалить заказ?`, header: 'Подтверждение',
    icon: 'pi pi-exclamation-triangle', acceptClass: 'p-button-danger',
    accept: async () => {
      try { await ordersApi.delete(o.id); toast.add({ severity: 'success', summary: 'Заказ удалён', life: 2500 }); loadPage() }
      catch { toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 }) }
    },
  })
}

function fmtMoney(n: number): string {
  if (!n) return '—'
  if (n >= 1_000_000) return `₽${(n/1_000_000).toFixed(1)}М`
  if (n >= 1_000)     return `₽${(n/1_000).toFixed(0)}К`
  return `₽${n}`
}

function fmtDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

onMounted(() => {
  loadPage(0)
  loadStatuses()

  // SSE — реалтайм обновление без перезагрузки страницы
  const { onEvent } = useServerEvents()

  // Новый заказ из магазина → обновляем список и показываем тост
  onEvent('order.created', (e: any) => {
    loadPage(0)
    toast.add({
      severity: 'info',
      summary: 'Новый заказ из магазина',
      detail: e.externalOrderId
        ? `${e.externalOrderId}${e.customerName ? ' · ' + e.customerName : ''}`
        : e.customerName ?? 'Заказ получен',
      life: 5000,
    })
  })

  // Смена статуса → тихо обновляем список (без тоста — менеджер сам менял)
  onEvent('order.status_changed', () => {
    loadPage()
  })
})
</script>

<style scoped>
.orders-view { display: flex; flex-direction: column; gap: 20px; }

.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-title  { display: flex; align-items: center; gap: 10px; font-size: 1.375rem; font-weight: 700; margin: 0 0 4px; }
.page-title__icon { width: 36px; height: 36px; border-radius: 10px; background: var(--color-primary); color: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.page-subtitle { color: var(--color-text-muted); font-size: .875rem; margin: 0; }
.page-header__actions { display: flex; gap: 8px; align-items: center; }

.status-chips { display: flex; gap: 8px; flex-wrap: wrap; }
.status-chip { display: flex; align-items: center; gap: 6px; padding: 6px 14px; border-radius: 20px; border: 1px solid var(--color-border); background: var(--color-bg-card); cursor: pointer; font-size: .8rem; font-weight: 500; transition: all .15s; color: var(--color-text); }
.status-chip:hover { border-color: var(--sc); }
.status-chip--active { background: color-mix(in srgb, var(--sc) 15%, transparent); border-color: var(--sc); color: var(--sc); }
.status-chip__dot { width: 7px; height: 7px; border-radius: 50%; background: var(--sc); }
.status-chip__count { background: var(--color-border); border-radius: 10px; padding: 0 7px; font-size: .75rem; color: var(--color-text-muted); min-width: 20px; text-align: center; }

.filters { display: flex; align-items: center; gap: 12px; padding: 14px 16px; flex-wrap: wrap; }
.filters__search { flex: 1; min-width: 220px; }
.filters__search :deep(.p-inputtext) { width: 100%; }

.card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; }
.table-card { overflow: hidden; }

:deep(.p-datatable .p-datatable-thead > tr > th) {
  background: var(--color-bg-card); border-bottom: 1px solid var(--color-border);
  color: var(--color-text-muted); font-size: .75rem; font-weight: 600;
  text-transform: uppercase; letter-spacing: .04em; padding: 10px 16px;
}
:deep(.p-datatable .p-datatable-tbody > tr > td) { padding: 12px 16px; border-bottom: 1px solid var(--color-border); cursor: pointer; }
:deep(.p-datatable .p-datatable-tbody > tr:last-child > td) { border-bottom: none; }

.order-num { color: var(--color-text-muted); font-size: .85rem; }
.cell-primary { font-weight: 500; }
.amount { font-weight: 600; color: var(--color-text); }

.status-badge { display: flex; align-items: center; gap: 6px; font-size: .82rem; font-weight: 500; color: var(--c); }
.status-badge__dot { width: 7px; height: 7px; border-radius: 50%; background: var(--c); flex-shrink: 0; }

.text-muted { color: var(--color-text-muted); font-size: .875rem; }
.font-mono { font-family: 'JetBrains Mono', monospace; }

.empty-state { padding: 48px 20px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.empty-state__icon { font-size: 2.5rem; color: var(--color-text-muted); }

.pagination { border-top: 1px solid var(--color-border); }
:deep(.p-paginator) { background: var(--color-bg-card); border: none; padding: 12px 16px; }
:deep(.menu-danger .p-menuitem-text),
:deep(.menu-danger .p-menuitem-icon) { color: var(--color-danger) !important; }

.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
</style>
