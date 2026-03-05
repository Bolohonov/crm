<template>
  <Dialog v-model:visible="localVisible" header="Пригласить пользователя"
    modal :style="{ width: '520px' }" :draggable="false">

    <form @submit.prevent="handleSubmit" class="invite-form">
      <p class="invite-hint">
        Пользователь получит письмо со ссылкой для подтверждения и входа в систему.
      </p>

      <div v-if="error" class="form-error">
        <i class="pi pi-exclamation-circle" />{{ error }}
      </div>

      <!-- Имя -->
      <div class="field-row">
        <div class="field">
          <label>Фамилия *</label>
          <InputText v-model="form.lastName" placeholder="Иванов"
            :class="{ 'p-invalid': v$.lastName.$error }" fluid />
          <small v-if="v$.lastName.$error" class="field-error">Обязательно</small>
        </div>
        <div class="field">
          <label>Имя *</label>
          <InputText v-model="form.firstName" placeholder="Иван"
            :class="{ 'p-invalid': v$.firstName.$error }" fluid />
          <small v-if="v$.firstName.$error" class="field-error">Обязательно</small>
        </div>
      </div>

      <div class="field">
        <label>Отчество</label>
        <InputText v-model="form.middleName" placeholder="Иванович" fluid />
      </div>

      <!-- Email -->
      <div class="field">
        <label>Email *</label>
        <InputText v-model="form.email" placeholder="user@company.ru" type="email"
          :class="{ 'p-invalid': v$.email.$error }" fluid />
        <small v-if="v$.email.$error" class="field-error">{{ v$.email.$errors[0].$message }}</small>
      </div>

      <!-- Телефон -->
      <div class="field">
        <label>Телефон</label>
        <InputText v-model="form.phone" placeholder="+79001234567" fluid />
      </div>

      <!-- Роли -->
      <div class="field">
        <label>Роли *</label>
        <MultiSelect
          v-model="form.roleIds"
          :options="roles"
          option-label="name"
          option-value="id"
          placeholder="Выберите роли..."
          display="chip"
          :class="{ 'p-invalid': v$.roleIds.$error }"
          fluid
        >
          <template #option="{ option }">
            <div class="role-option">
              <span class="role-dot" :style="option.color ? `background:${option.color}` : ''" />
              <span>{{ option.name }}</span>
              <span class="text-muted" style="font-size:0.75rem;margin-left:auto">
                {{ option.permissionCount }} прав
              </span>
            </div>
          </template>
        </MultiSelect>
        <small v-if="v$.roleIds.$error" class="field-error">Выберите хотя бы одну роль</small>
      </div>

      <!-- Предпросмотр прав -->
      <Transition name="fade">
        <div v-if="selectedRolesPermissions.length" class="permissions-preview">
          <span class="preview-label">Доступные права:</span>
          <div class="permissions-list">
            <span v-for="g in permissionGroups" :key="g.group" class="perm-group">
              <strong>{{ g.group }}:</strong> {{ g.names.join(', ') }}
            </span>
          </div>
        </div>
      </Transition>

      <div class="dialog-footer">
        <Button label="Отмена" text type="button" @click="close" />
        <Button type="submit" icon="pi pi-send" label="Отправить приглашение" :loading="saving" />
      </div>
    </form>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch , onMounted } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import MultiSelect from 'primevue/multiselect'
import { useVuelidate } from '@vuelidate/core'
import { required, email, helpers, minLength } from '@vuelidate/validators'
import { adminApi, type Role } from '@/api/admin'
import { useAppToast } from '@/composables/useAppToast'

const props = defineProps<{ visible: boolean; roles?: Role[] }>()
const emit  = defineEmits<{ 'update:visible': [boolean]; 'invited': [] }>()

const toast  = useAppToast()
const saving = ref(false)
const error  = ref('')
const internalRoles = ref<Role[]>([])
const availableRoles = computed(() => props.roles ?? internalRoles.value)

onMounted(async () => {
  if (!props.roles || props.roles.length === 0) {
    try {
      const { data: res } = await adminApi.listRoles()
      internalRoles.value = res.data ?? []
    } catch { /* ignore */ }
  }
})

const form = reactive({
  email: '', firstName: '', lastName: '', middleName: '', phone: '', roleIds: [] as string[],
})

const rules = {
  email:     { required: helpers.withMessage('Обязательно', required), email: helpers.withMessage('Неверный формат', email) },
  firstName: { required: helpers.withMessage('Обязательно', required) },
  lastName:  { required: helpers.withMessage('Обязательно', required) },
  roleIds:   { required: helpers.withMessage('Обязательно', required), minLength: helpers.withMessage('Выберите роль', minLength(1)) },
}
const v$ = useVuelidate(rules, form)

// Агрегируем права из выбранных ролей
const selectedRolesPermissions = computed(() => {
  const ids = new Set(form.roleIds)
  return availableRoles.value
    .filter(r => ids.has(r.id))
    .flatMap(r => r.permissions ?? [])
    .reduce((acc, p) => {
      if (!acc.find(x => x.id === p.id)) acc.push(p)
      return acc
    }, [] as any[])
})

const permissionGroups = computed(() => {
  const groups: Record<string, string[]> = {}
  for (const p of selectedRolesPermissions.value) {
    if (!groups[p.group]) groups[p.group] = []
    groups[p.group].push(p.name)
  }
  return Object.entries(groups).map(([group, names]) => ({ group, names }))
})

async function handleSubmit() {
  error.value = ''
  if (!(await v$.value.$validate())) return
  saving.value = true
  try {
    await adminApi.inviteUser({
      email: form.email, firstName: form.firstName, lastName: form.lastName,
      middleName: form.middleName || undefined, phone: form.phone || undefined,
      roleIds: form.roleIds,
    })
    emit('invited')
  } catch (e: any) {
    error.value = e?.response?.data?.error?.message ?? 'Ошибка отправки приглашения'
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:visible', false)
  Object.assign(form, { email:'', firstName:'', lastName:'', middleName:'', phone:'', roleIds:[] })
  v$.value.$reset(); error.value = ''
}
</script>

<style scoped>
.invite-form { display: flex; flex-direction: column; gap: 16px; }
.invite-hint { font-size: 0.875rem; color: var(--text-muted); line-height: 1.5; padding: 10px 14px; background: var(--bg-elevated); border-radius: var(--radius-md); border-left: 3px solid var(--accent-500); }
.field { display: flex; flex-direction: column; gap: 5px; }
.field label { font-size: 0.8125rem; font-weight: 500; color: var(--text-secondary); }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field-error { color: #fca5a5; font-size: 0.8rem; }
.form-error { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: var(--radius-md); color: #fca5a5; font-size: 0.875rem; }

.role-option { display: flex; align-items: center; gap: 8px; }
.role-dot { width: 10px; height: 10px; border-radius: 50%; background: var(--accent-500); flex-shrink: 0; }

.permissions-preview {
  padding: 12px; background: var(--bg-elevated); border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle); display: flex; flex-direction: column; gap: 8px;
}
.preview-label { font-size: 0.75rem; font-weight: 600; letter-spacing: 0.06em; text-transform: uppercase; color: var(--text-muted); }
.permissions-list { display: flex; flex-direction: column; gap: 4px; }
.perm-group { font-size: 0.8125rem; color: var(--text-secondary); line-height: 1.5; }
.perm-group strong { color: var(--text-primary); }

.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 16px; border-top: 1px solid var(--border-subtle); }

.fade-enter-active, .fade-leave-active { transition: opacity 200ms, transform 200ms; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-6px); }
</style>
