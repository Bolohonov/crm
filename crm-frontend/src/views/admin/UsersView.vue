<template>
  <div class="users-view animate-fade-in">

    <!-- ── Заголовок ─────────────────────────────────────────────── -->
    <div class="section-header">
      <div>
        <h2 class="section-title">Пользователи</h2>
        <p class="section-sub">{{ total }} пользователей в системе</p>
      </div>
      <Button icon="pi pi-user-plus" label="Пригласить" @click="inviteVisible = true" />
    </div>

    <!-- ── Поиск ──────────────────────────────────────────────────── -->
    <div class="search-bar">
      <IconField style="flex:1">
        <InputIcon class="pi pi-search" />
        <InputText v-model="query" placeholder="Поиск по имени или email…" style="width:100%" @input="onSearchDebounced" />
      </IconField>
      <Select
        v-model="statusFilter"
        :options="statusOptions"
        option-label="label"
        option-value="value"
        placeholder="Все статусы"
        show-clear
        style="width:160px"
        @change="loadUsers"
      />
    </div>

    <!-- ── Карточки пользователей ─────────────────────────────────── -->
    <div v-if="loading" class="loading-state">
      <ProgressSpinner style="width:36px;height:36px" />
    </div>

    <div v-else-if="users.length === 0" class="empty-state">
      <i class="pi pi-users" />
      <p>Пользователи не найдены</p>
    </div>

    <div v-else class="user-grid">
      <div
        v-for="user in users"
        :key="user.id"
        class="user-card"
        :class="{ 'user-card--blocked': user.status === 'BLOCKED' }"
      >
        <!-- Аватар -->
        <div class="user-card__avatar" :style="{ background: avatarColor(user) }">
          {{ avatarLetter(user) }}
        </div>

        <!-- Инфо -->
        <div class="user-card__info">
          <div class="user-card__name">{{ user.lastName }} {{ user.firstName }}</div>
          <div class="user-card__email">{{ user.email }}</div>
          <div class="user-card__roles">
            <Tag
              v-for="role in user.roles.slice(0, 3)"
              :key="role.id"
              :value="role.name"
              severity="secondary"
              style="font-size:.7rem;padding:2px 7px"
            />
            <span v-if="user.roles.length > 3" class="roles-more">+{{ user.roles.length - 3 }}</span>
          </div>
        </div>

        <!-- Статус и тип -->
        <div class="user-card__status">
          <div class="status-badge" :class="`status-badge--${user.status.toLowerCase()}`">
            <span class="status-badge__dot" />
            {{ statusLabel(user.status) }}
          </div>
          <div class="user-type-badge" :class="user.userType === 'ADMIN' ? 'user-type-badge--admin' : ''">
            {{ user.userType === 'ADMIN' ? 'Администратор' : 'Пользователь' }}
          </div>
        </div>

        <!-- Действия -->
        <div class="user-card__actions">
          <Button icon="pi pi-pencil" text rounded size="small" @click="openEdit(user)" v-tooltip="'Редактировать'" />
          <Button
            v-if="user.status === 'ACTIVE' && user.userType !== 'ADMIN'"
            icon="pi pi-ban"
            text rounded size="small"
            severity="danger"
            @click="confirmBlock(user)"
            v-tooltip="'Заблокировать'"
          />
          <Button
            v-if="user.status === 'BLOCKED'"
            icon="pi pi-check-circle"
            text rounded size="small"
            severity="success"
            @click="unblockUser(user)"
            v-tooltip="'Разблокировать'"
          />
          <Button
            icon="pi pi-shield"
            text rounded size="small"
            @click="openRoles(user)"
            v-tooltip="'Роли'"
          />
        </div>
      </div>
    </div>

    <!-- Пагинация -->
    <div class="pagination-wrap" v-if="totalPages > 1">
      <Paginator
        :rows="pageSize"
        :total-records="total"
        :first="currentPage * pageSize"
        @page="onPage"
      />
    </div>

    <!-- ── Диалог редактирования ──────────────────────────────────── -->
    <Dialog v-model:visible="editVisible" :header="'Редактировать пользователя'" modal style="width:440px">
      <div class="form-grid" v-if="editingUser">
        <div class="field">
          <label>Фамилия</label>
          <InputText v-model="editingUser.lastName" style="width:100%" />
        </div>
        <div class="field">
          <label>Имя</label>
          <InputText v-model="editingUser.firstName" style="width:100%" />
        </div>
        <div class="field">
          <label>Отчество</label>
          <InputText v-model="editingUser.middleName" style="width:100%" />
        </div>
        <div class="field">
          <label>Телефон</label>
          <InputText v-model="editingUser.phone" style="width:100%" />
        </div>
      </div>
      <template #footer>
        <Button label="Отмена" text @click="editVisible = false" />
        <Button label="Сохранить" :loading="saving" @click="saveUser" />
      </template>
    </Dialog>

    <!-- ── Диалог назначения ролей ────────────────────────────────── -->
    <Dialog v-model:visible="rolesVisible" header="Роли пользователя" modal style="width:420px">
      <div v-if="editingUser">
        <p class="roles-dialog__user">{{ editingUser.lastName }} {{ editingUser.firstName }}</p>
        <div class="roles-list">
          <div
            v-for="role in allRoles"
            :key="role.id"
            class="role-item"
            :class="{ 'role-item--selected': selectedRoleIds.has(role.id) }"
            @click="toggleRole(role.id)"
          >
            <Checkbox :model-value="selectedRoleIds.has(role.id)" :binary="true" />
            <div class="role-item__info">
              <div class="role-item__name">{{ role.name }}</div>
              <div class="role-item__desc">{{ role.description }}</div>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <Button label="Отмена" text @click="rolesVisible = false" />
        <Button label="Сохранить" :loading="saving" @click="saveRoles" />
      </template>
    </Dialog>

    <!-- ── Диалог приглашения ─────────────────────────────────────── -->
    <UserInviteDialog v-model:visible="inviteVisible" @invited="loadUsers" />

    <ConfirmDialog />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { usersApi } from '@/api/users'
import UserInviteDialog from '@/components/admin/UserInviteDialog.vue'

const toast   = useToast()
const confirm = useConfirm()

const users       = ref<any[]>([])
const allRoles    = ref<any[]>([])
const loading     = ref(false)
const saving      = ref(false)
const total       = ref(0)
const totalPages  = ref(0)
const currentPage = ref(0)
const pageSize    = ref(20)

const query        = ref('')
const statusFilter = ref<string | null>(null)

const editVisible  = ref(false)
const rolesVisible = ref(false)
const inviteVisible = ref(false)
const editingUser  = ref<any>(null)
const selectedRoleIds = ref(new Set<string>())

const statusOptions = [
  { label: 'Активные',     value: 'ACTIVE' },
  { label: 'Заблокированные', value: 'BLOCKED' },
  { label: 'Не верифицированы', value: 'PENDING' },
]

async function loadUsers(page = currentPage.value) {
  loading.value = true
  try {
    const { data: res } = await usersApi.list({ page, size: pageSize.value, q: query.value || undefined })
    if (res.data) {
      let items = res.data.content
      if (statusFilter.value) items = items.filter((u: any) => u.status === statusFilter.value)
      users.value      = items
      total.value      = res.data.totalElements
      totalPages.value = res.data.totalPages
      currentPage.value = page
    }
  } finally {
    loading.value = false
  }
}

function onPage(e: any) { pageSize.value = e.rows; loadUsers(e.page) }
let searchTimer: ReturnType<typeof setTimeout>
function onSearchDebounced() { clearTimeout(searchTimer); searchTimer = setTimeout(() => loadUsers(0), 400) }

function openEdit(user: any) { editingUser.value = { ...user }; editVisible.value = true }

function openRoles(user: any) {
  editingUser.value = user
  selectedRoleIds.value = new Set(user.roles.map((r: any) => r.id))
  rolesVisible.value = true
  // Загружаем все роли
  import('@/api/admin').then(m => m.rolesApi.getAll().then(({ data: res }: any) => { allRoles.value = res.data ?? [] }))
}

function toggleRole(id: string) {
  if (selectedRoleIds.value.has(id)) selectedRoleIds.value.delete(id)
  else selectedRoleIds.value.add(id)
}

async function saveUser() {
  saving.value = true
  try {
    await usersApi.update(editingUser.value.id, {
      firstName: editingUser.value.firstName,
      lastName: editingUser.value.lastName,
      middleName: editingUser.value.middleName,
      phone: editingUser.value.phone,
    })
    toast.add({ severity: 'success', summary: 'Сохранено', life: 2500 })
    editVisible.value = false
    loadUsers()
  } catch { toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 }) }
  finally { saving.value = false }
}

async function saveRoles() {
  saving.value = true
  try {
    await usersApi.setRoles(editingUser.value.id, [...selectedRoleIds.value])
    toast.add({ severity: 'success', summary: 'Роли обновлены', life: 2500 })
    rolesVisible.value = false
    loadUsers()
  } catch { toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 }) }
  finally { saving.value = false }
}

function confirmBlock(user: any) {
  confirm.require({
    message: `Заблокировать ${user.firstName} ${user.lastName}?`,
    header: 'Подтверждение',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: () => blockUser(user),
  })
}

async function blockUser(user: any) {
  try {
    await usersApi.setStatus(user.id, 'BLOCKED')
    toast.add({ severity: 'warn', summary: 'Пользователь заблокирован', life: 3000 })
    loadUsers()
  } catch { toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 }) }
}

async function unblockUser(user: any) {
  try {
    await usersApi.setStatus(user.id, 'ACTIVE')
    toast.add({ severity: 'success', summary: 'Пользователь разблокирован', life: 2500 })
    loadUsers()
  } catch { toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 }) }
}

function statusLabel(s: string): string { return { ACTIVE: 'Активен', BLOCKED: 'Заблокирован', PENDING: 'Ожидает' }[s] ?? s }

const AVATAR_COLORS = ['#3b82f6','#8b5cf6','#ec4899','#14b8a6','#f59e0b']
function avatarColor(u: any): string { return AVATAR_COLORS[(u.id?.charCodeAt(0) ?? 0) % AVATAR_COLORS.length] }
function avatarLetter(u: any): string { return (u.lastName ?? u.email ?? '?').charAt(0).toUpperCase() }

onMounted(() => loadUsers())
</script>

<style scoped>
.users-view { display: flex; flex-direction: column; gap: 20px; }

.section-header { display: flex; justify-content: space-between; align-items: flex-start; }
.section-title  { font-size: 1.125rem; font-weight: 700; margin: 0 0 4px; }
.section-sub    { font-size: .875rem; color: var(--color-text-muted); margin: 0; }

.search-bar { display: flex; gap: 12px; }

.user-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 12px; }

.user-card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; padding: 16px; display: flex; align-items: center; gap: 14px; transition: box-shadow .15s; }
.user-card:hover { box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.user-card--blocked { opacity: .65; }

.user-card__avatar { width: 42px; height: 42px; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; color: #fff; font-weight: 700; font-size: 1rem; }
.user-card__info { flex: 1; min-width: 0; }
.user-card__name  { font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.user-card__email { font-size: .8rem; color: var(--color-text-muted); margin: 2px 0 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.user-card__roles { display: flex; flex-wrap: wrap; gap: 4px; }
.roles-more { font-size: .72rem; color: var(--color-text-muted); align-self: center; }

.user-card__status { display: flex; flex-direction: column; gap: 5px; align-items: flex-end; flex-shrink: 0; }
.status-badge { display: flex; align-items: center; gap: 5px; font-size: .78rem; font-weight: 600; }
.status-badge__dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.status-badge--active    { color: var(--color-success); }
.status-badge--blocked   { color: var(--color-danger); }
.status-badge--pending   { color: var(--color-warning); }
.user-type-badge { font-size: .72rem; padding: 2px 8px; border-radius: 10px; background: var(--color-bg-hover); color: var(--color-text-muted); }
.user-type-badge--admin  { background: color-mix(in srgb, var(--color-primary) 15%, transparent); color: var(--color-primary); }

.user-card__actions { display: flex; flex-direction: column; gap: 2px; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; padding: 4px 0; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field label { font-size: .8rem; font-weight: 600; color: var(--color-text-muted); }

.roles-dialog__user { font-weight: 600; margin-bottom: 14px; }
.roles-list { display: flex; flex-direction: column; gap: 6px; max-height: 360px; overflow-y: auto; }
.role-item { display: flex; align-items: center; gap: 12px; padding: 10px 14px; border-radius: 8px; border: 1px solid var(--color-border); cursor: pointer; transition: all .15s; }
.role-item:hover { background: var(--color-bg-hover); }
.role-item--selected { border-color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 8%, transparent); }
.role-item__info { flex: 1; }
.role-item__name { font-weight: 500; font-size: .875rem; }
.role-item__desc { font-size: .75rem; color: var(--color-text-muted); margin-top: 2px; }

.loading-state { display: flex; justify-content: center; padding: 60px; }
.empty-state { padding: 60px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; color: var(--color-text-muted); }
.empty-state .pi { font-size: 2.5rem; }
.pagination-wrap { display: flex; justify-content: center; }
:deep(.p-paginator) { border: none; background: none; }

.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
</style>
