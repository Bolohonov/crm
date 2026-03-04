<template>
  <div class="admin-view animate-fade-in">

    <!-- ── Заголовок ─────────────────────────────────────────────── -->
    <div class="page-header">
      <div class="page-header__left">
        <h1 class="page-title">
          <span class="page-title__icon"><i class="pi pi-cog" /></span>
          Панель администратора
        </h1>
        <p class="page-subtitle">Управление системой и экспорт данных</p>
      </div>
    </div>

    <!-- ── Навигация по разделам ──────────────────────────────────── -->
    <div class="admin-tabs card">
      <button
        v-for="tab in TABS"
        :key="tab.id"
        class="admin-tab"
        :class="{ 'admin-tab--active': activeTab === tab.id }"
        @click="activeTab = tab.id"
      >
        <i :class="tab.icon" />
        {{ tab.label }}
      </button>
    </div>

    <!-- ══════════════════════════════════════════════════════════════ -->
    <!--  ЭКСПОРТ                                                       -->
    <!-- ══════════════════════════════════════════════════════════════ -->
    <div v-if="activeTab === 'export'" class="export-section">

      <!-- Фильтры экспорта -->
      <div class="card export-filters">
        <div class="export-filters__title">
          <i class="pi pi-filter" />
          Параметры выгрузки
        </div>
        <div class="export-filters__row">
          <!-- Менеджер -->
          <div class="filter-field">
            <label class="filter-label">Менеджер</label>
            <Select
              v-model="exportManagerId"
              :options="managerOptions"
              option-label="label"
              option-value="value"
              placeholder="Все менеджеры"
              show-clear
              style="width: 220px"
            />
          </div>

          <!-- Статус заказа (только для заказов) -->
          <div class="filter-field">
            <label class="filter-label">Статус заказа</label>
            <Select
              v-model="exportOrderStatus"
              :options="orderStatusOptions"
              option-label="label"
              option-value="value"
              placeholder="Все статусы"
              show-clear
              style="width: 200px"
            />
          </div>

          <!-- Кнопка сброса -->
          <button
            class="filter-reset"
            :disabled="!exportManagerId && !exportOrderStatus"
            @click="exportManagerId = null; exportOrderStatus = null"
          >
            <i class="pi pi-filter-slash" /> Сбросить
          </button>
        </div>

        <!-- Описание фильтра -->
        <div class="filter-desc" v-if="exportManagerId || exportOrderStatus">
          <i class="pi pi-info-circle" />
          <span>
            Будут выгружены данные
            <b v-if="exportManagerId"> менеджера {{ managerLabel }}</b>
            <b v-if="exportOrderStatus"> со статусом «{{ orderStatusLabel }}»</b>
          </span>
        </div>
      </div>

      <!-- Карточки экспорта -->
      <div class="export-cards">

        <!-- ── Клиенты ──────────────────────────────────────────── -->
        <div class="export-card card">
          <div class="export-card__header">
            <div class="export-card__icon export-card__icon--customers">
              <i class="pi pi-users" />
            </div>
            <div class="export-card__info">
              <div class="export-card__title">Клиенты</div>
              <div class="export-card__desc">
                {{ exportManagerId ? `Клиенты менеджера ${managerLabel}` : 'Все клиенты системы' }}
              </div>
            </div>
          </div>

          <div class="export-card__hint">
            Поля: ФИО / организация, контакты, ИНН, менеджер, кол-во заказов, сумма
          </div>

          <div class="export-card__actions">
            <button
              class="export-btn export-btn--excel"
              :class="{ 'export-btn--loading': loadingKey === 'customers-xlsx' }"
              :disabled="!!loadingKey"
              @click="doExport('customers', 'xlsx')"
            >
              <i class="pi pi-file-excel" />
              <span>Excel</span>
              <i v-if="loadingKey === 'customers-xlsx'" class="pi pi-spin pi-spinner" />
            </button>
            <button
              class="export-btn export-btn--csv"
              :class="{ 'export-btn--loading': loadingKey === 'customers-csv' }"
              :disabled="!!loadingKey"
              @click="doExport('customers', 'csv')"
            >
              <i class="pi pi-file" />
              <span>CSV</span>
              <i v-if="loadingKey === 'customers-csv'" class="pi pi-spin pi-spinner" />
            </button>
          </div>
        </div>

        <!-- ── Заказы ───────────────────────────────────────────── -->
        <div class="export-card card">
          <div class="export-card__header">
            <div class="export-card__icon export-card__icon--orders">
              <i class="pi pi-shopping-cart" />
            </div>
            <div class="export-card__info">
              <div class="export-card__title">Заказы</div>
              <div class="export-card__desc">
                {{ exportManagerId
                    ? `Заказы менеджера ${managerLabel}`
                    : 'Все заказы системы' }}
                {{ exportOrderStatus ? ` · статус «${orderStatusLabel}»` : '' }}
              </div>
            </div>
          </div>

          <div class="export-card__hint">
            Поля: номер, дата, клиент, статус, менеджер, позиции, сумма, комментарий
          </div>

          <div class="export-card__actions">
            <button
              class="export-btn export-btn--excel"
              :class="{ 'export-btn--loading': loadingKey === 'orders-xlsx' }"
              :disabled="!!loadingKey"
              @click="doExport('orders', 'xlsx')"
            >
              <i class="pi pi-file-excel" />
              <span>Excel</span>
              <i v-if="loadingKey === 'orders-xlsx'" class="pi pi-spin pi-spinner" />
            </button>
            <button
              class="export-btn export-btn--csv"
              :class="{ 'export-btn--loading': loadingKey === 'orders-csv' }"
              :disabled="!!loadingKey"
              @click="doExport('orders', 'csv')"
            >
              <i class="pi pi-file" />
              <span>CSV</span>
              <i v-if="loadingKey === 'orders-csv'" class="pi pi-spin pi-spinner" />
            </button>
          </div>
        </div>

      </div>

      <!-- История последних выгрузок -->
      <div class="card export-history" v-if="exportHistory.length">
        <div class="export-history__title">
          <i class="pi pi-history" /> Последние выгрузки
        </div>
        <div class="export-history__list">
          <div
            v-for="h in exportHistory"
            :key="h.id"
            class="history-item"
          >
            <i :class="h.format === 'xlsx' ? 'pi pi-file-excel' : 'pi pi-file'"
               :style="{ color: h.format === 'xlsx' ? '#22c55e' : '#3b82f6' }" />
            <span class="history-item__name">{{ h.filename }}</span>
            <span class="history-item__time">{{ h.time }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ══════════════════════════════════════════════════════════════ -->
    <!--  ДРУГИЕ РАЗДЕЛЫ (заглушки — раскрываются в UsersView и др.) -->
    <!-- ══════════════════════════════════════════════════════════════ -->
    <div v-if="activeTab === 'users'" class="card placeholder-card">
      <i class="pi pi-users placeholder-icon" />
      <p>Управление пользователями — откройте через боковое меню «Пользователи»</p>
      <button class="goto-btn" @click="$router.push('/admin/users')">
        Перейти <i class="pi pi-arrow-right" />
      </button>
    </div>

    <div v-if="activeTab === 'roles'" class="card placeholder-card">
      <i class="pi pi-shield placeholder-icon" />
      <p>Управление ролями и правами — откройте через боковое меню «Роли»</p>
      <button class="goto-btn" @click="$router.push('/admin/roles')">
        Перейти <i class="pi pi-arrow-right" />
      </button>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import Select from 'primevue/select'
import { adminApi } from '@/api/admin'
import { exportApi } from '@/api/export'
import { useAppToast } from '@/composables/useAppToast'
import dayjs from 'dayjs'

const toast = useAppToast()

// ── Табы ──────────────────────────────────────────────────────────
const TABS = [
  { id: 'export', label: 'Экспорт данных', icon: 'pi pi-download' },
  { id: 'users',  label: 'Пользователи',   icon: 'pi pi-users'    },
  { id: 'roles',  label: 'Роли и права',   icon: 'pi pi-shield'   },
]
const activeTab = ref('export')

// ── Фильтры ───────────────────────────────────────────────────────
const exportManagerId    = ref<string | null>(null)
const exportOrderStatus  = ref<string | null>(null)
const managerOptions     = ref<{ label: string; value: string }[]>([])

const orderStatusOptions = [
  { label: 'Новый',       value: 'NEW'       },
  { label: 'Комплектация',value: 'PICKING'   },
  { label: 'Отгружен',   value: 'SHIPPED'   },
  { label: 'Доставлен',  value: 'DELIVERED' },
  { label: 'Архив',      value: 'ARCHIVED'  },
]

const managerLabel = computed(() =>
  managerOptions.value.find(o => o.value === exportManagerId.value)?.label ?? ''
)

const orderStatusLabel = computed(() =>
  orderStatusOptions.find(o => o.value === exportOrderStatus.value)?.label ?? ''
)

// ── Загрузка менеджеров ───────────────────────────────────────────
async function loadManagers() {
  try {
    const { data: res } = await adminApi.listUsers({ size: 200 })
    managerOptions.value = (res.data?.content ?? []).map(u => ({
      label: `${u.lastName} ${u.firstName}`,
      value: u.id,
    }))
  } catch {
    // Не критично — можно работать без фильтра по менеджеру
  }
}

// ── Экспорт ───────────────────────────────────────────────────────
const loadingKey   = ref<string | null>(null)
const exportHistory = ref<{ id: number; filename: string; format: string; time: string }[]>([])
let historyCounter = 0

async function doExport(entity: 'customers' | 'orders', format: 'xlsx' | 'csv') {
  const key = `${entity}-${format}`
  loadingKey.value = key

  try {
    const params: Record<string, string> = {}
    if (exportManagerId.value) params.managerId = exportManagerId.value
    if (entity === 'orders' && exportOrderStatus.value) {
      params.statusCode = exportOrderStatus.value
    }

    const blob = await exportApi.download(entity, format, params)

    // Собираем имя файла с датой
    const date     = dayjs().format('YYYY-MM-DD')
    const manager  = exportManagerId.value
      ? `-${managerLabel.value.split(' ')[0]}`
      : ''
    const status   = entity === 'orders' && exportOrderStatus.value
      ? `-${exportOrderStatus.value.toLowerCase()}`
      : ''
    const filename = `${entity}${manager}${status}-${date}.${format}`

    // Скачиваем через временный <a>
    const url  = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href     = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)

    // История
    exportHistory.value.unshift({
      id:       ++historyCounter,
      filename,
      format,
      time: dayjs().format('HH:mm:ss'),
    })
    if (exportHistory.value.length > 10) exportHistory.value.pop()

    toast.success(`Файл ${filename} скачан`)
  } catch (err: any) {
    toast.error('Ошибка экспорта', err?.response?.data?.message ?? 'Попробуйте позже')
  } finally {
    loadingKey.value = null
  }
}

onMounted(loadManagers)
</script>

<style scoped>
.admin-view { display: flex; flex-direction: column; gap: 20px; }

/* ── Заголовок ─────────────────────────────────────────────────────── */
.page-header { display: flex; align-items: flex-start; justify-content: space-between; }
.page-title  { display: flex; align-items: center; gap: 10px; font-size: 1.375rem; font-weight: 700; margin: 0 0 4px; }
.page-title__icon { width: 36px; height: 36px; border-radius: 10px; background: var(--color-primary); color: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.page-subtitle { color: var(--color-text-muted); font-size: .875rem; margin: 0; }

/* ── Табы ──────────────────────────────────────────────────────────── */
.admin-tabs { display: flex; gap: 4px; padding: 8px; }
.admin-tab {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 16px; border-radius: 8px; border: none;
  background: none; color: var(--color-text-muted);
  font-size: .875rem; font-weight: 500; cursor: pointer;
  transition: all .15s;
}
.admin-tab:hover { background: var(--color-bg-hover); color: var(--color-text); }
.admin-tab--active { background: var(--color-primary); color: #fff; }
.admin-tab--active:hover { background: var(--color-primary); }

/* ── Фильтры ───────────────────────────────────────────────────────── */
.export-filters { padding: 18px 20px; }
.export-filters__title {
  font-size: .875rem; font-weight: 600; color: var(--color-text);
  display: flex; align-items: center; gap: 8px; margin-bottom: 14px;
}
.export-filters__title .pi { color: var(--color-primary); }
.export-filters__row { display: flex; align-items: flex-end; gap: 16px; flex-wrap: wrap; }

.filter-field { display: flex; flex-direction: column; gap: 6px; }
.filter-label { font-size: .75rem; font-weight: 600; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .04em; }

.filter-reset {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 14px; border: 1px solid var(--color-border);
  border-radius: 8px; background: none; color: var(--color-text-muted);
  font-size: .8125rem; cursor: pointer; transition: all .15s;
  align-self: flex-end;
}
.filter-reset:hover:not(:disabled) { border-color: var(--color-danger); color: var(--color-danger); }
.filter-reset:disabled { opacity: .4; cursor: not-allowed; }

.filter-desc {
  display: flex; align-items: center; gap: 8px;
  margin-top: 12px; padding: 10px 14px;
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  border-radius: 8px; border: 1px solid color-mix(in srgb, var(--color-primary) 20%, transparent);
  font-size: .8125rem; color: var(--color-text-muted);
}
.filter-desc .pi { color: var(--color-primary); flex-shrink: 0; }

/* ── Карточки экспорта ─────────────────────────────────────────────── */
.export-section { display: flex; flex-direction: column; gap: 16px; }
.export-cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(360px, 1fr)); gap: 16px; }

.export-card { padding: 20px; display: flex; flex-direction: column; gap: 14px; }

.export-card__header { display: flex; align-items: center; gap: 14px; }

.export-card__icon {
  width: 48px; height: 48px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  font-size: 1.25rem; flex-shrink: 0;
}
.export-card__icon--customers { background: color-mix(in srgb, #6366f1 15%, transparent); color: #6366f1; }
.export-card__icon--orders    { background: color-mix(in srgb, #f59e0b 15%, transparent); color: #f59e0b; }

.export-card__title { font-size: 1rem; font-weight: 700; color: var(--color-text); margin-bottom: 2px; }
.export-card__desc  { font-size: .8125rem; color: var(--color-text-muted); }

.export-card__hint {
  font-size: .75rem; color: var(--color-text-muted);
  padding: 8px 12px;
  background: var(--color-bg-hover);
  border-radius: 6px;
  border-left: 3px solid var(--color-border);
}

.export-card__actions { display: flex; gap: 10px; }

.export-btn {
  display: flex; align-items: center; gap: 7px;
  padding: 9px 18px; border-radius: 8px;
  border: 1px solid transparent;
  font-size: .875rem; font-weight: 600;
  cursor: pointer; transition: all .15s;
  flex: 1; justify-content: center;
}
.export-btn:disabled { opacity: .6; cursor: not-allowed; }
.export-btn--loading { pointer-events: none; }

.export-btn--excel {
  background: color-mix(in srgb, #22c55e 12%, transparent);
  border-color: color-mix(in srgb, #22c55e 30%, transparent);
  color: #16a34a;
}
.export-btn--excel:hover:not(:disabled) {
  background: #22c55e; color: #fff; border-color: #22c55e;
}

.export-btn--csv {
  background: color-mix(in srgb, #3b82f6 12%, transparent);
  border-color: color-mix(in srgb, #3b82f6 30%, transparent);
  color: #2563eb;
}
.export-btn--csv:hover:not(:disabled) {
  background: #3b82f6; color: #fff; border-color: #3b82f6;
}

/* ── История ───────────────────────────────────────────────────────── */
.export-history { padding: 16px 20px; }
.export-history__title {
  font-size: .8125rem; font-weight: 600; color: var(--color-text-muted);
  display: flex; align-items: center; gap: 7px; margin-bottom: 10px;
  text-transform: uppercase; letter-spacing: .04em;
}
.export-history__list { display: flex; flex-direction: column; gap: 6px; }
.history-item {
  display: flex; align-items: center; gap: 10px;
  padding: 6px 8px; border-radius: 6px;
  font-size: .8125rem;
  transition: background .12s;
}
.history-item:hover { background: var(--color-bg-hover); }
.history-item__name { flex: 1; color: var(--color-text); font-weight: 500; }
.history-item__time { color: var(--color-text-muted); font-size: .75rem; font-variant-numeric: tabular-nums; }

/* ── Заглушки других разделов ──────────────────────────────────────── */
.placeholder-card {
  padding: 60px 40px; text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 16px;
}
.placeholder-icon { font-size: 2.5rem; color: var(--color-text-muted); }
.placeholder-card p { color: var(--color-text-muted); margin: 0; }

.goto-btn {
  display: flex; align-items: center; gap: 6px;
  padding: 8px 18px; border-radius: 8px;
  border: 1px solid var(--color-primary); background: none;
  color: var(--color-primary); font-size: .875rem; font-weight: 600;
  cursor: pointer; transition: all .15s;
}
.goto-btn:hover { background: var(--color-primary); color: #fff; }

/* ── Утилиты ───────────────────────────────────────────────────────── */
.card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; }
.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity: 0; transform: translateY(6px) } to { opacity: 1; transform: none } }
</style>
