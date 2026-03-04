<template>
  <div
    class="task-card"
    :class="{
      'task-card--overdue': task.overdue,
      'task-card--done':    task.statusCode === 'DONE',
      'task-card--today':   task.dueToday && !task.overdue,
    }"
    @click="$emit('click')"
  >
    <!-- Цветная полоса типа задачи -->
    <div class="task-card__type-bar" :style="{ background: task.taskTypeColor || '#3b82f6' }" />

    <!-- Чекбокс статуса -->
    <div class="task-card__check" @click.stop="cycleStatus">
      <div class="check-circle" :style="{ borderColor: task.statusColor || '#888' }">
        <i v-if="task.statusCode === 'DONE'" class="pi pi-check"
           :style="{ color: task.statusColor }" />
      </div>
    </div>

    <!-- Основной контент -->
    <div class="task-card__body">
      <div class="task-card__top">
        <span class="task-card__title" :class="{ done: task.statusCode === 'DONE' }">
          {{ task.title }}
        </span>
        <div class="task-card__badges">
          <Tag v-if="task.taskTypeName" :value="task.taskTypeName" severity="secondary" />
          <Tag
            :value="task.statusName || task.statusCode"
            :style="task.statusColor ? `background:${task.statusColor}22;color:${task.statusColor};border:1px solid ${task.statusColor}44` : ''"
          />
        </div>
      </div>

      <div class="task-card__meta">
        <!-- Дата -->
        <span v-if="task.scheduledAt" class="meta-item"
              :class="{ 'meta-item--overdue': task.overdue, 'meta-item--today': task.dueToday && !task.overdue }">
          <i class="pi pi-clock" />
          {{ formatDateTime(task.scheduledAt) }}
        </span>

        <!-- Исполнитель -->
        <span v-if="task.assigneeName" class="meta-item">
          <i class="pi pi-user" />
          {{ task.assigneeName }}
        </span>

        <!-- Клиент -->
        <span v-if="task.customerName" class="meta-item">
          <i class="pi pi-building" />
          {{ task.customerName }}
        </span>
      </div>
    </div>

    <!-- Действия при ховере -->
    <div class="task-card__actions" @click.stop>
      <Button icon="pi pi-pencil" text rounded size="small"
        v-tooltip="'Редактировать'" @click="$emit('click')" />
      <Button v-if="can('TASK_DELETE')" icon="pi pi-trash" text rounded size="small"
        severity="danger" v-tooltip="'Удалить'" @click="$emit('delete')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import Tag from 'primevue/tag'
import Button from 'primevue/button'
import type { TaskResponse } from '@/api/tasks'
import { usePermission } from '@/composables/usePermission'
import dayjs from 'dayjs'

const props = defineProps<{ task: TaskResponse }>()
const emit = defineEmits<{
  'click': []
  'status-change': [statusId: string]
  'delete': []
}>()

const { can } = usePermission()

function cycleStatus() {
  // Упрощённый цикл: NEW → IN_PROGRESS → DONE → NEW
  // В реальности statusId берётся из словаря
  emit('status-change', 'NEXT_STATUS')
}

function formatDateTime(iso: string) {
  const d = dayjs(iso)
  const today    = dayjs().startOf('day')
  const tomorrow = today.add(1, 'day')
  if (d.isSame(today, 'day'))    return `Сегодня, ${d.format('HH:mm')}`
  if (d.isSame(tomorrow, 'day')) return `Завтра, ${d.format('HH:mm')}`
  return d.format('D MMM, HH:mm')
}
</script>

<style scoped>
.task-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  position: relative;
  overflow: hidden;
}

.task-card:hover {
  border-color: var(--border-default);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.task-card:hover .task-card__actions { opacity: 1; }

.task-card--overdue {
  border-left-color: var(--danger) !important;
  background: rgba(239,68,68,0.03);
}

.task-card--today {
  border-left-color: var(--accent-500) !important;
  background: rgba(59,130,246,0.03);
}

.task-card--done { opacity: 0.6; }

/* Тип-полоса */
.task-card__type-bar {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  width: 3px;
  border-radius: 3px 0 0 3px;
}

/* Чекбокс */
.task-card__check { flex-shrink: 0; }
.check-circle {
  width: 22px; height: 22px; border-radius: 50%;
  border: 2px solid currentColor; display: flex; align-items: center; justify-content: center;
  transition: all var(--transition-fast);
}
.check-circle .pi { font-size: 11px; }
.task-card:hover .check-circle { transform: scale(1.1); }

/* Тело */
.task-card__body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.task-card__top { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.task-card__title {
  font-size: 0.9375rem; font-weight: 500; color: var(--text-primary);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  transition: color var(--transition-fast);
}
.task-card__title.done {
  text-decoration: line-through; color: var(--text-muted);
}
.task-card__badges { display: flex; gap: 6px; flex-shrink: 0; }

/* Meta */
.task-card__meta { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.meta-item { display: flex; align-items: center; gap: 5px; font-size: 0.8125rem; color: var(--text-muted); }
.meta-item .pi { font-size: 11px; }
.meta-item--overdue { color: var(--danger) !important; }
.meta-item--today   { color: var(--accent-400) !important; }

/* Actions */
.task-card__actions {
  display: flex; gap: 2px; opacity: 0;
  transition: opacity var(--transition-fast);
  flex-shrink: 0;
}
</style>
