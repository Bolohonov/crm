<template>
  <Drawer v-model:visible="visible" position="right" :style="{ width: '480px' }">
    <template #header>
      <div class="drawer-title">
        <span>Пользователь</span>
        <div class="drawer-title__actions">
          <Button icon="pi pi-trash" text rounded size="small" severity="danger"
            v-tooltip="'Удалить'" @click="confirmDelete" />
        </div>
      </div>
    </template>

    <div v-if="user" class="user-detail animate-fade-in">

      <!-- Профиль -->
      <div class="user-hero">
        <div class="hero-avatar" :style="{ background: avatarColor(fullName(user)) }">
          {{ initials(fullName(user)) }}
        </div>
        <div class="hero-info">
          <h2>{{ fullName(user) }}</h2>
          <span class="hero-email">{{ user.email }}</span>
          <div class="hero-badges">
            <Tag :value="user.isActive ? 'Активен' : 'Отключён'"
              :severity="user.isActive ? 'success' : 'secondary'" />
            <Tag v-if="!user.isEmailVerified" value="Email не подтверждён" severity="warn" />
          </div>
        </div>
        <Button
          :icon="user.isActive ? 'pi pi-ban' : 'pi pi-check-circle'"
          :label="user.isActive ? 'Деактивировать' : 'Активировать'"
          :severity="user.isActive ? 'secondary' : 'success'"
          text size="small"
          @click="toggleActive"
        />
      </div>

      <Divider />

      <!-- Форма редактирования -->
      <form @submit.prevent="saveProfile" class="user-form">
        <span class="section-label">Данные профиля</span>

        <div class="field-row">
          <div class="field">
            <label>Фамилия</label>
            <InputText v-model="form.lastName" fluid />
          </div>
          <div class="field">
            <label>Имя</label>
            <InputText v-model="form.firstName" fluid />
          </div>
        </div>
        <div class="field">
          <label>Отчество</label>
          <InputText v-model="form.middleName" fluid />
        </div>
        <div class="field">
          <label>Телефон</label>
          <InputText v-model="form.phone" placeholder="+79001234567" fluid />
        </div>

        <Button type="submit" label="Сохранить профиль" outlined size="small"
          :loading="savingProfile" style="align-self:flex-end" />
      </form>

      <Divider />

      <!-- Управление ролями -->
      <div class="roles-section">
        <span class="section-label">Роли пользователя</span>

        <!-- Текущие роли -->
        <div class="current-roles">
          <div v-for="role in user.roles" :key="role.id" class="role-item">
            <span class="role-color" :style="role.color ? `background:${role.color}` : ''" />
            <span class="role-name">{{ role.name }}</span>
            <span class="role-code font-mono text-muted">{{ role.code }}</span>
            <Button icon="pi pi-times" text rounded size="small"
              @click="removeRole(role.id)"
              :disabled="user.roles.length <= 1"
              v-tooltip="user.roles.length <= 1 ? 'Нельзя удалить последнюю роль' : 'Убрать роль'"
            />
          </div>
          <div v-if="user.roles.length === 0" class="roles-empty text-muted">
            <i class="pi pi-exclamation-triangle" style="color:#f59e0b" />
            Нет ролей — пользователь не имеет доступа
          </div>
        </div>

        <!-- Добавить роль -->
        <div class="add-role">
          <Select v-model="newRoleId" :options="availableRoles"
            option-label="name" option-value="id"
            placeholder="Добавить роль..." fluid
            :disabled="availableRoles.length === 0" />
          <Button icon="pi pi-plus" label="Добавить"
            :disabled="!newRoleId" :loading="addingRole"
            @click="addRole" size="small" />
        </div>
      </div>

      <Divider />

      <!-- Техническая информация -->
      <div class="meta-section">
        <span class="section-label">Информация</span>
        <InfoField label="ID"             :value="user.id" monospace />
        <InfoField label="Дата создания"  :value="formatDateTime(user.createdAt)" />
        <InfoField label="Последний вход" :value="user.lastLoginAt ? formatDateTime(user.lastLoginAt) : 'Не входил'" />
      </div>

    </div>

    <ConfirmDialog />
  </Drawer>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import Drawer from 'primevue/drawer'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Divider from 'primevue/divider'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import ConfirmDialog from 'primevue/confirmdialog'
import { useConfirm } from 'primevue/useconfirm'
import { adminApi, type AdminUser, type Role } from '@/api/admin'
import { useAppToast } from '@/composables/useAppToast'
import InfoField from '@/components/common/InfoField.vue'
import dayjs from 'dayjs'

const props = defineProps<{ visible: boolean; user: AdminUser | null; roles: Role[] }>()
const emit  = defineEmits<{
  'update:visible': [boolean]
  'updated': []
}>()

const visible = computed({ get: () => props.visible, set: v => emit('update:visible', v) })
const confirm = useConfirm()
const toast   = useAppToast()

const savingProfile = ref(false)
const addingRole    = ref(false)
const newRoleId     = ref<string | null>(null)

const form = reactive({ firstName: '', lastName: '', middleName: '', phone: '' })

// Роли которых ещё нет у пользователя
const availableRoles = computed(() => {
  const hasIds = new Set(props.user?.roles.map(r => r.id) ?? [])
  return props.roles.filter(r => !hasIds.has(r.id))
})

watch(() => props.user, (u) => {
  if (!u) return
  form.firstName  = u.firstName
  form.lastName   = u.lastName
  form.middleName = u.middleName ?? ''
  form.phone      = u.phone ?? ''
}, { immediate: true })

async function saveProfile() {
  if (!props.user) return
  savingProfile.value = true
  try {
    await adminApi.updateUser(props.user.id, {
      firstName: form.firstName, lastName: form.lastName,
      middleName: form.middleName || undefined, phone: form.phone || undefined,
    })
    emit('updated')
    toast.success('Профиль сохранён')
  } catch { toast.error('Ошибка сохранения') }
  finally { savingProfile.value = false }
}

async function toggleActive() {
  if (!props.user) return
  try {
    await adminApi.setActive(props.user.id, !props.user.isActive)
    emit('updated')
    toast.success(props.user.isActive ? 'Пользователь деактивирован' : 'Пользователь активирован')
  } catch { toast.error('Ошибка') }
}

async function addRole() {
  if (!props.user || !newRoleId.value) return
  addingRole.value = true
  try {
    const ids = [...props.user.roles.map(r => r.id), newRoleId.value]
    await adminApi.assignRoles(props.user.id, ids)
    newRoleId.value = null
    emit('updated')
  } catch { toast.error('Не удалось добавить роль') }
  finally { addingRole.value = false }
}

async function removeRole(roleId: string) {
  if (!props.user) return
  try {
    const ids = props.user.roles.map(r => r.id).filter(id => id !== roleId)
    await adminApi.assignRoles(props.user.id, ids)
    emit('updated')
  } catch { toast.error('Не удалось убрать роль') }
}

function confirmDelete() {
  confirm.require({
    message: `Удалить пользователя «${fullName(props.user!)}»? Это необратимо.`,
    header: 'Удаление пользователя',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    acceptLabel: 'Удалить', rejectLabel: 'Отмена',
    accept: async () => {
      try {
        await adminApi.deleteUser(props.user!.id)
        visible.value = false
        emit('updated')
        toast.success('Пользователь удалён')
      } catch { toast.error('Ошибка удаления') }
    }
  })
}

function fullName(u: AdminUser) { return [u.lastName, u.firstName, u.middleName].filter(Boolean).join(' ') }
function initials(n: string) { return n.trim().split(' ').slice(0,2).map(p=>p[0]?.toUpperCase()??'').join('') || '?' }
const COLORS = ['#3b82f6','#8b5cf6','#ec4899','#f59e0b','#22c55e','#06b6d4']
function avatarColor(n: string) { let h=0; for(const c of n) h=(h*31+c.charCodeAt(0))|0; return COLORS[Math.abs(h)%COLORS.length] }
function formatDateTime(iso: string) { return dayjs(iso).format('D MMM YYYY, HH:mm') }
</script>

<style scoped>
.drawer-title { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.drawer-title__actions { display: flex; gap: 4px; }

.user-detail { display: flex; flex-direction: column; gap: 16px; padding-bottom: 24px; }

/* Hero */
.user-hero { display: grid; grid-template-columns: auto 1fr auto; align-items: start; gap: 14px; }
.hero-avatar { width: 56px; height: 56px; border-radius: 14px; display: flex; align-items: center; justify-content: center; color: white; font-size: 1.125rem; font-weight: 700; }
.hero-info { display: flex; flex-direction: column; gap: 4px; }
.hero-info h2 { font-size: 1.0625rem; font-weight: 700; color: var(--text-primary); letter-spacing: -0.01em; }
.hero-email { font-size: 0.8125rem; color: var(--text-muted); }
.hero-badges { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 4px; }

/* Form */
.section-label { font-size: 0.75rem; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: var(--text-muted); }
.user-form { display: flex; flex-direction: column; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 5px; }
.field label { font-size: 0.8125rem; font-weight: 500; color: var(--text-secondary); }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }

/* Roles */
.roles-section { display: flex; flex-direction: column; gap: 10px; }
.current-roles { display: flex; flex-direction: column; gap: 6px; }
.role-item { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: var(--bg-elevated); border-radius: var(--radius-md); border: 1px solid var(--border-subtle); }
.role-color { width: 10px; height: 10px; border-radius: 50%; background: var(--accent-500); flex-shrink: 0; }
.role-name { flex: 1; font-size: 0.875rem; font-weight: 500; color: var(--text-primary); }
.role-code { font-size: 0.75rem; }
.roles-empty { display: flex; align-items: center; gap: 8px; font-size: 0.875rem; padding: 10px; }
.add-role { display: flex; gap: 8px; align-items: center; }
.add-role .p-select { flex: 1; }

/* Meta */
.meta-section { display: flex; flex-direction: column; gap: 12px; }
</style>
