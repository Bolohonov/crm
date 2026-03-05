<template>
  <Dialog v-model:visible="localVisible"
    :header="isEdit ? 'Редактировать роль' : 'Новая роль'"
    modal :style="{ width: '480px' }" :draggable="false">

    <form @submit.prevent="handleSubmit" class="role-form">

      <div v-if="error" class="form-error">
        <i class="pi pi-exclamation-circle" />{{ error }}
      </div>

      <div class="field">
        <label>Название *</label>
        <InputText v-model="form.name" placeholder="Менеджер по продажам"
          :class="{ 'p-invalid': v$.name.$error }" fluid />
        <small v-if="v$.name.$error" class="field-error">{{ v$.name.$errors[0].$message }}</small>
      </div>

      <div class="field">
        <label>Код *</label>
        <InputText
          v-model="form.code"
          placeholder="SALES_MANAGER"
          :class="{ 'p-invalid': v$.code.$error }"
          @input="form.code = form.code.toUpperCase().replace(/[^A-Z0-9_]/g, '')"
          fluid
        />
        <small v-if="v$.code.$error" class="field-error">{{ v$.code.$errors[0].$message }}</small>
        <small class="field-hint">Только A–Z, 0–9, _ . Используется в коде системы.</small>
      </div>

      <div class="field">
        <label>Описание</label>
        <Textarea v-model="form.description" placeholder="Для чего предназначена роль..."
          rows="2" auto-resize fluid />
      </div>

      <!-- Цвет -->
      <div class="field">
        <label>Цвет</label>
        <div class="color-picker">
          <div
            v-for="c in colorPalette"
            :key="c"
            class="color-swatch"
            :class="{ active: form.color === c }"
            :style="{ background: c }"
            @click="form.color = c"
          />
          <div class="color-swatch color-swatch--custom"
            :style="form.color && !colorPalette.includes(form.color) ? { background: form.color, border: '2px solid white' } : {}"
          >
            <input type="color" v-model="form.color" title="Произвольный цвет" />
            <i class="pi pi-palette" />
          </div>
        </div>
        <div v-if="form.color" class="color-preview">
          <span class="role-preview-chip" :style="`background:${form.color}22;color:${form.color};border:1px solid ${form.color}44`">
            {{ form.name || 'Название роли' }}
          </span>
        </div>
      </div>

      <div class="dialog-footer">
        <Button label="Отмена" text type="button" @click="close" />
        <Button type="submit"
          :label="isEdit ? 'Сохранить' : 'Создать роль'"
          :loading="saving" />
      </div>
    </form>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import { useVuelidate } from '@vuelidate/core'
import { required, helpers, minLength } from '@vuelidate/validators'
import { adminApi, type Role } from '@/api/admin'
import { useAppToast } from '@/composables/useAppToast'

const props = defineProps<{ visible: boolean; role?: Role | null }>()
const emit  = defineEmits<{ 'update:visible': [boolean]; 'saved': [] }>()

const toast  = useAppToast()
const saving = ref(false)
const error  = ref('')
const isEdit = computed(() => !!props.role)

const form = reactive({ name: '', code: '', description: '', color: '#3b82f6' })

const colorPalette = [
  '#3b82f6', '#8b5cf6', '#ec4899', '#ef4444',
  '#f97316', '#f59e0b', '#22c55e', '#06b6d4',
  '#6b7280',
]

const rules = {
  name: { required: helpers.withMessage('Название обязательно', required) },
  code: {
    required:  helpers.withMessage('Код обязателен', required),
    minLength: helpers.withMessage('Минимум 2 символа', minLength(2)),
  },
}
const v$ = useVuelidate(rules, form)

watch(() => props.role, (r) => {
  if (!r) { Object.assign(form, { name:'', code:'', description:'', color:'#3b82f6' }); return }
  form.name        = r.name
  form.code        = r.code
  form.description = r.description ?? ''
  form.color       = r.color ?? '#3b82f6'
}, { immediate: true })

async function handleSubmit() {
  error.value = ''
  if (!(await v$.value.$validate())) return
  saving.value = true
  try {
    const payload = {
      name: form.name, code: form.code,
      description: form.description || undefined,
      color: form.color,
      permissionIds: props.role?.permissions.map(p => p.id) ?? [],
    }
    if (isEdit.value && props.role) {
      await adminApi.updateRole(props.role.id, payload)
      toast.success('Роль обновлена')
    } else {
      await adminApi.createRole(payload)
      toast.success('Роль создана')
    }
    emit('saved')
  } catch (e: any) {
    error.value = e?.response?.data?.error?.message ?? 'Ошибка сохранения'
  } finally {
    saving.value = false
  }
}

function close() { emit('update:visible', false); v$.value.$reset(); error.value = '' }
</script>

<style scoped>
.role-form { display: flex; flex-direction: column; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 5px; }
.field label { font-size: 0.8125rem; font-weight: 500; color: var(--text-secondary); }
.field-error { color: #fca5a5; font-size: 0.8rem; }
.field-hint  { font-size: 0.75rem; color: var(--text-muted); }
.form-error { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: var(--radius-md); color: #fca5a5; font-size: 0.875rem; }

/* Color picker */
.color-picker { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.color-swatch {
  width: 28px; height: 28px; border-radius: 8px; cursor: pointer;
  border: 2px solid transparent; transition: all var(--transition-fast);
  flex-shrink: 0;
}
.color-swatch:hover { transform: scale(1.15); }
.color-swatch.active { border-color: white; box-shadow: 0 0 0 2px currentColor; }
.color-swatch--custom {
  background: var(--bg-elevated); border: 1px dashed var(--border-default);
  display: flex; align-items: center; justify-content: center; position: relative; overflow: hidden;
}
.color-swatch--custom input {
  position: absolute; inset: 0; opacity: 0; cursor: pointer; width: 100%; height: 100%;
}
.color-swatch--custom .pi { font-size: 12px; color: var(--text-muted); pointer-events: none; }

.color-preview { margin-top: 4px; }
.role-preview-chip { display: inline-flex; padding: 3px 10px; border-radius: 12px; font-size: 0.8125rem; font-weight: 500; }

.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 16px; border-top: 1px solid var(--border-subtle); }
</style>
