<template>
  <Drawer
    v-model:visible="localVisible"
    position="right"
    :style="{ width: '480px' }"
    :pt="{ header: { class: 'drawer-header' } }"
  >
    <template #header>
      <div class="drawer-title">
        <span>Задача</span>
        <div class="drawer-title__actions">
          <Button v-if="can('TASK_EDIT')" icon="pi pi-pencil" text rounded size="small"
            v-tooltip="'Редактировать'" @click="showEditDialog = true" />
          <Button v-if="can('TASK_DELETE')" icon="pi pi-trash" text rounded size="small"
            severity="danger" v-tooltip="'Удалить'" @click="confirmDelete" />
        </div>
      </div>
    </template>

    <div v-if="task" class="task-detail animate-fade-in">

      <!-- Статус + тип -->
      <div class="task-detail__status-row">
        <Tag
          :value="task.statusName || task.statusCode"
          :style="`background:${task.statusColor}22;color:${task.statusColor};border:1px solid ${task.statusColor}44`"
        />
        <Tag v-if="task.taskTypeName" :value="task.taskTypeName" severity="secondary" />
        <Tag v-if="task.overdue" value="Просрочено" severity="danger" />
      </div>

      <!-- Заголовок -->
      <h2 class="task-detail__title">{{ task.title }}</h2>

      <!-- Описание -->
      <p v-if="task.description" class="task-detail__desc">{{ task.description }}</p>

      <!-- Метаданные -->
      <div class="task-detail__meta">
        <div class="meta-row" v-if="task.scheduledAt">
          <span class="meta-label"><i class="pi pi-clock" />Запланировано</span>
          <span class="meta-value" :class="{ 'overdue-text': task.overdue }">
            {{ formatDateTime(task.scheduledAt) }}
          </span>
        </div>
        <div class="meta-row" v-if="task.completedAt">
          <span class="meta-label"><i class="pi pi-check-circle" />Выполнено</span>
          <span class="meta-value">{{ formatDateTime(task.completedAt) }}</span>
        </div>
        <div class="meta-row" v-if="task.assigneeName">
          <span class="meta-label"><i class="pi pi-user" />Исполнитель</span>
          <span class="meta-value">{{ task.assigneeName }}</span>
        </div>
        <div class="meta-row" v-if="task.authorName">
          <span class="meta-label"><i class="pi pi-user-edit" />Автор</span>
          <span class="meta-value">{{ task.authorName }}</span>
        </div>
        <div class="meta-row" v-if="task.customerName">
          <span class="meta-label"><i class="pi pi-building" />Клиент</span>
          <span class="meta-value">{{ task.customerName }}</span>
        </div>
        <div class="meta-row">
          <span class="meta-label"><i class="pi pi-calendar" />Создано</span>
          <span class="meta-value font-mono">{{ formatDateTime(task.createdAt) }}</span>
        </div>
      </div>

      <!-- Быстрый выбор статуса -->
      <div class="task-detail__status-change">
        <span class="section-label">Изменить статус</span>
        <div class="status-buttons">
          <Button v-for="s in quickStatuses" :key="s.id"
            :label="s.name" text size="small"
            :class="{ active: task.statusId === s.id }"
            :style="task.statusId === s.id ? `color:${s.color}` : ''"
            @click="changeStatus(s.id)"
          />
        </div>
      </div>

      <Divider />

      <!-- Комментарии -->
      <div class="task-detail__comments">
        <span class="section-label">
          Комментарии
          <span class="count-badge" v-if="task.comments?.length">
            {{ task.comments.length }}
          </span>
        </span>

        <div class="comments-list" v-if="task.comments?.length">
          <div v-for="c in task.comments" :key="c.id" class="comment">
            <div class="comment__avatar">{{ initials(c.authorName ?? '') }}</div>
            <div class="comment__body">
              <div class="comment__header">
                <span class="comment__author">{{ c.authorName }}</span>
                <span class="comment__time font-mono">{{ formatDateTime(c.createdAt) }}</span>
              </div>
              <p class="comment__text">{{ c.content }}</p>
            </div>
          </div>
        </div>

        <div v-else class="comments-empty">Комментариев пока нет</div>

        <!-- Новый комментарий -->
        <div class="comment-input">
          <Textarea
            v-model="newComment"
            placeholder="Написать комментарий..."
            rows="3"
            auto-resize
            fluid
          />
          <Button
            label="Отправить"
            icon="pi pi-send"
            size="small"
            :disabled="!newComment.trim()"
            :loading="sendingComment"
            @click="sendComment"
          />
        </div>
      </div>

    </div>

    <!-- Загрузка -->
    <div v-else-if="loading" class="drawer-loading">
      <ProgressSpinner />
    </div>

    <!-- Форма редактирования -->
    <TaskFormDialog
      v-if="task"
      v-model:visible="showEditDialog"
      :task="task"
      @saved="onEdited"
    />
  </Drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import Drawer from 'primevue/drawer'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Divider from 'primevue/divider'
import Textarea from 'primevue/textarea'
import ProgressSpinner from 'primevue/progressspinner'
import { useConfirm } from 'primevue/useconfirm'
import { tasksApi, type TaskResponse } from '@/api/tasks'
import { usePermission } from '@/composables/usePermission'
import { useAppToast } from '@/composables/useAppToast'
import TaskFormDialog from './TaskFormDialog.vue'
import dayjs from 'dayjs'

const props = defineProps<{ visible: boolean; taskId: string | null }>()
const emit = defineEmits<{
  'update:visible': [boolean]
  'updated': []
  'deleted': []
}>()

const visible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

import { computed } from 'vue'
const confirm = useConfirm()
const { can } = usePermission()
const toast = useAppToast()

const task          = ref<TaskResponse | null>(null)
const loading       = ref(false)
const showEditDialog = ref(false)
const newComment    = ref('')
const sendingComment = ref(false)

// Демо-статусы — в реальности из /dictionaries/TASK_STATUS
const quickStatuses = [
  { id: 'new-id',         name: 'Новая',    color: '#6b7280' },
  { id: 'progress-id',    name: 'В работе', color: '#f59e0b' },
  { id: 'done-id',        name: 'Выполнена', color: '#22c55e' },
  { id: 'cancelled-id',   name: 'Отменена', color: '#ef4444' },
]

watch(() => props.taskId, async (id) => {
  if (!id) { task.value = null; return }
  loading.value = true
  try {
    const { data: res } = await tasksApi.getById(id)
    task.value = res.data ?? null
  } catch { toast.error('Не удалось загрузить задачу') }
  finally { loading.value = false }
}, { immediate: true })

async function changeStatus(statusId: string) {
  if (!task.value) return
  try {
    await tasksApi.changeStatus(task.value.id, statusId)
    const { data: res } = await tasksApi.getById(task.value.id)
    task.value = res.data ?? null
    emit('updated')
  } catch { toast.error('Не удалось изменить статус') }
}

async function sendComment() {
  if (!task.value || !newComment.value.trim()) return
  sendingComment.value = true
  try {
    const comment = await tasksApi.addComment(task.value.id, newComment.value)
    task.value.comments = [...(task.value.comments ?? []), comment.data.data!]
    newComment.value = ''
  } catch { toast.error('Не удалось отправить комментарий') }
  finally { sendingComment.value = false }
}

function confirmDelete() {
  confirm.require({
    message: `Удалить задачу «${task.value?.title}»?`,
    header: 'Удаление задачи',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    acceptLabel: 'Удалить',
    rejectLabel: 'Отмена',
    accept: async () => {
      try {
        await tasksApi.delete(task.value!.id)
        emit('deleted')
        visible.value = false
      } catch { toast.error('Не удалось удалить задачу') }
    }
  })
}

function onEdited() {
  showEditDialog.value = false
  if (props.taskId) {
    tasksApi.getById(props.taskId).then(({ data: res }) => { task.value = res.data ?? null })
  }
  emit('updated')
}

function formatDateTime(iso: string) {
  return dayjs(iso).format('D MMM YYYY, HH:mm')
}

function initials(name: string) {
  return name?.trim().split(' ').slice(0,2).map(p=>p[0]?.toUpperCase()??'').join('') || '?'
}
</script>

<style scoped>
.drawer-title { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.drawer-title__actions { display: flex; gap: 4px; }

.task-detail { display: flex; flex-direction: column; gap: 16px; padding-bottom: 24px; }

.task-detail__status-row { display: flex; gap: 8px; flex-wrap: wrap; }
.task-detail__title { font-size: 1.125rem; font-weight: 700; color: var(--text-primary); line-height: 1.4; letter-spacing: -0.01em; }
.task-detail__desc { font-size: 0.9rem; color: var(--text-secondary); line-height: 1.6; white-space: pre-line; }

/* Meta */
.task-detail__meta { display: flex; flex-direction: column; gap: 10px; }
.meta-row { display: flex; align-items: center; gap: 12px; }
.meta-label { display: flex; align-items: center; gap: 6px; font-size: 0.8125rem; color: var(--text-muted); min-width: 130px; }
.meta-label .pi { font-size: 12px; }
.meta-value { font-size: 0.875rem; color: var(--text-primary); }
.overdue-text { color: var(--danger) !important; }

/* Status change */
.section-label { font-size: 0.75rem; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: var(--text-muted); }
.task-detail__status-change { display: flex; flex-direction: column; gap: 8px; }
.status-buttons { display: flex; gap: 6px; flex-wrap: wrap; }
.status-buttons .p-button { border: 1px solid var(--border-default) !important; border-radius: var(--radius-md) !important; }
.status-buttons .p-button.active { background: var(--bg-elevated) !important; }
.count-badge { display: inline-flex; align-items: center; justify-content: center; width: 20px; height: 20px; border-radius: 10px; background: var(--bg-elevated); font-size: 0.75rem; color: var(--text-muted); margin-left: 6px; }

/* Comments */
.task-detail__comments { display: flex; flex-direction: column; gap: 12px; }
.comments-list { display: flex; flex-direction: column; gap: 14px; }
.comment { display: flex; gap: 10px; }
.comment__avatar { width: 30px; height: 30px; border-radius: 50%; background: var(--accent-500); display: flex; align-items: center; justify-content: center; color: white; font-size: 0.75rem; font-weight: 600; flex-shrink: 0; }
.comment__body { flex: 1; }
.comment__header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.comment__author { font-size: 0.875rem; font-weight: 500; color: var(--text-primary); }
.comment__time { font-size: 0.75rem; color: var(--text-muted); }
.comment__text { font-size: 0.875rem; color: var(--text-secondary); line-height: 1.5; }
.comments-empty { font-size: 0.875rem; color: var(--text-muted); }

.comment-input { display: flex; flex-direction: column; gap: 8px; }

.drawer-loading { display: flex; justify-content: center; padding: 60px; }
</style>
