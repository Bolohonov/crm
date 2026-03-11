<template>
  <div class="roles-view animate-fade-in">

    <!-- ── Заголовок ─────────────────────────────────────────────── -->
    <div class="section-header">
      <div>
        <h2 class="section-title">Роли и права доступа</h2>
        <p class="section-sub">{{ roles.length }} ролей · управляйте доступом к разделам системы</p>
      </div>
      <Button icon="pi pi-plus" label="Создать роль" @click="openCreate" />
    </div>

    <!-- ── Layout: список ролей + детали ─────────────────────────── -->
    <div class="roles-layout">

      <!-- Список ролей -->
      <div class="roles-list-panel card">
        <div v-if="loading" class="loading-state">
          <ProgressSpinner style="width:32px;height:32px" />
        </div>
        <div v-else-if="roles.length === 0" class="empty-state">
          <i class="pi pi-shield" />
          <p>Роли не найдены</p>
        </div>
        <div v-else class="roles-list">
          <div
              v-for="role in roles"
              :key="role.id"
              class="role-item"
              :class="{ 'role-item--active': selectedRoleId === role.id, 'role-item--system': role.isSystem }"
              @click="selectRole(role)"
          >
            <div class="role-item__dot" :style="{ background: role.color || '#6b7280' }" />
            <div class="role-item__body">
              <div class="role-item__name">{{ role.name }}</div>
              <div class="role-item__meta">
                <span><i class="pi pi-users" /> {{ role.userCount ?? 0 }}</span>
                <span><i class="pi pi-key" /> {{ (role.permissions || []).length }}</span>
              </div>
            </div>
            <Tag v-if="role.isSystem" value="Сист." severity="secondary" style="font-size:.65rem;flex-shrink:0" />
            <Button
                v-else
                icon="pi pi-ellipsis-v"
                text rounded size="small"
                @click.stop="openRoleMenu($event, role)"
            />
          </div>
        </div>
      </div>

      <!-- Детали роли -->
      <div class="role-detail card" v-if="selectedRole">
        <div class="role-detail__header">
          <div class="role-color-bar" :style="{ background: selectedRole.color || '#6b7280' }" />
          <div class="role-detail__title-block">
            <h3 class="role-detail__name">{{ selectedRole.name }}</h3>
            <p class="role-detail__desc text-muted">{{ selectedRole.description || 'Описание не указано' }}</p>
          </div>
          <Button
              v-if="!selectedRole.isSystem"
              icon="pi pi-pencil"
              text
              @click="openEdit(selectedRole)"
              v-tooltip="'Редактировать'"
          />
        </div>

        <!-- Права по модулям -->
        <div class="permissions-section">
          <div class="permissions-section__title">Права доступа</div>

          <div v-if="loadingPerms" class="loading-mini">
            <ProgressSpinner style="width:24px;height:24px" />
          </div>

          <div v-else class="permission-modules">
            <div
                v-for="module in permissionModules"
                :key="module.code"
                class="perm-module"
            >
              <div class="perm-module__header">
                <span class="perm-module__icon" :style="{ color: module.color }"><i :class="module.icon" /></span>
                <span class="perm-module__name">{{ module.name }}</span>
                <span class="perm-module__count">
                  {{ countGranted(module) }}/{{ module.permissions.length }}
                </span>
              </div>
              <div class="perm-module__body">
                <div
                    v-for="perm in module.permissions"
                    :key="perm.id"
                    class="perm-item"
                    :class="{ 'perm-item--granted': hasPermission(perm.id) }"
                    @click="!selectedRole.isSystem && togglePermission(perm)"
                >
                  <div class="perm-item__check">
                    <i v-if="hasPermission(perm.id)" class="pi pi-check" />
                  </div>
                  <div class="perm-item__info">
                    <div class="perm-item__name">{{ perm.name }}</div>
                    <div class="perm-item__code text-muted">{{ perm.code }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="permissions-save" v-if="!selectedRole.isSystem && permsDirty">
            <Button label="Сохранить права" :loading="savingPerms" @click="savePermissions" />
            <Button label="Отмена" text @click="resetPermissions" />
          </div>
        </div>
      </div>

      <!-- Пустое состояние детали -->
      <div class="role-detail-empty card" v-else>
        <i class="pi pi-shield" />
        <p>Выберите роль для просмотра прав</p>
      </div>
    </div>

    <!-- ── Контекстное меню ───────────────────────────────────────── -->
    <ContextMenu ref="roleMenu" :model="roleMenuItems" />

    <!-- ── Диалог роли ────────────────────────────────────────────── -->
    <RoleFormDialog
        v-model:visible="formVisible"
        :role="editingRole"
        @saved="onRoleSaved"
    />


  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import { useConfirm } from 'primevue/useconfirm'
import { adminApi } from '@/api/admin'
import RoleFormDialog from '@/components/admin/RoleFormDialog.vue'

const toast   = useToast()
const confirm = useConfirm()

// ── Состояние ────────────────────────────────────────────────────
const roles          = ref<any[]>([])
const allPermissions = ref<any[]>([])
const loading        = ref(false)
const loadingPerms   = ref(false)
const savingPerms    = ref(false)

const selectedRoleId   = ref<string | null>(null)
const selectedRole     = ref<any>(null)
const grantedPermIds   = ref(new Set<string>())
const originalPermIds  = ref(new Set<string>())
const permsDirty       = ref(false)

const formVisible  = ref(false)
const editingRole  = ref<any>(null)
const roleMenu     = ref()
const activeRole   = ref<any>(null)

const roleMenuItems = [
  { label: 'Редактировать', icon: 'pi pi-pencil',  command: () => openEdit(activeRole.value) },
  { separator: true },
  { label: 'Удалить',       icon: 'pi pi-trash',   class: 'menu-danger', command: () => confirmDelete(activeRole.value) },
]

// ── Модули прав (мета-данные для группировки) ────────────────────
const MODULE_META = [
  { code: 'CUSTOMERS', name: 'Клиенты',   icon: 'pi pi-users',         color: '#3b82f6' },
  { code: 'TASKS',     name: 'Задачи',    icon: 'pi pi-calendar',      color: '#8b5cf6' },
  { code: 'ORDERS',    name: 'Заказы',    icon: 'pi pi-shopping-cart', color: '#22c55e' },
  { code: 'PRODUCTS',  name: 'Товары',    icon: 'pi pi-box',           color: '#f59e0b' },
  { code: 'USERS',     name: 'Пользователи', icon: 'pi pi-user-edit', color: '#ec4899' },
  { code: 'ADMIN',     name: 'Администрирование', icon: 'pi pi-cog',  color: '#6b7280' },
]

const permissionModules = computed(() => {
  return MODULE_META.map(m => ({
    ...m,
    permissions: allPermissions.value.filter(p => p.module === m.code || p.code.startsWith(m.code.charAt(0))),
  })).filter(m => m.permissions.length > 0)
})

// ── Загрузка ─────────────────────────────────────────────────────
async function loadRoles() {
  loading.value = true
  try {
    const { data: res } = await adminApi.listRoles()
    roles.value = res.data ?? []
  } finally {
    loading.value = false
  }
}

async function loadPermissions() {
  try {
    const { data: res } = await adminApi.listPermissions()
    allPermissions.value = res.data ?? []
  } catch {}
}

async function selectRole(role: any) {
  if (selectedRoleId.value === role.id) return
  selectedRoleId.value = role.id

  loadingPerms.value = true
  try {
    const { data: res } = await adminApi.getRole(role.id)
    selectedRole.value = res.data
    const ids = new Set<string>((res.data?.permissions ?? []).map((p: any) => p.id))
    grantedPermIds.value  = ids
    originalPermIds.value = new Set(ids)
    permsDirty.value = false
  } finally {
    loadingPerms.value = false
  }
}

// ── Права ─────────────────────────────────────────────────────────
function hasPermission(permId: string): boolean { return grantedPermIds.value.has(permId) }
function countGranted(module: any): number { return module.permissions.filter((p: any) => hasPermission(p.id)).length }

function togglePermission(perm: any) {
  if (grantedPermIds.value.has(perm.id)) grantedPermIds.value.delete(perm.id)
  else grantedPermIds.value.add(perm.id)
  permsDirty.value = true
}

function resetPermissions() {
  grantedPermIds.value = new Set(originalPermIds.value)
  permsDirty.value = false
}

async function savePermissions() {
  savingPerms.value = true
  try {
    await adminApi.updateRole(selectedRole.value.id, {
      permissionIds: [...grantedPermIds.value],
    })
    originalPermIds.value = new Set(grantedPermIds.value)
    permsDirty.value = false
    toast.add({ severity: 'success', summary: 'Права сохранены', life: 2500 })
    await loadRoles()
  } catch {
    toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 })
  } finally {
    savingPerms.value = false
  }
}

// ── CRUD ──────────────────────────────────────────────────────────
function openCreate() { editingRole.value = null; formVisible.value = true }
function openEdit(role: any) { editingRole.value = role; formVisible.value = true }
function openRoleMenu(event: MouseEvent, role: any) { activeRole.value = role; roleMenu.value.show(event) }

function onRoleSaved() { formVisible.value = false; loadRoles() }

function confirmDelete(role: any) {
  confirm.require({
    message: `Удалить роль «${role.name}»? Пользователи потеряют связанные права.`,
    header: 'Удаление роли',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    accept: async () => {
      try {
        await adminApi.deleteRole(role.id)
        toast.add({ severity: 'success', summary: 'Роль удалена', life: 2500 })
        if (selectedRoleId.value === role.id) { selectedRole.value = null; selectedRoleId.value = null }
        loadRoles()
      } catch {
        toast.add({ severity: 'error', summary: 'Ошибка', life: 3000 })
      }
    },
  })
}

onMounted(() => { loadRoles(); loadPermissions() })
</script>

<style scoped>
.roles-view { display: flex; flex-direction: column; gap: 20px; }
.section-header { display: flex; justify-content: space-between; align-items: flex-start; }
.section-title  { font-size: 1.125rem; font-weight: 700; margin: 0 0 4px; }
.section-sub    { font-size: .875rem; color: var(--color-text-muted); margin: 0; }

.card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; }

/* ── Layout ─────────────────────────────────────────────────────── */
.roles-layout { display: grid; grid-template-columns: 300px 1fr; gap: 16px; align-items: start; }
@media (max-width: 900px) { .roles-layout { grid-template-columns: 1fr; } }

/* ── Список ─────────────────────────────────────────────────────── */
.roles-list-panel { overflow: hidden; }
.roles-list { display: flex; flex-direction: column; }

.role-item { display: flex; align-items: center; gap: 10px; padding: 12px 16px; cursor: pointer; border-bottom: 1px solid var(--color-border); transition: background .12s; }
.role-item:last-child { border-bottom: none; }
.role-item:hover { background: var(--color-bg-hover); }
.role-item--active { background: color-mix(in srgb, var(--color-primary) 8%, transparent); }
.role-item--system { opacity: .8; }

.role-item__dot  { width: 10px; height: 10px; border-radius: 50%; flex-shrink: 0; }
.role-item__body { flex: 1; min-width: 0; }
.role-item__name { font-weight: 600; font-size: .875rem; }
.role-item__meta { display: flex; gap: 10px; font-size: .75rem; color: var(--color-text-muted); margin-top: 3px; }
.role-item__meta .pi { font-size: .65rem; margin-right: 3px; }

/* ── Детали ─────────────────────────────────────────────────────── */
.role-detail { overflow: hidden; }
.role-detail__header { display: flex; align-items: flex-start; gap: 14px; padding: 18px 20px; border-bottom: 1px solid var(--color-border); }
.role-color-bar { width: 4px; height: 48px; border-radius: 2px; flex-shrink: 0; }
.role-detail__title-block { flex: 1; }
.role-detail__name { font-size: 1.125rem; font-weight: 700; margin: 0 0 4px; }
.role-detail__desc { font-size: .875rem; margin: 0; }

/* ── Права ──────────────────────────────────────────────────────── */
.permissions-section { padding: 20px; }
.permissions-section__title { font-size: .8rem; font-weight: 700; text-transform: uppercase; letter-spacing: .06em; color: var(--color-text-muted); margin-bottom: 16px; }

.permission-modules { display: flex; flex-direction: column; gap: 20px; }

.perm-module__header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.perm-module__icon   { font-size: .875rem; width: 24px; text-align: center; }
.perm-module__name   { font-weight: 600; font-size: .875rem; flex: 1; }
.perm-module__count  { font-size: .75rem; color: var(--color-text-muted); background: var(--color-bg-hover); padding: 2px 8px; border-radius: 10px; }

.perm-module__body { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 6px; padding-left: 32px; }

.perm-item { display: flex; align-items: center; gap: 10px; padding: 8px 10px; border-radius: 8px; border: 1px solid var(--color-border); cursor: pointer; transition: all .12s; }
.perm-item:hover { border-color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 5%, transparent); }
.perm-item--granted { border-color: var(--color-primary); background: color-mix(in srgb, var(--color-primary) 8%, transparent); }

.perm-item__check { width: 18px; height: 18px; border-radius: 4px; border: 1.5px solid var(--color-border); display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: .65rem; color: var(--color-primary); }
.perm-item--granted .perm-item__check { border-color: var(--color-primary); background: var(--color-primary); color: #fff; }

.perm-item__name { font-size: .8rem; font-weight: 500; }
.perm-item__code { font-size: .7rem; font-family: 'JetBrains Mono', monospace; }

.permissions-save { display: flex; justify-content: flex-end; gap: 8px; margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--color-border); }

.role-detail-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; min-height: 300px; color: var(--color-text-muted); }
.role-detail-empty .pi { font-size: 2.5rem; }

.loading-state { display: flex; justify-content: center; padding: 48px; }
.loading-mini  { display: flex; justify-content: center; padding: 20px; }
.empty-state   { padding: 48px 20px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; color: var(--color-text-muted); }
.empty-state .pi { font-size: 2rem; }

.text-muted { color: var(--color-text-muted); }
:deep(.menu-danger .p-menuitem-text),
:deep(.menu-danger .p-menuitem-icon) { color: var(--color-danger) !important; }

.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
</style>
