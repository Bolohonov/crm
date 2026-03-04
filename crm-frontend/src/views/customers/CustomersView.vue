<template>
  <div class="customers-view animate-fade-in">

    <!-- ── Заголовок ─────────────────────────────────────────────── -->
    <div class="page-header">
      <div class="page-header__left">
        <h1 class="page-title">
          <span class="page-title__icon"><i class="pi pi-users" /></span>
          Клиенты
        </h1>
        <p class="page-subtitle">{{ total }} {{ pluralize(total, 'клиент', 'клиента', 'клиентов') }}</p>
      </div>
      <div class="page-header__actions">
        <Button
          v-if="can('CUSTOMER_CREATE')"
          icon="pi pi-plus"
          label="Добавить клиента"
          @click="openCreate"
        />
      </div>
    </div>

    <!-- ── Фильтры ────────────────────────────────────────────────── -->
    <div class="filters card">
      <IconField class="filters__search">
        <InputIcon class="pi pi-search" />
        <InputText
          v-model="query"
          placeholder="Поиск по имени, email, телефону…"
          @input="onSearchDebounced"
        />
      </IconField>
      <Select
        v-model="typeFilter"
        :options="typeOptions"
        option-label="label"
        option-value="value"
        placeholder="Все типы"
        show-clear
        style="width: 180px"
        @change="loadPage(0)"
      />
      <Select
        v-model="statusFilter"
        :options="statusOptions"
        option-label="label"
        option-value="value"
        placeholder="Все статусы"
        show-clear
        style="width: 160px"
        @change="loadPage(0)"
      />
    </div>

    <!-- ── Таблица ────────────────────────────────────────────────── -->
    <div class="card table-card">
      <DataTable
        :value="customers"
        :loading="loading"
        lazy
        :rows="pageSize"
        :total-records="total"
        row-hover
        striped-rows
        @row-click="openDetail"
      >
        <template #empty>
          <div class="empty-state">
            <i class="pi pi-users empty-state__icon" />
            <p>Клиенты не найдены</p>
            <Button
              v-if="can('CUSTOMER_CREATE')"
              label="Добавить первого клиента"
              icon="pi pi-plus"
              text
              @click="openCreate"
            />
          </div>
        </template>

        <Column field="displayName" header="Клиент" style="min-width: 240px">
          <template #body="{ data }">
            <div class="customer-cell">
              <div class="customer-avatar" :style="{ background: avatarColor(data) }">
                {{ avatarLetter(data) }}
              </div>
              <div class="customer-info">
                <div class="customer-info__name">{{ displayName(data) }}</div>
                <div class="customer-info__sub">{{ data.email }}</div>
              </div>
            </div>
          </template>
        </Column>

        <Column field="type" header="Тип" style="width: 140px">
          <template #body="{ data }">
            <Tag :value="typeLabel(data.type)" :severity="typeSeverity(data.type)" />
          </template>
        </Column>

        <Column field="phone" header="Телефон" style="width: 160px">
          <template #body="{ data }">
            <span class="text-muted font-mono">{{ data.phone || '—' }}</span>
          </template>
        </Column>

        <Column field="isActive" header="Статус" style="width: 110px">
          <template #body="{ data }">
            <div class="status-dot" :class="data.isActive ? 'status-dot--active' : 'status-dot--inactive'">
              <span class="status-dot__pulse" v-if="data.isActive" />
              {{ data.isActive ? 'Активен' : 'Архив' }}
            </div>
          </template>
        </Column>

        <Column field="createdAt" header="Дата" style="width: 120px">
          <template #body="{ data }">
            <span class="text-muted">{{ fmtDate(data.createdAt) }}</span>
          </template>
        </Column>

        <Column style="width: 60px">
          <template #body="{ data }">
            <Button
              icon="pi pi-ellipsis-v"
              text
              rounded
              size="small"
              @click.stop="openMenu($event, data)"
            />
          </template>
        </Column>
      </DataTable>

      <!-- Пагинация -->
      <div class="pagination" v-if="totalPages > 1">
        <Paginator
          :rows="pageSize"
          :total-records="total"
          :first="currentPage * pageSize"
          :rows-per-page-options="[20, 50, 100]"
          template="FirstPageLink PrevPageLink PageLinks NextPageLink LastPageLink RowsPerPageDropdown"
          @page="onPage"
        />
      </div>
    </div>

    <!-- ── Контекстное меню ───────────────────────────────────────── -->
    <ContextMenu ref="menu" :model="menuItems" />

    <!-- ── Диалог создания/редактирования ────────────────────────── -->
    <CustomerFormDialog
      v-model:visible="formVisible"
      :customer="editingCustomer"
      @saved="onSaved"
    />

    <!-- ── Подтверждение удаления ─────────────────────────────────── -->
    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useConfirm } from 'primevue/useconfirm'
import { useToast } from 'primevue/usetoast'
import { customersApi } from '@/api/customers'
import { usePermission } from '@/composables/usePermission'
import CustomerFormDialog from '@/components/customers/CustomerFormDialog.vue'

const router  = useRouter()
const confirm = useConfirm()
const toast   = useToast()
const { can } = usePermission()

// ── Состояние ────────────────────────────────────────────────────
const customers   = ref<any[]>([])
const loading     = ref(false)
const total       = ref(0)
const totalPages  = ref(0)
const currentPage = ref(0)
const pageSize    = ref(20)

const query        = ref('')
const typeFilter   = ref<string | null>(null)
const statusFilter = ref<string | null>(null)

const formVisible      = ref(false)
const editingCustomer  = ref<any>(null)
const menu             = ref()
const activeRow        = ref<any>(null)

// ── Справочники ──────────────────────────────────────────────────
const typeOptions = [
  { label: 'Физическое лицо', value: 'INDIVIDUAL' },
  { label: 'Юридическое лицо', value: 'LEGAL' },
  { label: 'ИП', value: 'SOLE_TRADER' },
]

const statusOptions = [
  { label: 'Активные', value: 'true' },
  { label: 'Архивные', value: 'false' },
]

const menuItems = ref([
  { label: 'Открыть', icon: 'pi pi-external-link', command: () => openDetail({ data: activeRow.value }) },
  { label: 'Редактировать', icon: 'pi pi-pencil', command: () => openEdit(activeRow.value), visible: () => can('CUSTOMER_EDIT') },
  { separator: true },
  { label: 'Удалить', icon: 'pi pi-trash', class: 'menu-danger', command: () => confirmDelete(activeRow.value), visible: () => can('CUSTOMER_DELETE') },
])

// ── Загрузка ─────────────────────────────────────────────────────
async function loadPage(page = currentPage.value) {
  loading.value = true
  try {
    const { data: res } = await customersApi.search({
      query: query.value || undefined,
      customerType: typeFilter.value || undefined,
      page,
      size: pageSize.value,
    })
    if (res.data) {
      customers.value  = res.data.content
      total.value      = res.data.totalElements
      totalPages.value = res.data.totalPages
      currentPage.value = page
    }
  } catch {
    toast.add({ severity: 'error', summary: 'Ошибка', detail: 'Не удалось загрузить клиентов', life: 3000 })
  } finally {
    loading.value = false
  }
}

// ── Дебаунс поиска ───────────────────────────────────────────────
let searchTimer: ReturnType<typeof setTimeout>
function onSearchDebounced() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadPage(0), 400)
}

// ── Пагинация ────────────────────────────────────────────────────
function onPage(e: any) {
  pageSize.value = e.rows
  loadPage(e.page)
}

// ── Навигация ────────────────────────────────────────────────────
function openDetail({ data }: any) {
  router.push(`/customers/${data.id}`)
}

function openCreate() {
  editingCustomer.value = null
  formVisible.value = true
}

function openEdit(c: any) {
  editingCustomer.value = c
  formVisible.value = true
}

function openMenu(event: MouseEvent, row: any) {
  activeRow.value = row
  menu.value.show(event)
}

// ── Сохранение ───────────────────────────────────────────────────
function onSaved() {
  formVisible.value = false
  loadPage()
  toast.add({ severity: 'success', summary: 'Сохранено', life: 2500 })
}

// ── Удаление ─────────────────────────────────────────────────────
function confirmDelete(c: any) {
  confirm.require({
    message: `Удалить клиента «${displayName(c)}»?`,
    header:  'Подтверждение',
    icon:    'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await customersApi.delete(c.id)
        toast.add({ severity: 'success', summary: 'Клиент удалён', life: 2500 })
        loadPage()
      } catch {
        toast.add({ severity: 'error', summary: 'Ошибка удаления', life: 3000 })
      }
    },
  })
}

// ── Helpers ───────────────────────────────────────────────────────
function displayName(c: any): string {
  if (c.companyName) return c.companyName
  return [c.lastName, c.firstName, c.middleName].filter(Boolean).join(' ') || c.email || '—'
}

const AVATAR_COLORS = ['#3b82f6','#8b5cf6','#ec4899','#14b8a6','#f59e0b','#ef4444','#10b981']
function avatarColor(c: any): string {
  const idx = (c.id?.charCodeAt(0) ?? 0) % AVATAR_COLORS.length
  return AVATAR_COLORS[idx]
}
function avatarLetter(c: any): string {
  return displayName(c).charAt(0).toUpperCase() || '?'
}

function typeLabel(t: string): string {
  return { INDIVIDUAL: 'Физлицо', LEGAL: 'Юрлицо', SOLE_TRADER: 'ИП' }[t] ?? t
}
function typeSeverity(t: string): string {
  return { INDIVIDUAL: 'info', LEGAL: 'success', SOLE_TRADER: 'warning' }[t] ?? 'secondary'
}

function fmtDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: '2-digit' })
}

function pluralize(n: number, one: string, few: string, many: string): string {
  const mod10 = n % 10, mod100 = n % 100
  if (mod10 === 1 && mod100 !== 11) return one
  if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) return few
  return many
}

onMounted(() => loadPage(0))
</script>

<style scoped>
.customers-view { display: flex; flex-direction: column; gap: 20px; }

.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-title { display: flex; align-items: center; gap: 10px; font-size: 1.375rem; font-weight: 700; color: var(--color-text); margin: 0 0 4px; }
.page-title__icon { width: 36px; height: 36px; border-radius: 10px; background: var(--color-primary); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 1rem; flex-shrink: 0; }
.page-subtitle { color: var(--color-text-muted); font-size: .875rem; margin: 0; }

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

.customer-cell { display: flex; align-items: center; gap: 12px; }
.customer-avatar { width: 36px; height: 36px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 700; font-size: .875rem; flex-shrink: 0; }
.customer-info__name { font-weight: 500; color: var(--color-text); font-size: .9rem; }
.customer-info__sub { font-size: .8rem; color: var(--color-text-muted); margin-top: 1px; }

.status-dot { display: flex; align-items: center; gap: 6px; font-size: .8rem; font-weight: 500; position: relative; }
.status-dot--active { color: var(--color-success); }
.status-dot--inactive { color: var(--color-text-muted); }
.status-dot__pulse { width: 7px; height: 7px; border-radius: 50%; background: currentColor; animation: pulse 2s infinite; }
@keyframes pulse { 0%,100% { opacity:1 } 50% { opacity:.4 } }

.empty-state { padding: 48px 20px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.empty-state__icon { font-size: 2.5rem; color: var(--color-text-muted); }
.empty-state p { color: var(--color-text-muted); margin: 0; }

.pagination { border-top: 1px solid var(--color-border); }
:deep(.p-paginator) { background: var(--color-bg-card); border: none; padding: 12px 16px; }

.text-muted { color: var(--color-text-muted); }
.font-mono { font-family: 'JetBrains Mono', monospace; font-size: .85em; }

:deep(.menu-danger) { color: var(--color-danger) !important; }
:deep(.menu-danger .p-menuitem-icon) { color: var(--color-danger) !important; }

.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
</style>
