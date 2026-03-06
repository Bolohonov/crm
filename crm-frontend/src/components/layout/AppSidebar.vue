<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': ui.sidebarCollapsed }">
    <!-- Логотип -->
    <div class="sidebar__logo">
      <div class="sidebar__logo-icon">
        <i class="pi pi-cloud" />
      </div>
      <Transition name="label">
        <span v-if="!ui.sidebarCollapsed" class="sidebar__logo-text">
          CRM <em>Cloud</em>
        </span>
      </Transition>
    </div>

    <!-- Навигация -->
    <nav class="sidebar__nav">
      <div class="sidebar__section">
        <span v-if="!ui.sidebarCollapsed" class="sidebar__section-label">Главное</span>
        <SidebarItem
            v-for="item in mainItems"
            :key="item.to"
            :item="item"
            :collapsed="ui.sidebarCollapsed"
        />
      </div>

      <div class="sidebar__section" v-if="adminItems.length">
        <span v-if="!ui.sidebarCollapsed" class="sidebar__section-label">Администрирование</span>
        <SidebarItem
            v-for="item in adminItems"
            :key="item.to"
            :item="item"
            :collapsed="ui.sidebarCollapsed"
        />
      </div>
    </nav>

    <!-- Профиль пользователя -->
    <div class="sidebar__footer">
      <div class="sidebar__user" @click="goToProfile">
        <Avatar
            :label="avatarLabel"
            :image="auth.user?.avatarUrl"
            shape="circle"
            size="small"
            class="sidebar__avatar"
        />
        <Transition name="label">
          <div v-if="!ui.sidebarCollapsed" class="sidebar__user-info">
            <span class="sidebar__user-name">{{ auth.user?.firstName }} {{ auth.user?.lastName }}</span>
            <span class="sidebar__user-role">{{ planLabel }}</span>
          </div>
        </Transition>
      </div>

      <!-- Кнопка свернуть -->
      <Button
          :icon="ui.sidebarCollapsed ? 'pi pi-chevron-right' : 'pi pi-chevron-left'"
          text
          rounded
          size="small"
          class="sidebar__collapse-btn"
          @click="ui.toggleSidebar()"
      />
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import Avatar from 'primevue/avatar'
import Button from 'primevue/button'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import SidebarItem from './SidebarItem.vue'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

interface NavItem {
  label: string
  to: string
  icon: string
  module?: string
  permission?: string
}

const allMainItems: NavItem[] = [
  { label: 'Дашборд',  to: '/dashboard', icon: 'pi pi-home' },
  { label: 'Клиенты',  to: '/customers', icon: 'pi pi-users',     module: 'CUSTOMERS', permission: 'CUSTOMER_VIEW' },
  { label: 'Задачи',   to: '/tasks',     icon: 'pi pi-calendar',   module: 'TASKS',     permission: 'TASK_VIEW' },
  { label: 'Заказы',   to: '/orders',    icon: 'pi pi-shopping-cart', module: 'ORDERS',  permission: 'ORDER_VIEW' },
]

const allAdminItems: NavItem[] = [
  { label: 'Панель',       to: '/admin',        icon: 'pi pi-cog' },
  { label: 'Пользователи', to: '/admin/users',  icon: 'pi pi-user-edit', permission: 'USER_MANAGE' },
  { label: 'Роли',         to: '/admin/roles',  icon: 'pi pi-shield',    permission: 'ROLE_MANAGE' },
]

// Фильтруем по включённым модулям и правам
const mainItems = computed(() =>
    allMainItems.filter(item => {
      if (item.module && !auth.hasModule(item.module)) return false
      if (item.permission && !auth.can(item.permission)) return false
      return true
    })
)

const adminItems = computed(() =>
    auth.isAdmin
        ? allAdminItems.filter(item => !item.permission || auth.can(item.permission))
        : []
)

const avatarLabel = computed(() => {
  const u = auth.user
  if (!u) return '?'
  return `${u.firstName[0]}${u.lastName[0]}`.toUpperCase()
})

const planLabel = computed(() =>
    auth.user?.tenantPlan === 'STANDARD' ? 'Standard' : 'Free plan'
)

function goToProfile() {
  // TODO: router.push('/profile')
}
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  position: sticky;
  top: 0;
  background: var(--sidebar-bg);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  transition: width var(--transition-slow);
  z-index: 100;
}

/* ---- Логотип ---- */
.sidebar__logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

.sidebar__logo-icon {
  width: 32px; height: 32px;
  background: var(--accent-500);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  flex-shrink: 0;
  box-shadow: 0 0 16px rgba(59,130,246,0.3);
}

.sidebar__logo-text {
  font-size: 1.0625rem;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
  letter-spacing: -0.01em;
}

.sidebar__logo-text em {
  font-style: normal;
  color: var(--accent-400);
}

/* ---- Навигация ---- */
.sidebar__nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.sidebar__section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar__section-label {
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-muted);
  padding: 0 8px;
  margin-bottom: 4px;
}

/* ---- Footer / User ---- */
.sidebar__footer {
  border-top: 1px solid var(--border-subtle);
  padding: 12px 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar__user {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
  min-width: 0;
}

.sidebar__user:hover { background: var(--bg-hover); }

.sidebar__avatar { flex-shrink: 0; }

.sidebar__user-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.sidebar__user-name {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.sidebar__user-role {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.sidebar__collapse-btn {
  flex-shrink: 0;
  color: var(--text-muted) !important;
}

/* Анимация текстовых лейблов при сворачивании */
.label-enter-active, .label-leave-active { transition: opacity 150ms, width 150ms; }
.label-enter-from, .label-leave-to { opacity: 0; width: 0; }

/* Светлая тема */
[data-theme="light"] .sidebar {
  background: var(--sidebar-bg);
  border-right-color: var(--border-default);
}
.sidebar--collapsed {
  width: 60px;
}

.sidebar--collapsed .sidebar__logo-icon {
  margin: 0 auto;
}
</style>
