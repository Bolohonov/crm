<template>
  <div class="task-calendar card">

    <!-- ── Шапка ─────────────────────────────────────────────────── -->
    <div class="cal-header">
      <div class="cal-nav">
        <button class="cal-nav__btn" @click="prevMonth">
          <i class="pi pi-chevron-left" />
        </button>
        <span class="cal-nav__title">{{ monthTitle }}</span>
        <button class="cal-nav__btn" @click="nextMonth">
          <i class="pi pi-chevron-right" />
        </button>
      </div>

      <div class="cal-header__right">
        <button class="cal-today-btn" @click="goToday">Сегодня</button>

        <!-- Фильтр по исполнителю -->
        <Select
          v-model="assigneeFilter"
          :options="assigneeOptions"
          option-label="label"
          option-value="value"
          placeholder="Все исполнители"
          show-clear
          style="width: 180px"
          @change="loadEvents"
        />
      </div>
    </div>

    <!-- ── Дни недели ─────────────────────────────────────────────── -->
    <div class="cal-weekdays">
      <div
        v-for="d in WEEKDAYS"
        :key="d.short"
        class="cal-weekday"
        :class="{ 'cal-weekday--weekend': d.weekend }"
      >
        {{ d.short }}
      </div>
    </div>

    <!-- ── Сетка ──────────────────────────────────────────────────── -->
    <div v-if="!loading" class="cal-grid">
      <div
        v-for="cell in calendarCells"
        :key="cell.key"
        class="cal-cell"
        :class="{
          'cal-cell--other':   !cell.currentMonth,
          'cal-cell--today':    cell.isToday,
          'cal-cell--weekend':  cell.isWeekend,
          'cal-cell--has-events': cell.events.length > 0,
        }"
        @click="onDayClick(cell)"
      >
        <!-- Номер дня -->
        <div class="cal-cell__num">{{ cell.day }}</div>

        <!-- Задачи на этот день -->
        <div class="cal-cell__events">
          <div
            v-for="ev in cell.events.slice(0, maxVisible)"
            :key="ev.taskId"
            class="cal-event"
            :class="{
              'cal-event--done':    ev.statusCode === 'DONE' || ev.statusCode === 'CANCELLED',
              'cal-event--overdue': isOverdue(ev),
            }"
            :style="{ '--ev-color': priorityColor(ev.priority) }"
            @click.stop="$emit('task-click', { id: ev.taskId, title: ev.title })"
            v-tooltip.top="eventTooltip(ev)"
          >
            <span class="cal-event__dot" />
            <span class="cal-event__time" v-if="hasTime(ev.start)">
              {{ formatTime(ev.start) }}
            </span>
            <span class="cal-event__title">{{ ev.title }}</span>
          </div>

          <!-- +N скрытых -->
          <div
            v-if="cell.events.length > maxVisible"
            class="cal-event-more"
            @click.stop="openDayPopover(cell, $event)"
          >
            +{{ cell.events.length - maxVisible }} ещё
          </div>
        </div>
      </div>
    </div>

    <!-- ── Загрузка ───────────────────────────────────────────────── -->
    <div v-else class="cal-loading">
      <ProgressSpinner style="width:36px;height:36px" stroke-width="4" />
    </div>

    <!-- ── Легенда ────────────────────────────────────────────────── -->
    <div class="cal-legend">
      <div class="legend-item">
        <span class="legend-dot" style="background:var(--color-primary)" />
        Обычный приоритет
      </div>
      <div class="legend-item">
        <span class="legend-dot" style="background:#f59e0b" />
        Высокий
      </div>
      <div class="legend-item">
        <span class="legend-dot" style="background:#ef4444" />
        Критический / Просрочен
      </div>
      <div class="legend-item">
        <span class="legend-dot" style="background:var(--color-text-muted);opacity:.5" />
        Выполнен
      </div>
    </div>

    <!-- ── Popover: все задачи дня ───────────────────────────────── -->
    <Popover ref="dayPopover">
      <div v-if="popoverCell" class="day-popover">
        <div class="day-popover__header">
          <span class="day-popover__date">{{ formatCellDate(popoverCell) }}</span>
          <button
            v-if="can('TASK_CREATE')"
            class="day-popover__add"
            @click="emitCreate(popoverCell)"
          >
            <i class="pi pi-plus" /> Создать
          </button>
        </div>
        <div class="day-popover__list">
          <div
            v-for="ev in popoverCell.events"
            :key="ev.taskId"
            class="day-popover__item"
            :class="{
              'day-popover__item--done':    ev.statusCode === 'DONE',
              'day-popover__item--overdue': isOverdue(ev),
            }"
            :style="{ borderLeftColor: priorityColor(ev.priority) }"
            @click="$emit('task-click', { id: ev.taskId, title: ev.title }); dayPopover.hide()"
          >
            <div class="day-popover__item-top">
              <span class="day-popover__item-time" v-if="hasTime(ev.start)">
                {{ formatTime(ev.start) }}
              </span>
              <span class="day-popover__item-title">{{ ev.title }}</span>
            </div>
            <div class="day-popover__item-meta" v-if="ev.assigneeName || ev.customerName">
              <span v-if="ev.assigneeName">
                <i class="pi pi-user" /> {{ ev.assigneeName }}
              </span>
              <span v-if="ev.customerName">
                <i class="pi pi-building" /> {{ ev.customerName }}
              </span>
            </div>
          </div>
        </div>
      </div>
    </Popover>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import ProgressSpinner from 'primevue/progressspinner'
import Select from 'primevue/select'
import Popover from 'primevue/popover'
import { tasksApi, type CalendarEvent } from '@/api/tasks'
import { usePermission } from '@/composables/usePermission'
import { useAppToast } from '@/composables/useAppToast'
import dayjs from 'dayjs'
import 'dayjs/locale/ru'
import isSameOrBefore from 'dayjs/plugin/isSameOrBefore'
import isoWeek from 'dayjs/plugin/isoWeek'

dayjs.extend(isSameOrBefore)
dayjs.extend(isoWeek)
dayjs.locale('ru')

// ── Props / Emits ─────────────────────────────────────────────────

const props = defineProps<{
  assigneeOptions?: { label: string; value: string }[]
}>()

const emit = defineEmits<{
  'task-click': [task: { id: string; title: string }]
  'create-at':  [date: string]
}>()

// ── State ─────────────────────────────────────────────────────────

const { can }      = usePermission()
const toast        = useAppToast()
const loading      = ref(false)
const current      = ref(dayjs().startOf('month'))
const events       = ref<CalendarEvent[]>([])
const assigneeFilter = ref<string | null>(null)
const dayPopover   = ref()
const popoverCell  = ref<CalCell | null>(null)
const maxVisible   = 3

// ── Константы ─────────────────────────────────────────────────────

const WEEKDAYS = [
  { short: 'Пн', weekend: false },
  { short: 'Вт', weekend: false },
  { short: 'Ср', weekend: false },
  { short: 'Чт', weekend: false },
  { short: 'Пт', weekend: false },
  { short: 'Сб', weekend: true  },
  { short: 'Вс', weekend: true  },
]

// ── Computed ──────────────────────────────────────────────────────

const monthTitle = computed(() =>
  current.value.format('MMMM YYYY')
)

interface CalCell {
  key: string
  day: number
  date: dayjs.Dayjs
  currentMonth: boolean
  isToday: boolean
  isWeekend: boolean
  events: CalendarEvent[]
}

const calendarCells = computed((): CalCell[] => {
  const monthStart = current.value.startOf('month')
  const monthEnd   = current.value.endOf('month')
  const gridStart  = monthStart.startOf('isoWeek')
  const gridEnd    = monthEnd.endOf('isoWeek')

  const today = dayjs().startOf('day')
  const cells: CalCell[] = []
  let cur = gridStart

  while (cur.isSameOrBefore(gridEnd, 'day')) {
    const dateStr = cur.format('YYYY-MM-DD')
    const dayEvents = events.value.filter(e => {
      const d = e.start ? dayjs(e.start).format('YYYY-MM-DD') : null
      return d === dateStr
    })

    cells.push({
      key:          dateStr,
      day:          cur.date(),
      date:         cur,
      currentMonth: cur.month() === current.value.month(),
      isToday:      cur.isSame(today, 'day'),
      isWeekend:    cur.isoWeekday() >= 6,
      events:       dayEvents,
    })

    cur = cur.add(1, 'day')
  }

  return cells
})

// ── Загрузка данных ───────────────────────────────────────────────

async function loadEvents() {
  loading.value = true
  try {
    const monthStart = current.value.startOf('month')
    const monthEnd   = current.value.endOf('month')
    const from = monthStart.startOf('isoWeek').toISOString()
    const to   = monthEnd.endOf('isoWeek').toISOString()

    const { data: res } = await tasksApi.calendar({
      from,
      to,
      assigneeId: assigneeFilter.value ?? undefined,
    })
    events.value = res.data ?? []
  } catch {
    toast.error('Не удалось загрузить календарь задач')
  } finally {
    loading.value = false
  }
}

// ── Навигация ─────────────────────────────────────────────────────

function prevMonth() { current.value = current.value.subtract(1, 'month'); loadEvents() }
function nextMonth() { current.value = current.value.add(1, 'month');      loadEvents() }
function goToday()   { current.value = dayjs().startOf('month');            loadEvents() }

// ── Взаимодействие ────────────────────────────────────────────────

function onDayClick(cell: CalCell) {
  if (!cell.currentMonth) return
  // Клик по пустой ячейке — создать задачу
  if (cell.events.length === 0 && can('TASK_CREATE')) {
    emitCreate(cell)
  }
}

function emitCreate(cell: CalCell) {
  dayPopover.value?.hide()
  emit('create-at', cell.date.format('YYYY-MM-DDTHH:mm'))
}

function openDayPopover(cell: CalCell, event: MouseEvent) {
  popoverCell.value = cell
  dayPopover.value.show(event)
}

// ── Утилиты ──────────────────────────────────────────────────────

function hasTime(iso: string): boolean {
  if (!iso) return false
  const t = dayjs(iso)
  return t.hour() !== 0 || t.minute() !== 0
}

function formatTime(iso: string): string {
  return dayjs(iso).format('HH:mm')
}

function formatCellDate(cell: CalCell): string {
  return cell.date.format('D MMMM, dddd')
}

function isOverdue(ev: CalendarEvent): boolean {
  if (!ev.start) return false
  if (ev.statusCode === 'DONE' || ev.statusCode === 'CANCELLED') return false
  return dayjs(ev.start).isBefore(dayjs(), 'day')
}

function priorityColor(priority?: string): string {
  switch (priority) {
    case 'CRITICAL': return '#ef4444'
    case 'HIGH':     return '#f59e0b'
    case 'MEDIUM':   return '#3b82f6'
    case 'LOW':      return '#22c55e'
    default:         return 'var(--color-primary)'
  }
}

function eventTooltip(ev: CalendarEvent): string {
  const parts = [ev.title]
  if (ev.assigneeName)  parts.push(`Исполнитель: ${ev.assigneeName}`)
  if (ev.customerName)  parts.push(`Клиент: ${ev.customerName}`)
  if (isOverdue(ev))    parts.push('⚠ Просрочена')
  return parts.join('\n')
}

onMounted(loadEvents)
</script>

<style scoped>
.task-calendar {
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ── Шапка ────────────────────────────────────────────────────────── */
.cal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
  flex-wrap: wrap;
  gap: 12px;
}

.cal-header__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cal-nav {
  display: flex;
  align-items: center;
  gap: 4px;
}

.cal-nav__btn {
  width: 32px; height: 32px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  display: flex; align-items: center; justify-content: center;
  cursor: pointer;
  transition: all .15s;
}
.cal-nav__btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 5%, transparent);
}

.cal-nav__title {
  font-size: 1rem;
  font-weight: 700;
  color: var(--color-text);
  min-width: 175px;
  text-align: center;
  text-transform: capitalize;
  letter-spacing: -.01em;
  padding: 0 8px;
}

.cal-today-btn {
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-card);
  color: var(--color-text-muted);
  font-size: .8125rem;
  font-weight: 500;
  cursor: pointer;
  transition: all .15s;
  white-space: nowrap;
}
.cal-today-btn:hover {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

/* ── Заголовки дней ───────────────────────────────────────────────── */
.cal-weekdays {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding: 0 12px;
  border-bottom: 1px solid var(--color-border);
}

.cal-weekday {
  padding: 10px 8px;
  text-align: center;
  font-size: .6875rem;
  font-weight: 700;
  letter-spacing: .06em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}
.cal-weekday--weekend { color: #ef4444; opacity: .7; }

/* ── Сетка ────────────────────────────────────────────────────────── */
.cal-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  padding: 8px 12px 12px;
  gap: 2px;
  flex: 1;
}

.cal-cell {
  min-height: 108px;
  padding: 6px 5px;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background .12s, border-color .12s;
  position: relative;
  overflow: hidden;
}

.cal-cell:hover {
  background: var(--color-bg-hover);
  border-color: var(--color-border);
}

.cal-cell--other {
  opacity: .35;
  pointer-events: none;
}

.cal-cell--today {
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
  border-color: color-mix(in srgb, var(--color-primary) 25%, transparent) !important;
}

.cal-cell--weekend .cal-cell__num { color: #ef4444; }

.cal-cell--today .cal-cell__num {
  background: var(--color-primary);
  color: #fff;
}

/* Номер дня */
.cal-cell__num {
  width: 24px; height: 24px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: .8125rem;
  font-weight: 600;
  color: var(--color-text-muted);
  margin-bottom: 5px;
  transition: background .15s;
}

/* ── Событие ──────────────────────────────────────────────────────── */
.cal-cell__events {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.cal-event {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 5px;
  border-radius: 4px;
  background: color-mix(in srgb, var(--ev-color, var(--color-primary)) 10%, var(--color-bg-card));
  cursor: pointer;
  transition: background .12s;
  overflow: hidden;
  min-width: 0;
}

.cal-event:hover {
  background: color-mix(in srgb, var(--ev-color, var(--color-primary)) 20%, var(--color-bg-card));
}

.cal-event--done {
  opacity: .45;
}
.cal-event--done .cal-event__title {
  text-decoration: line-through;
}

.cal-event--overdue {
  --ev-color: #ef4444;
}

.cal-event__dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--ev-color, var(--color-primary));
  flex-shrink: 0;
}

.cal-event__time {
  font-size: .65rem;
  font-weight: 700;
  color: var(--ev-color, var(--color-primary));
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.cal-event__title {
  font-size: .7rem;
  font-weight: 500;
  color: var(--color-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.cal-event-more {
  font-size: .68rem;
  color: var(--color-text-muted);
  padding: 1px 5px;
  cursor: pointer;
  font-weight: 500;
  transition: color .12s;
}
.cal-event-more:hover { color: var(--color-primary); }

/* ── Загрузка ─────────────────────────────────────────────────────── */
.cal-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

/* ── Легенда ──────────────────────────────────────────────────────── */
.cal-legend {
  display: flex;
  gap: 20px;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border);
  flex-wrap: wrap;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: .75rem;
  color: var(--color-text-muted);
}

.legend-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* ── Popover: задачи дня ──────────────────────────────────────────── */
.day-popover {
  min-width: 280px;
  max-width: 340px;
}

.day-popover__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--color-border);
  gap: 8px;
}

.day-popover__date {
  font-size: .875rem;
  font-weight: 600;
  color: var(--color-text);
  text-transform: capitalize;
}

.day-popover__add {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--color-primary);
  border-radius: 6px;
  background: none;
  color: var(--color-primary);
  font-size: .75rem;
  font-weight: 600;
  cursor: pointer;
  transition: all .15s;
  white-space: nowrap;
}
.day-popover__add:hover {
  background: var(--color-primary);
  color: #fff;
}

.day-popover__list {
  display: flex;
  flex-direction: column;
  gap: 1px;
  max-height: 320px;
  overflow-y: auto;
  padding: 8px;
}

.day-popover__item {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 8px 10px;
  border-radius: 6px;
  border-left: 3px solid var(--color-border);
  background: var(--color-bg-hover);
  cursor: pointer;
  transition: background .12s;
}

.day-popover__item:hover { background: color-mix(in srgb, var(--color-primary) 8%, var(--color-bg-hover)); }
.day-popover__item--done { opacity: .5; }
.day-popover__item--overdue { border-left-color: #ef4444 !important; }

.day-popover__item-top {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.day-popover__item-time {
  font-size: .7rem;
  font-weight: 700;
  color: var(--color-text-muted);
  flex-shrink: 0;
  font-variant-numeric: tabular-nums;
}

.day-popover__item-title {
  font-size: .8125rem;
  font-weight: 500;
  color: var(--color-text);
  line-height: 1.35;
}

.day-popover__item-meta {
  display: flex;
  gap: 10px;
  font-size: .7rem;
  color: var(--color-text-muted);
}

.day-popover__item-meta .pi {
  font-size: .65rem;
}

/* ── Адаптив ──────────────────────────────────────────────────────── */
@media (max-width: 768px) {
  .cal-cell {
    min-height: 64px;
  }

  .cal-event__time { display: none; }

  .cal-nav__title { min-width: 130px; font-size: .9rem; }

  .cal-header { padding: 12px 14px; }
  .cal-weekdays { padding: 0 6px; }
  .cal-grid { padding: 6px; }
}
</style>
