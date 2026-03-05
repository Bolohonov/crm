<template>
  <div class="tasks-view animate-fade-in">

    <!-- ── Заголовок ─────────────────────────────────────────────── -->
    <div class="page-header">
      <div class="page-header__left">
        <h1 class="page-title">
          <span class="page-title__icon"><i class="pi pi-check-square" /></span>
          Задачи
        </h1>
        <p class="page-subtitle">{{ total }} задач · {{ overdueCount }} просрочено</p>
      </div>
      <div class="page-header__actions">
        <!-- Переключатель вид: список / канбан -->
        <div class="view-toggle">
          <button class="view-toggle__btn" :class="{ active: viewMode === 'list' }" @click="viewMode = 'list'" v-tooltip="'Список'">
            <i class="pi pi-list" />
          </button>
          <button class="view-toggle__btn" :class="{ active: viewMode === 'kanban' }" @click="viewMode = 'kanban'" v-tooltip="'Канбан'">
            <i class="pi pi-th-large" />
          </button>
          <button class="view-toggle__btn" :class="{ active: viewMode === 'calendar' }" @click="viewMode = 'calendar'" v-tooltip="'Календарь'">
            <i class="pi pi-calendar" />
          </button>
        </div>
        <Button v-if="can('TASK_CREATE')" icon="pi pi-plus" label="Новая задача" @click="openCreate" />
      </div>
    </div>

    <!-- ── Фильтры ────────────────────────────────────────────────── -->
    <div class="filters card">
      <IconField class="filters__search">
        <InputIcon class="pi pi-search" />
        <InputText v-model="query" placeholder="Поиск по задачам…" @input="onSearchDebounced" />
      </IconField>
      <Select
        v-model="priorityFilter"
        :options="priorityOptions"
        option-label="label"
        option-value="value"
        placeholder="Приоритет"
        show-clear
        style="width: 160px"
        @change="() => loadTasks()"
      />
      <Select
        v-model="statusFilter"
        :options="statusOptions"
        option-label="label"
        option-value="value"
        placeholder="Статус"
        show-clear
        style="width: 160px"
        @change="() => loadTasks()"
      />
      <Select
        v-model="assigneeFilter"
        :options="assigneeOptions"
        option-label="label"
        option-value="value"
        placeholder="Исполнитель"
        show-clear
        style="width: 180px"
        @change="() => loadTasks()"
      />
      <Button icon="pi pi-filter-slash" text :disabled="!hasFilters" @click="clearFilters" v-tooltip="'Сбросить'" />
    </div>

    <!-- ── Список ─────────────────────────────────────────────────── -->
    <template v-if="viewMode === 'list'">
      <div v-if="loading" class="loading-state">
        <ProgressSpinner style="width:40px;height:40px" />
      </div>

      <div v-else-if="tasks.length === 0" class="empty-card card">
        <i class="pi pi-check-circle empty-icon" />
        <p>Задачи не найдены</p>
        <Button v-if="can('TASK_CREATE')" label="Создать задачу" icon="pi pi-plus" text @click="openCreate" />
      </div>

      <div v-else class="task-list">
        <div
          v-for="task in tasks"
          :key="task.id"
          class="task-row card"
          :class="[`task-row--${priority(task)}`, { 'task-row--overdue': isOverdue(task) }]"
          @click="openTask(task)"
        >
          <!-- Приоритет / чекбокс -->
          <div class="task-row__check" @click.stop="completeTask(task)">
            <div class="priority-dot" :style="{ background: priorityColor(task.priority) }" />
          </div>

          <!-- Заголовок -->
          <div class="task-row__body">
            <div class="task-row__title">{{ task.title }}</div>
            <div class="task-row__meta">
              <span v-if="task.customerName" class="meta-tag"><i class="pi pi-building" />{{ task.customerName }}</span>
              <span v-if="task.assigneeName" class="meta-tag"><i class="pi pi-user" />{{ task.assigneeName }}</span>
              <span v-if="task.scheduledAt" class="meta-tag" :class="{ 'meta-tag--danger': isOverdue(task) }">
                <i class="pi pi-clock" />{{ fmtDate(task.scheduledAt) }}
              </span>
              <span v-if="task.commentCount" class="meta-tag"><i class="pi pi-comment" />{{ task.commentCount }}</span>
            </div>
          </div>

          <!-- Статус -->
          <div class="task-row__status">
            <div class="status-badge" :style="{ '--c': task.statusColor }">
              <span class="status-badge__dot" />
              {{ task.statusName }}
            </div>
          </div>

          <!-- Действия -->
          <div class="task-row__actions">
            <Button icon="pi pi-ellipsis-v" text rounded size="small" @click.stop="openMenu($event, task)" />
          </div>
        </div>
      </div>

      <!-- Пагинация -->
      <div class="card pagination-card" v-if="totalPages > 1">
        <Paginator
          :rows="pageSize"
          :total-records="total"
          :first="currentPage * pageSize"
          @page="onPage"
        />
      </div>
    </template>

    <!-- ── Канбан ─────────────────────────────────────────────────── -->
    <template v-else-if="viewMode === 'kanban'">
      <div class="kanban">
        <div
          v-for="col in kanbanColumns"
          :key="col.statusId"
          class="kanban-col"
          :style="{ '--cc': col.color }"
        >
          <div class="kanban-col__header">
            <span class="kanban-col__name">{{ col.name }}</span>
            <span class="kanban-col__count">{{ col.tasks.length }}</span>
          </div>
          <!-- data-status-id используется в onDrop для определения целевой колонки -->
          <div
            class="kanban-col__body"
            :data-status-id="col.statusId"
            :ref="el => setColRef(el, col.statusId)"
          >
            <div
              v-for="task in col.tasks"
              :key="task.id"
              class="kanban-card"
              :data-task-id="task.id"
              :class="{ 'kanban-card--dragging': draggingId === task.id }"
              @click="openTask(task)"
            >
              <div class="kanban-card__priority" :style="{ background: priorityColor(task.priority) }" />
              <div class="kanban-card__title">{{ task.title }}</div>
              <div class="kanban-card__meta" v-if="task.customerName || task.scheduledAt">
                <span v-if="task.customerName">{{ task.customerName }}</span>
                <span v-if="task.scheduledAt" :class="{ overdue: isOverdue(task) }">{{ fmtDate(task.scheduledAt) }}</span>
              </div>
              <div class="kanban-card__assignee" v-if="task.assigneeName">
                <div class="avatar-xs">{{ task.assigneeName.charAt(0) }}</div>
                {{ task.assigneeName }}
              </div>
            </div>
            <button class="kanban-add" v-if="can('TASK_CREATE')" @click="openCreateInStatus(col.statusId)">
              <i class="pi pi-plus" /> Добавить
            </button>
          </div>
        </div>
      </div>
    </template>

    <!-- ── Контекстное меню ───────────────────────────────────────── -->
    <ContextMenu ref="menu" :model="menuItems" />

    <!-- ── Drawer детали ──────────────────────────────────────────── -->
    <TaskDetailDrawer
      v-model:visible="detailVisible"
      :task-id="selectedTaskId"
      :statuses="statusList"
      @updated="loadTasks"
      @deleted="onDeleted"
    />

    <!-- ── Диалог создания ────────────────────────────────────────── -->
    <TaskFormDialog
      v-model:visible="formVisible"
      :initial-status-id="newTaskStatusId"
      :statuses="statusList"
      @saved="onSaved"
    />

    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { tasksApi, type TaskResponse } from '@/api/tasks'
import { usePermission } from '@/composables/usePermission'
import TaskDetailDrawer from '@/components/tasks/TaskDetailDrawer.vue'
import TaskFormDialog from '@/components/tasks/TaskFormDialog.vue'
import TaskCalendar from '@/components/tasks/TaskCalendar.vue'
import Sortable from 'sortablejs'

const toast   = useToast()
const confirm = useConfirm()
const { can } = usePermission()

// ── Состояние ────────────────────────────────────────────────────
const tasks         = ref<TaskResponse[]>([])
const statusList    = ref<any[]>([])
const loading       = ref(false)
const total         = ref(0)
const overdueCount  = ref(0)
const totalPages    = ref(0)
const currentPage   = ref(0)
const pageSize      = ref(20)
const viewMode      = ref<'list'|'kanban'|'calendar'>('list')
const calendarDate  = ref<string | null>(null)

// ── Drag & Drop ───────────────────────────────────────────────────
const draggingId  = ref<string | null>(null)
const sortables   = new Map<string, Sortable>()   // statusId → Sortable instance
const colRefs     = new Map<string, HTMLElement>() // statusId → DOM element

/** Вызывается через :ref="el => setColRef(el, col.statusId)" */
function setColRef(el: any, statusId: string) {
  if (!el) { colRefs.delete(statusId); return }
  colRefs.set(statusId, el as HTMLElement)
}

/** Инициализируем Sortable на всех колонках после рендера */
function initSortable() {
  // Уничтожаем старые инстансы если были
  sortables.forEach(s => s.destroy())
  sortables.clear()

  colRefs.forEach((el, statusId) => {
    const s = Sortable.create(el, {
      group:     'kanban',         // общая группа — карточки перетаскиваются между колонками
      animation: 150,
      ghostClass: 'kanban-card--ghost',
      chosenClass: 'kanban-card--chosen',
      dragClass:  'kanban-card--drag',
      // Кнопку "Добавить" исключаем из перетаскивания
      filter: '.kanban-add',
      // handle: '.kanban-card__priority', // раскомментировать если тащить только за полоску
      onStart(evt: { item: HTMLElement }) {
        draggingId.value = evt.item.dataset.taskId ?? null
      },
      onEnd(evt: { item: HTMLElement }) {
        draggingId.value = null
      },
      onAdd(evt: { item: HTMLElement; to: HTMLElement; from: HTMLElement }) {
        // Карточка перемещена в другую колонку
        const taskId  = evt.item.dataset.taskId
        const toColEl = evt.to as HTMLElement
        const toStatusId = toColEl.dataset.statusId

        if (!taskId || !toStatusId) return

        // Возвращаем DOM в исходное состояние — Vue перерендерит
        evt.from.insertBefore(evt.item, evt.from.children[evt.oldIndex!] ?? null)

        moveTaskToStatus(taskId, toStatusId)
      },
    })
    sortables.set(statusId, s)
  })
}

/** Вызываем смену статуса через API, затем перезагружаем задачи */
async function moveTaskToStatus(taskId: string, newStatusId: string) {
  // Оптимистичное обновление — немедленно двигаем задачу в UI
  const task = tasks.value.find(t => t.id === taskId)
  if (!task) return

  const oldStatusId = task.statusId
  task.statusId = newStatusId  // оптимистично

  try {
    await tasksApi.changeStatus(taskId, newStatusId)
    toast.add({ severity: 'success', summary: 'Статус изменён', life: 1500 })
  } catch (err: any) {
    // Откатываем если API вернул ошибку (например, запрещённый переход)
    task.statusId = oldStatusId
    const msg = err?.response?.data?.message ?? 'Переход недопустим'
    toast.add({ severity: 'error', summary: 'Не удалось изменить статус', detail: msg, life: 3000 })
  }
}

const query          = ref('')
const priorityFilter = ref<string | null>(null)
const statusFilter   = ref<string | null>(null)
const assigneeFilter = ref<string | null>(null)

const detailVisible  = ref(false)
const selectedTaskId = ref<string | null>(null)
const formVisible    = ref(false)
const newTaskStatusId = ref<string | null>(null)
const menu           = ref()
const activeTask     = ref<any>(null)

const hasFilters = computed(() =>
  !!(query.value || priorityFilter.value || statusFilter.value || assigneeFilter.value)
)

const priorityOptions = [
  { label: '🔴 Критический', value: 'CRITICAL' },
  { label: '🟠 Высокий',     value: 'HIGH' },
  { label: '🟡 Средний',     value: 'MEDIUM' },
  { label: '🟢 Низкий',      value: 'LOW' },
]

const statusOptions = computed(() =>
  statusList.value.map(s => ({ label: s.name, value: s.id }))
)

// Заглушка — в реальном проекте подтягивать из /users
const assigneeOptions = ref<{ label: string; value: string }[]>([])

// Канбан — задачи, сгруппированные по статусам
const kanbanColumns = computed(() => {
  return statusList.value.map(s => ({
    statusId: s.id,
    name:     s.name,
    color:    s.color,
    tasks:    tasks.value.filter(t => t.statusId === s.id),
  }))
})

const menuItems = [
  { label: 'Открыть',    icon: 'pi pi-external-link', command: () => openTask(activeTask.value) },
  { separator: true },
  { label: 'Завершить',  icon: 'pi pi-check',  command: () => completeTask(activeTask.value), visible: () => can('TASK_EDIT') },
  { label: 'Удалить',    icon: 'pi pi-trash',  class: 'menu-danger', command: () => confirmDelete(activeTask.value), visible: () => can('TASK_DELETE') },
]

// ── Загрузка ─────────────────────────────────────────────────────
// Инициализируем Sortable после каждой загрузки задач в режиме канбан
async function loadTasks(page = currentPage.value) {
  // Оборачиваем существующую loadTasks — вызываем initSortable после рендера
  loading.value = true
  try {
    const params: any = { page, size: pageSize.value }
    if (statusFilter.value) params.statusId = statusFilter.value
    if (assigneeFilter.value) params.assigneeId = assigneeFilter.value

    const { data: res } = await tasksApi.list(params)
    if (res.data) {
      let items = res.data.content
      if (query.value) {
        const q = query.value.toLowerCase()
        items = items.filter(t => t.title.toLowerCase().includes(q))
      }
      if (priorityFilter.value) {
        items = items.filter(t => t.priority === priorityFilter.value)
      }
      tasks.value       = items
      total.value       = res.data.totalElements
      totalPages.value  = res.data.totalPages
      currentPage.value = page
      overdueCount.value = items.filter(isOverdue).length
    }
  } finally {
    loading.value = false
    // После рендера Vue обновит DOM — инициализируем Sortable
    if (viewMode.value === 'kanban') {
      await nextTick()
      initSortable()
    }
  }
}

async function loadStatuses() {
  try {
    const { data: res } = await tasksApi.getStatuses()
    statusList.value = res.data ?? []
  } catch {}
}

// ── Helpers ───────────────────────────────────────────────────────
function isOverdue(t: TaskResponse): boolean {
  if (!t.scheduledAt || t.statusCode === 'DONE' || t.statusCode === 'CANCELLED') return false
  return new Date(t.scheduledAt) < new Date()
}

function priority(t: TaskResponse): string { return (t.priority ?? 'MEDIUM').toLowerCase() }

function priorityColor(p?: string): string {
  return { CRITICAL: '#ef4444', HIGH: '#f59e0b', MEDIUM: '#3b82f6', LOW: '#22c55e' }[p ?? 'MEDIUM'] ?? '#94a3b8'
}

function fmtDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' })
}

function onPage(e: any) { pageSize.value = e.rows; loadTasks(e.page) }

let searchTimer: ReturnType<typeof setTimeout>
function onSearchDebounced() { clearTimeout(searchTimer); searchTimer = setTimeout(() => loadTasks(0), 300) }
function clearFilters() { query.value = ''; priorityFilter.value = null; statusFilter.value = null; assigneeFilter.value = null; loadTasks(0) }

function openTask(t: any) { selectedTaskId.value = t.id; detailVisible.value = true }
function openCreate() { newTaskStatusId.value = null; formVisible.value = true }
function openCreateInStatus(statusId: string) { newTaskStatusId.value = statusId; formVisible.value = true }
function openMenu(event: MouseEvent, t: any) { activeTask.value = t; menu.value.show(event) }

function onSaved() { formVisible.value = false; loadTasks(); toast.add({ severity: 'success', summary: 'Задача создана', life: 2500 }) }
function onDeleted() { detailVisible.value = false; loadTasks() }

async function completeTask(t: any) {
  const doneStatus = statusList.value.find(s => s.code === 'DONE' || s.isFinal)
  if (!doneStatus) return
  try {
    await tasksApi.changeStatus(t.id, doneStatus.id)
    loadTasks()
  } catch {}
}

function confirmDelete(t: any) {
  confirm.require({
    message: `Удалить задачу «${t.title}»?`, header: 'Подтверждение',
    icon: 'pi pi-exclamation-triangle', acceptClass: 'p-button-danger',
    accept: async () => {
      try { await tasksApi.delete(t.id); toast.add({ severity: 'success', summary: 'Задача удалена', life: 2500 }); loadTasks() }
      catch { toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 }) }
    },
  })
}

// При переключении на канбан — инициализируем Sortable
watch(viewMode, async (val) => {
  if (val === 'kanban') {
    await nextTick()
    initSortable()
  } else {
    sortables.forEach(s => s.destroy())
    sortables.clear()
  }
})

onBeforeUnmount(() => {
  sortables.forEach(s => s.destroy())
  sortables.clear()
})

onMounted(() => { loadStatuses(); loadTasks() })
</script>

<style scoped>
.tasks-view { display: flex; flex-direction: column; gap: 20px; }

.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.page-title  { display: flex; align-items: center; gap: 10px; font-size: 1.375rem; font-weight: 700; margin: 0 0 4px; }
.page-title__icon { width: 36px; height: 36px; border-radius: 10px; background: var(--color-primary); color: #fff; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.page-subtitle { color: var(--color-text-muted); font-size: .875rem; margin: 0; }
.page-header__actions { display: flex; gap: 8px; align-items: center; }

.view-toggle { display: flex; background: var(--color-bg-hover); border-radius: 8px; padding: 3px; gap: 2px; }
.view-toggle__btn { width: 32px; height: 32px; border: none; background: none; border-radius: 6px; display: flex; align-items: center; justify-content: center; cursor: pointer; color: var(--color-text-muted); transition: all .15s; }
.view-toggle__btn.active { background: var(--color-bg-card); color: var(--color-primary); box-shadow: 0 1px 3px rgba(0,0,0,.1); }

.filters { display: flex; align-items: center; gap: 12px; padding: 14px 16px; flex-wrap: wrap; }
.filters__search { flex: 1; min-width: 220px; }
.filters__search :deep(.p-inputtext) { width: 100%; }

.card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; }

/* ── Список ──────────────────────────────────────────────────────── */
.task-list { display: flex; flex-direction: column; gap: 6px; }

.task-row { display: flex; align-items: center; gap: 12px; padding: 12px 16px; cursor: pointer; transition: box-shadow .15s; border-left: 3px solid transparent; }
.task-row:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.task-row--critical { border-left-color: #ef4444; }
.task-row--high     { border-left-color: #f59e0b; }
.task-row--medium   { border-left-color: #3b82f6; }
.task-row--low      { border-left-color: #22c55e; }
.task-row--overdue  { background: color-mix(in srgb, #ef4444 5%, var(--color-bg-card)); }

.task-row__check { width: 28px; display: flex; justify-content: center; flex-shrink: 0; }
.priority-dot { width: 10px; height: 10px; border-radius: 50%; cursor: pointer; transition: transform .15s; }
.priority-dot:hover { transform: scale(1.3); }

.task-row__body { flex: 1; min-width: 0; }
.task-row__title { font-weight: 500; color: var(--color-text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.task-row__meta { display: flex; gap: 12px; margin-top: 3px; flex-wrap: wrap; }
.meta-tag { display: flex; align-items: center; gap: 4px; font-size: .75rem; color: var(--color-text-muted); }
.meta-tag .pi { font-size: .7rem; }
.meta-tag--danger { color: var(--color-danger); }

.task-row__status { flex-shrink: 0; }
.task-row__actions { flex-shrink: 0; }

.status-badge { display: flex; align-items: center; gap: 6px; font-size: .8rem; font-weight: 500; color: var(--c); }
.status-badge__dot { width: 7px; height: 7px; border-radius: 50%; background: var(--c); }

/* ── Канбан ──────────────────────────────────────────────────────── */
.kanban { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; align-items: start; }

.kanban-col { background: var(--color-bg-hover); border-radius: 12px; overflow: hidden; }
.kanban-col__header { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 2px solid var(--cc); }
.kanban-col__name { font-weight: 600; font-size: .875rem; color: var(--cc); }
.kanban-col__count { background: var(--cc); color: #fff; border-radius: 10px; padding: 0 8px; font-size: .75rem; font-weight: 700; }
.kanban-col__body { padding: 10px; display: flex; flex-direction: column; gap: 8px; min-height: 80px; }

.kanban-card { background: var(--color-bg-card); border-radius: 8px; padding: 10px 12px; cursor: pointer; border-left: 3px solid var(--color-border); transition: box-shadow .15s; }
.kanban-card:hover { box-shadow: 0 3px 10px rgba(0,0,0,.1); }
.kanban-card__priority { width: 100%; height: 3px; border-radius: 2px; margin-bottom: 8px; }
.kanban-card__title { font-size: .875rem; font-weight: 500; color: var(--color-text); margin-bottom: 6px; }
.kanban-card__meta { display: flex; justify-content: space-between; font-size: .75rem; color: var(--color-text-muted); margin-bottom: 6px; }
.kanban-card__meta .overdue { color: var(--color-danger); }
.kanban-card__assignee { display: flex; align-items: center; gap: 6px; font-size: .75rem; color: var(--color-text-muted); }
.avatar-xs { width: 20px; height: 20px; border-radius: 50%; background: var(--color-primary); color: #fff; display: flex; align-items: center; justify-content: center; font-size: .65rem; font-weight: 700; flex-shrink: 0; }

.kanban-add { width: 100%; padding: 8px; border: 1px dashed var(--color-border); border-radius: 8px; background: none; color: var(--color-text-muted); cursor: pointer; font-size: .8rem; display: flex; align-items: center; justify-content: center; gap: 6px; transition: all .15s; }
.kanban-add:hover { border-color: var(--color-primary); color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 5%, transparent); }

/* ── Drag & Drop ──────────────────────────────────────────────────── */
.kanban-card--ghost {
  opacity: .4;
  background: color-mix(in srgb, var(--color-primary) 10%, var(--color-bg-card));
  border: 2px dashed var(--color-primary);
}
.kanban-card--chosen {
  box-shadow: 0 8px 24px rgba(0,0,0,.15);
  transform: rotate(1.5deg);
  cursor: grabbing;
}
.kanban-card--drag {
  transform: rotate(2deg) scale(1.02);
  opacity: .95;
}
.kanban-card--dragging {
  opacity: .5;
  pointer-events: none;
}
/* Колонка подсвечивается при наведении перетаскиваемой карточки */
.kanban-col__body.sortable-drag-over {
  background: color-mix(in srgb, var(--color-primary) 5%, transparent);
  border-radius: 8px;
}

/* ── Утилиты ──────────────────────────────────────────────────────── */
.loading-state { display: flex; justify-content: center; padding: 60px; }
.empty-card { padding: 60px 20px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.empty-icon { font-size: 2.5rem; color: var(--color-text-muted); }
.empty-card p { color: var(--color-text-muted); margin: 0; }
.pagination-card { overflow: hidden; }
:deep(.p-paginator) { background: var(--color-bg-card); border: none; }
:deep(.menu-danger .p-menuitem-text),
:deep(.menu-danger .p-menuitem-icon) { color: var(--color-danger) !important; }

.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
</style>
