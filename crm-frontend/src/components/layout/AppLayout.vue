<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': ui.sidebarCollapsed }">

    <!-- ============================================================ -->
    <!--  SIDEBAR                                                       -->
    <!-- ============================================================ -->
    <aside class="sidebar">
      <!-- Logo -->
      <div class="sidebar-logo">
        <div class="logo-icon">
          <i class="pi pi-cloud" />
        </div>
        <span class="logo-text">CRM <strong>Cloud</strong></span>
      </div>

      <!-- Navigation -->
      <nav class="sidebar-nav">
        <template v-for="item in visibleNavItems" :key="item.to">
          <RouterLink
            :to="item.to"
            class="nav-item"
            :class="{ active: isActive(item.to) }"
            v-tooltip.right="ui.sidebarCollapsed ? item.label : undefined"
          >
            <i :class="['pi', item.icon]" />
            <span class="nav-label">{{ item.label }}</span>
            <span v-if="item.badge" class="nav-badge">{{ item.badge }}</span>
          </RouterLink>
        </template>
      </nav>

      <!-- Collapse toggle -->
      <button class="sidebar-toggle" @click="ui.toggleSidebar()">
        <i :class="['pi', ui.sidebarCollapsed ? 'pi-chevron-right' : 'pi-chevron-left']" />
      </button>
    </aside>

    <!-- ============================================================ -->
    <!--  MAIN AREA                                                     -->
    <!-- ============================================================ -->
    <div class="main-area">

      <!-- TOPBAR -->
      <header class="topbar">
        <div class="topbar-left">
          <!-- Хлебные крошки / заголовок страницы -->
          <span class="page-title">{{ currentPageTitle }}</span>
        </div>

        <div class="topbar-right">
          <!-- Часы -->
          <div class="topbar-clock">
            <i class="pi pi-clock" />
            <span>{{ currentTime }}</span>
          </div>

          <!-- Переключатель темы -->
          <button class="icon-btn" @click="ui.toggleTheme()" v-tooltip.bottom="'Сменить тему'">
            <i :class="['pi', ui.isDark ? 'pi-sun' : 'pi-moon']" />
          </button>

          <!-- Уведомления (заглушка) -->
          <button class="icon-btn" v-tooltip.bottom="'Уведомления'">
            <i class="pi pi-bell" />
          </button>

          <!-- Аватар пользователя -->
          <div class="user-menu" @click="userMenuVisible = !userMenuVisible" ref="userMenuRef">
            <div class="avatar">
              <img v-if="auth.user?.avatarUrl" :src="auth.user.avatarUrl" alt="avatar" />
              <span v-else class="avatar-initials">{{ userInitials }}</span>
            </div>
            <span class="user-name">{{ auth.user?.firstName }}</span>
            <i class="pi pi-chevron-down" style="font-size:10px" />
          </div>
        </div>
      </header>

      <!-- User dropdown -->
      <Teleport to="body">
        <div v-if="userMenuVisible" class="user-dropdown" :style="dropdownStyle">
          <div class="user-dropdown-header">
            <div class="dropdown-name">{{ auth.user?.firstName }} {{ auth.user?.lastName }}</div>
            <div class="dropdown-email">{{ auth.user?.email }}</div>
          </div>
          <div class="dropdown-divider" />
          <button class="dropdown-item" @click="goToProfile">
            <i class="pi pi-user" /> Профиль
          </button>
          <button v-if="auth.isAdmin" class="dropdown-item" @click="router.push('/admin/users')">
            <i class="pi pi-cog" /> Панель администратора
          </button>
          <div class="dropdown-divider" />
          <button class="dropdown-item danger" @click="handleLogout">
            <i class="pi pi-sign-out" /> Выход
          </button>
        </div>
      </Teleport>

      <!-- PAGE CONTENT -->
      <main class="page-content">
        <RouterView v-slot="{ Component }">
          <Transition name="fade" mode="out-in">
            <component :is="Component" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

const router = useRouter()
const route  = useRoute()
const auth   = useAuthStore()
const ui     = useUiStore()

// ---- Время ----
const currentTime = ref('')
let timeInterval: ReturnType<typeof setInterval>

function updateTime() {
  currentTime.value = new Date().toLocaleTimeString('ru-RU', {
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

onMounted(() => { updateTime(); timeInterval = setInterval(updateTime, 1000) })
onUnmounted(() => clearInterval(timeInterval))

// ---- Navigation ----
const navItems = [
  { to: '/dashboard',  icon: 'pi-home',         label: 'Главная',  permission: null },
  { to: '/customers',  icon: 'pi-users',         label: 'Клиенты',  permission: 'CUSTOMER_VIEW' },
  { to: '/tasks',      icon: 'pi-calendar',      label: 'Задачи',   permission: 'TASK_VIEW' },
  { to: '/orders',     icon: 'pi-shopping-cart', label: 'Заказы',   permission: 'ORDER_VIEW' },
  { to: '/funnel',     icon: 'pi-filter',        label: 'Воронка',  permission: 'ORDER_VIEW' },
  { to: '/admin/users',icon: 'pi-shield',        label: 'Управление',permission: 'USER_MANAGE' }
]

const visibleNavItems = computed(() =>
  navItems.filter(item =>
    !item.permission || auth.hasPermission(item.permission)
  )
)

function isActive(to: string) {
  return route.path === to || route.path.startsWith(to + '/')
}

const pageTitles: Record<string, string> = {
  '/dashboard': 'Главная',
  '/customers': 'Клиенты',
  '/tasks': 'Задачи',
  '/orders': 'Заказы',
  '/funnel': 'Воронка продаж',
  '/admin/users': 'Управление пользователями',
  '/admin/roles': 'Роли и права',
  '/admin/modules': 'Настройки модулей'
}

const currentPageTitle = computed(() =>
  pageTitles[route.path] ?? pageTitles[Object.keys(pageTitles).find(k => route.path.startsWith(k)) ?? ''] ?? ''
)

// ---- User menu ----
const userMenuVisible = ref(false)
const userMenuRef = ref<HTMLElement>()
const dropdownStyle = ref({})

function closeOnOutsideClick(e: MouseEvent) {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target as Node)) {
    userMenuVisible.value = false
  }
}

onMounted(() => document.addEventListener('click', closeOnOutsideClick))
onUnmounted(() => document.removeEventListener('click', closeOnOutsideClick))

const userInitials = computed(() => {
  if (!auth.user) return '?'
  return (auth.user.firstName[0] + auth.user.lastName[0]).toUpperCase()
})

function goToProfile() {
  userMenuVisible.value = false
  router.push('/profile')
}

async function handleLogout() {
  userMenuVisible.value = false
  await auth.logout()
}
</script>

<style scoped>
/* ============================================================
   APP SHELL
   ============================================================ */
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

/* ============================================================
   SIDEBAR
   ============================================================ */
.sidebar {
  width: var(--sidebar-width);
  height: 100vh;
  background: var(--color-bg-sidebar);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width var(--transition);
  position: relative;
  z-index: 100;
  border-right: 1px solid rgba(255,255,255,.05);
}

.app-shell.sidebar-collapsed .sidebar {
  width: var(--sidebar-collapsed);
}

/* Logo */
.sidebar-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px;
  border-bottom: 1px solid rgba(255,255,255,.06);
  overflow: hidden;
}

.logo-icon {
  width: 32px;
  height: 32px;
  background: var(--color-primary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: white;
  font-size: 15px;
}

.logo-text {
  font-size: 15px;
  color: #e2e8f0;
  white-space: nowrap;
  opacity: 1;
  transition: opacity var(--transition);
}

.logo-text strong { color: var(--color-primary); }

.sidebar-collapsed .logo-text { opacity: 0; pointer-events: none; }

/* Nav */
.sidebar-nav {
  flex: 1;
  padding: 12px 10px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow-y: auto;
  overflow-x: hidden;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 10px;
  border-radius: var(--radius);
  color: var(--color-text-sidebar);
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  transition: background var(--transition), color var(--transition);
  overflow: hidden;
  white-space: nowrap;
  position: relative;
}

.nav-item .pi {
  font-size: 16px;
  flex-shrink: 0;
  width: 20px;
  text-align: center;
}

.nav-item:hover {
  background: rgba(255,255,255,.07);
  color: #fff;
}

.nav-item.active {
  background: var(--color-primary);
  color: #fff;
}

.nav-label {
  opacity: 1;
  transition: opacity var(--transition);
}

.sidebar-collapsed .nav-label { opacity: 0; }

.nav-badge {
  margin-left: auto;
  background: var(--color-danger);
  color: white;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 99px;
}

/* Collapse toggle */
.sidebar-toggle {
  border: none;
  background: rgba(255,255,255,.05);
  color: var(--color-text-sidebar);
  cursor: pointer;
  padding: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-top: 1px solid rgba(255,255,255,.06);
  transition: background var(--transition);
}

.sidebar-toggle:hover {
  background: rgba(255,255,255,.1);
  color: #fff;
}

/* ============================================================
   MAIN AREA
   ============================================================ */
.main-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

/* ---- TOPBAR ---- */
.topbar {
  height: var(--topbar-height);
  background: var(--color-bg-card);
  border-bottom: 1px solid var(--color-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 50;
}

.page-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.topbar-clock {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-muted);
  padding: 0 12px;
  font-variant-numeric: tabular-nums;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: var(--radius);
  color: var(--color-text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  transition: background var(--transition), color var(--transition);
}

.icon-btn:hover {
  background: var(--color-bg-hover);
  color: var(--color-text);
}

/* User avatar menu */
.user-menu {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border-radius: var(--radius);
  cursor: pointer;
  margin-left: 4px;
  transition: background var(--transition);
}

.user-menu:hover { background: var(--color-bg-hover); }

.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: var(--color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.avatar img { width: 100%; height: 100%; object-fit: cover; }

.avatar-initials {
  font-size: 11px;
  font-weight: 700;
  color: white;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text);
}

/* User dropdown */
.user-dropdown {
  position: fixed;
  right: 16px;
  top: 60px;
  width: 220px;
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  box-shadow: var(--shadow-modal);
  z-index: 9999;
  overflow: hidden;
  animation: fadeIn 150ms ease;
}

@keyframes fadeIn { from { opacity: 0; transform: translateY(-4px); } to { opacity: 1; } }

.user-dropdown-header { padding: 14px 16px; }

.dropdown-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.dropdown-email {
  font-size: 12px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.dropdown-divider {
  height: 1px;
  background: var(--color-border);
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 10px 16px;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-text);
  text-align: left;
  transition: background var(--transition);
}

.dropdown-item:hover { background: var(--color-bg-hover); }
.dropdown-item.danger { color: var(--color-danger); }
.dropdown-item.danger:hover { background: #fee2e2; }
.dark .dropdown-item.danger:hover { background: rgba(239,68,68,.1); }

/* ---- PAGE CONTENT ---- */
.page-content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: var(--color-bg);
}

/* ---- Route transitions ---- */
.fade-enter-active, .fade-leave-active { transition: opacity 120ms ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
