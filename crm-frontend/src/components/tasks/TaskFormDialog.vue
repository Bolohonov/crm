<template>
  <Dialog
      v-model:visible="localVisible"
      :header="isEdit ? 'Редактировать задачу' : 'Новая задача'"
      modal
      :style="{ width: '560px' }"
      :draggable="false"
  >
    <form @submit.prevent="handleSubmit" class="task-form">

      <div v-if="error" class="form-error">
        <i class="pi pi-exclamation-circle" />{{ error }}
      </div>

      <!-- Название -->
      <div class="field">
        <label>Название *</label>
        <InputText v-model="form.title" placeholder="Что нужно сделать?"
                   :class="{ 'p-invalid': v$.title.$error }" fluid />
        <small v-if="v$.title.$error" class="field-error">{{ v$.title.$errors[0].$message }}</small>
      </div>

      <!-- Описание -->
      <div class="field">
        <label>Описание</label>
        <Textarea v-model="form.description" placeholder="Подробности задачи..."
                  rows="3" auto-resize fluid />
      </div>

      <!-- Тип + статус -->
      <div class="field-row">
        <div class="field">
          <label>Тип *</label>
          <Select v-model="form.taskTypeId" :options="taskTypes"
                  option-label="name" option-value="id" placeholder="Тип задачи"
                  :loading="loadingDicts"
                  :class="{ 'p-invalid': v$.taskTypeId.$error }" fluid />
          <small v-if="v$.taskTypeId.$error" class="field-error">Обязательно</small>
        </div>
        <div class="field">
          <label>Статус *</label>
          <Select v-model="form.statusId" :options="taskStatuses"
                  option-label="name" option-value="id" placeholder="Статус"
                  :loading="loadingDicts"
                  :class="{ 'p-invalid': v$.statusId.$error }" fluid />
          <small v-if="v$.statusId.$error" class="field-error">Обязательно</small>
        </div>
      </div>

      <!-- Дата и время -->
      <div class="field">
        <label>Запланировано</label>
        <DatePicker
            v-model="scheduledDate"
            show-time hour-format="24"
            date-format="dd.mm.yy"
            placeholder="Выберите дату и время"
            fluid
        />
      </div>

      <!-- Исполнитель + клиент -->
      <div class="field-row">
        <div class="field">
          <label>Исполнитель</label>
          <Select v-model="form.assigneeId" :options="users"
                  option-label="fullName" option-value="id"
                  placeholder="Не назначен" show-clear
                  :loading="loadingDicts" fluid />
        </div>
        <div class="field">
          <label>Клиент</label>
          <Select v-model="form.customerId" :options="customers"
                  option-label="displayName" option-value="id"
                  placeholder="Без клиента" show-clear
                  :loading="loadingDicts" fluid />
        </div>
      </div>

      <div class="dialog-footer">
        <Button label="Отмена" text @click="close" />
        <Button type="submit"
                :label="isEdit ? 'Сохранить' : 'Создать задачу'"
                :loading="saving" />
      </div>
    </form>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch, onMounted } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import { useVuelidate } from '@vuelidate/core'
import { required, helpers } from '@vuelidate/validators'
import { tasksApi, type TaskResponse, type CreateTaskRequest } from '@/api/tasks'
import { useAppToast } from '@/composables/useAppToast'
import apiClient from '@/api/client'

const props = defineProps<{
  visible: boolean
  task?: TaskResponse
  initialDate?: string
  initialCustomerId?: string   // ← добавлен
  statuses?: { id: string; name: string }[]  // ← добавлен (опциональный override)
}>()

const emit = defineEmits<{
  'update:visible': [boolean]
  'saved': []
}>()

const toast  = useAppToast()
const saving = ref(false)
const error  = ref('')

// ── localVisible — без него Dialog не открывается ────────────────
const localVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

const isEdit = computed(() => !!props.task)

const form = reactive({
  title:       '',
  description: '',
  taskTypeId:  '',
  statusId:    '',
  assigneeId:  null as string | null,
  customerId:  null as string | null,
})
const scheduledDate = ref<Date | null>(null)

// ── Справочники — грузятся с бэка ────────────────────────────────
const taskTypes  = ref<{ id: string; name: string }[]>([])
const users      = ref<{ id: string; fullName: string }[]>([])
const customers  = ref<{ id: string; displayName: string }[]>([])
const loadingDicts = ref(false)

// Статусы: приоритет — проп statuses (переданный из родителя),
// если не передан — грузим сами
const taskStatuses = computed(() =>
    props.statuses?.length ? props.statuses : _taskStatuses.value
)
const _taskStatuses = ref<{ id: string; name: string }[]>([])

onMounted(async () => {
  loadingDicts.value = true
  try {
    const requests: Promise<any>[] = [
      apiClient.get('/users?size=200'),
      apiClient.get('/customers?size=200'),
      tasksApi.getTypes?.() ?? apiClient.get('/task-types'),
    ]
    if (!props.statuses?.length) {
      requests.push(tasksApi.getStatuses?.() ?? apiClient.get('/task-statuses'))
    }

    const [usersRes, customersRes, typesRes, statusesRes] = await Promise.all(requests)

    users.value     = usersRes.data?.data?.content
        ?? usersRes.data?.data
        ?? []
    customers.value = customersRes.data?.data?.content
        ?? customersRes.data?.data
        ?? []
    taskTypes.value = typesRes.data?.data?.content
        ?? typesRes.data?.data
        ?? []

    if (statusesRes) {
      _taskStatuses.value = statusesRes.data?.data?.content
          ?? statusesRes.data?.data
          ?? []
    }
  } catch (e) {
    console.error('TaskFormDialog: ошибка загрузки справочников', e)
  } finally {
    loadingDicts.value = false
  }
})

// ── Заполняем форму при редактировании ──────────────────────────
watch(() => props.task, (t) => {
  if (!t) return
  form.title       = t.title
  form.description = t.description ?? ''
  form.taskTypeId  = t.taskTypeId
  form.statusId    = t.statusId
  form.assigneeId  = t.assigneeId ?? null
  form.customerId  = t.customerId ?? null
  scheduledDate.value = t.scheduledAt ? new Date(t.scheduledAt) : null
}, { immediate: true })

// ── Предзаполняем дату из календаря ─────────────────────────────
watch(() => props.initialDate, (d) => {
  if (d) scheduledDate.value = new Date(d)
})

// ── Подставляем клиента при открытии нового ──────────────────────
watch(() => props.visible, (v) => {
  if (v && !props.task) {
    form.customerId = props.initialCustomerId ?? null
    // Сбрасываем остальные поля при открытии
    form.title       = ''
    form.description = ''
    form.taskTypeId  = ''
    form.statusId    = ''
    form.assigneeId  = null
    scheduledDate.value = null
    v$.value.$reset()
    error.value = ''
  }
})

// ── Валидация ────────────────────────────────────────────────────
const rules = {
  title:      { required: helpers.withMessage('Название обязательно', required) },
  taskTypeId: { required: helpers.withMessage('Обязательно', required) },
  statusId:   { required: helpers.withMessage('Обязательно', required) },
}
const v$ = useVuelidate(rules, form)

// ── Сабмит ───────────────────────────────────────────────────────
async function handleSubmit() {
  error.value = ''
  if (!(await v$.value.$validate())) return

  saving.value = true
  try {
    const payload: CreateTaskRequest = {
      title:       form.title,
      description: form.description || undefined,
      taskTypeId:  form.taskTypeId,
      statusId:    form.statusId,
      assigneeId:  form.assigneeId  || undefined,
      customerId:  form.customerId  || undefined,
      scheduledAt: scheduledDate.value
          ? scheduledDate.value.toISOString() : undefined,
    }

    if (isEdit.value && props.task) {
      await tasksApi.update(props.task.id, payload)
      toast.success('Задача обновлена')
    } else {
      await tasksApi.create(payload)
      toast.success('Задача создана')
    }

    emit('saved')
    emit('update:visible', false)
  } catch (e: any) {
    error.value = e?.response?.data?.error?.message ?? 'Ошибка сохранения'
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:visible', false)
  v$.value.$reset()
  error.value = ''
}
</script>

<style scoped>
.task-form { display: flex; flex-direction: column; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 5px; }
.field label { font-size: 0.8125rem; font-weight: 500; color: var(--text-secondary); }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field-error { color: #fca5a5; font-size: 0.8rem; }
.form-error { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: var(--radius-md); color: #fca5a5; font-size: 0.875rem; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 16px; border-top: 1px solid var(--border-subtle); }
</style>