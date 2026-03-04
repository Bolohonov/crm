<template>
  <div class="dashboard">
    <div class="welcome">
      <h2>Добро пожаловать, {{ auth.user?.firstName }}! 👋</h2>
      <p>{{ formattedDate }}</p>
    </div>
    <div class="stats-grid">
      <div class="stat-card card" v-for="stat in stats" :key="stat.label">
        <div class="stat-icon" :style="{ background: stat.color + '18', color: stat.color }">
          <i :class="['pi', stat.icon]" />
        </div>
        <div class="stat-info">
          <div class="stat-value">{{ stat.value }}</div>
          <div class="stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import dayjs from 'dayjs'
import 'dayjs/locale/ru'
dayjs.locale('ru')

const auth = useAuthStore()

const formattedDate = computed(() =>
  dayjs().format('dddd, D MMMM YYYY')
)

const stats = [
  { label: 'Клиентов', value: '—', icon: 'pi-users',         color: '#3b82f6' },
  { label: 'Задач',    value: '—', icon: 'pi-calendar',      color: '#8b5cf6' },
  { label: 'Заказов',  value: '—', icon: 'pi-shopping-cart', color: '#f59e0b' },
  { label: 'Команда',  value: '—', icon: 'pi-team',          color: '#22c55e' }
]
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 24px; }
.welcome h2 { font-size: 22px; font-weight: 700; color: var(--color-text); }
.welcome p  { font-size: 14px; color: var(--color-text-muted); margin-top: 4px;
              text-transform: capitalize; }
.stats-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.stat-card  { padding: 20px; display: flex; align-items: center; gap: 16px; }
.stat-icon  { width: 48px; height: 48px; border-radius: 12px;
              display: flex; align-items: center; justify-content: center; font-size: 20px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--color-text); }
.stat-label { font-size: 13px; color: var(--color-text-muted); margin-top: 2px; }
</style>
