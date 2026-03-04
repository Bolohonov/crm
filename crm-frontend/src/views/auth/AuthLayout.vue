<template>
  <div class="auth-shell">
    <div class="auth-bg">
      <!-- Декоративные круги фона -->
      <div class="bg-circle c1" />
      <div class="bg-circle c2" />
      <div class="bg-circle c3" />
    </div>

    <div class="auth-content">
      <RouterView />
    </div>

    <!-- Переключатель темы -->
    <button class="theme-toggle" @click="ui.toggleTheme()">
      <i :class="['pi', ui.isDark ? 'pi-sun' : 'pi-moon']" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { useUiStore } from '@/stores/ui'
const ui = useUiStore()
</script>

<style>
/* Глобальные стили для карточки авторизации — используются во всех auth-страницах */
.auth-card {
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  border-radius: 16px;
  padding: 40px;
  width: 100%;
  max-width: 420px;
  box-shadow: var(--shadow-modal);
}

.auth-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
  font-size: 18px;
  color: var(--color-text);
}

.auth-logo .logo-icon {
  width: 36px;
  height: 36px;
  background: var(--color-primary);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.auth-logo strong { color: var(--color-primary); }

.auth-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 6px;
}

.auth-subtitle {
  font-size: 14px;
  color: var(--color-text-muted);
}

.auth-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: var(--color-text-muted);
}

.auth-footer a {
  color: var(--color-primary);
  text-decoration: none;
  font-weight: 500;
  margin-left: 4px;
}
</style>

<style scoped>
.auth-shell {
  min-height: 100vh;
  background: var(--color-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 24px;
}

/* Декоративный фон */
.auth-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.bg-circle {
  position: absolute;
  border-radius: 50%;
  opacity: 0.06;
  background: var(--color-primary);
}

.c1 { width: 600px; height: 600px; top: -200px; right: -100px; }
.c2 { width: 400px; height: 400px; bottom: -150px; left: -100px; opacity: 0.04; }
.c3 { width: 200px; height: 200px; top: 30%; left: 10%; opacity: 0.03; }

.auth-content { position: relative; z-index: 1; width: 100%; display: flex; justify-content: center; }

.theme-toggle {
  position: fixed;
  top: 16px;
  right: 16px;
  width: 36px;
  height: 36px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-card);
  border-radius: 8px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  font-size: 14px;
  transition: background var(--transition);
}

.theme-toggle:hover { background: var(--color-bg-hover); color: var(--color-text); }
</style>
