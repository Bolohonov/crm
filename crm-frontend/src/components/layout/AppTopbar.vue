<template>
  <header class="topbar">
    <!-- Хлебные крошки / заголовок страницы -->
    <div class="topbar__title">
      <h1 class="page-title">{{ pageTitle }}</h1>
    </div>

    <!-- Правая часть -->
    <div class="topbar__actions">
      <!-- Текущее время -->
      <div class="topbar__clock">
        <span class="clock__time font-mono">{{ currentTime }}</span>
        <span class="clock__date">{{ currentDate }}</span>
      </div>

      <!-- Переключатель темы -->
      <Button
        :icon="ui.theme === 'dark' ? 'pi pi-sun' : 'pi pi-moon'"
        text
        rounded
        size="small"
        class="topbar__theme-btn"
        @click="ui.toggleTheme()"
        v-tooltip.bottom="ui.theme === 'dark' ? 'Светлая тема' : 'Тёмная тема'"
      />

      <!-- Уведомления (placeholder) -->
      <Button
        icon="pi pi-bell"
        text
        rounded
        size="small"
        class="topbar__icon-btn"
        v-tooltip.bottom="'Уведомления'"
      />

      <!-- Аватар / меню пользователя -->
      <div class="topbar__user" @click="toggle" ref="userMenuRef">
        <Avatar
          :label="avatarLabel"
          :image="auth.user?.avatarUrl"
          shape="circle"
          size="small"
        />
        <span class="topbar__user-name">{{ auth.user?.firstName }}</span>
        <i class="pi pi-chevron-down topbar__chevron" />
      </div>

      <!-- Выпадающее меню -->
      <Menu ref="menu" :model="userMenuItems" popup />
    </div>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Button from 'primevue/button'
import Avatar from 'primevue/avatar'
import Menu from 'primevue/menu'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import dayjs from 'dayjs'
import 'dayjs/locale/ru'

dayjs.locale('ru')

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()
const menu = ref()

// ---- Часы ----
const now = ref(dayjs())
let timer: ReturnType<typeof setInterval>

onMounted(() => { timer = setInterval(() => { now.value = dayjs() }, 1000) })
onUnmounted(() => clearInterval(timer))

const currentTime = computed(() => now.value.format('HH:mm:ss'))
const currentDate = computed(() => now.value.format('dd, D MMM'))

// ---- Заголовок страницы ----
const pageTitles: Record<string, string> = {
  '/dashboard': 'Дашборд',
  '/customers': 'Клиенты',
  '/tasks':     'Задачи',
  '/orders':    'Заказы',
  '/admin':     'Панель администратора',
  '/admin/users': 'Пользователи',
  '/admin/roles': 'Роли и права',
}

const pageTitle = computed(() =>
  pageTitles[route.path] ?? route.meta?.title as string ?? 'CRM Cloud'
)

// ---- Аватар ----
const avatarLabel = computed(() => {
  const u = auth.user
  if (!u) return '?'
  return `${u.firstName[0]}${u.lastName[0]}`.toUpperCase()
})

// ---- Меню пользователя ----
function toggle(event: MouseEvent) {
  menu.value.toggle(event)
}

const userMenuItems = computed(() => [
  {
    label: auth.user?.fullName,
    items: [
      { label: 'Профиль',   icon: 'pi pi-user',       command: () => {} },
      { label: 'Настройки', icon: 'pi pi-cog',         command: () => router.push('/admin') },
      { separator: true },
      { label: 'Выйти',     icon: 'pi pi-sign-out',    command: () => auth.logout(), class: 'text-danger' }
    ]
  }
])
</script>

<style scoped>
.topbar {
  height: var(--topbar-height);
  background: var(--topbar-bg);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-subtle);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  position: sticky;
  top: 0;
  z-index: 50;
}

.page-title {
  font-size: 1.0625rem;
  font-weight: 600;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.topbar__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ---- Часы ---- */
.topbar__clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-right: 8px;
}

.clock__time {
  font-size: 0.9375rem;
  font-weight: 500;
  color: var(--text-primary);
  line-height: 1.2;
  letter-spacing: 0.03em;
}

.clock__date {
  font-size: 0.6875rem;
  color: var(--text-muted);
  text-transform: capitalize;
}

/* ---- Кнопки ---- */
.topbar__theme-btn,
.topbar__icon-btn {
  color: var(--text-secondary) !important;
  transition: color var(--transition-fast) !important;
}

.topbar__theme-btn:hover,
.topbar__icon-btn:hover {
  color: var(--text-primary) !important;
}

/* ---- Пользователь ---- */
.topbar__user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
  border: 1px solid transparent;
}

.topbar__user:hover {
  background: var(--bg-hover);
  border-color: var(--border-subtle);
}

.topbar__user-name {
  font-size: 0.875rem;
  font-weight: 500;
  color: var(--text-primary);
}

.topbar__chevron {
  font-size: 0.625rem;
  color: var(--text-muted);
}
</style>
