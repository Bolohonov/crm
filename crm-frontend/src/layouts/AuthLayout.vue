<template>
  <div class="auth-shell">
    <!-- Фоновые декоративные элементы -->
    <div class="auth-bg">
      <div class="auth-bg__orb auth-bg__orb--1" />
      <div class="auth-bg__orb auth-bg__orb--2" />
      <div class="auth-bg__grid" />
    </div>

    <!-- Левая панель — брендинг -->
    <aside class="auth-brand">
      <div class="auth-brand__logo">
        <span class="auth-brand__icon">
          <i class="pi pi-cloud" />
        </span>
        <span class="auth-brand__name">CRM <em>Cloud</em></span>
      </div>
      <div class="auth-brand__tagline">
        <h2>Управляйте клиентами<br/>на новом уровне</h2>
        <p>Единая платформа для работы с клиентами, задачами и заказами</p>
      </div>
      <ul class="auth-brand__features">
        <li v-for="f in features" :key="f.text">
          <span class="feature-icon"><i :class="f.icon" /></span>
          {{ f.text }}
        </li>
      </ul>
    </aside>

    <!-- Правая панель — форма -->
    <main class="auth-content">
      <div class="auth-card animate-fade-in">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'

const features = [
  { icon: 'pi pi-users',    text: 'Управление клиентами и портфелями' },
  { icon: 'pi pi-calendar', text: 'Календарь задач и планирование' },
  { icon: 'pi pi-box',      text: 'Заказы и продукты в одном месте' },
  { icon: 'pi pi-shield',   text: 'Гибкие роли и права доступа' },
]
</script>

<style scoped>
.auth-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  position: relative;
  overflow: hidden;
}

/* ---- Фон ---- */
.auth-bg {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.auth-bg__orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.3;
}

.auth-bg__orb--1 {
  width: 500px; height: 500px;
  background: radial-gradient(circle, #1d4ed8, transparent);
  top: -100px; left: -100px;
}

.auth-bg__orb--2 {
  width: 400px; height: 400px;
  background: radial-gradient(circle, #0f766e, transparent);
  bottom: -80px; right: 30%;
}

.auth-bg__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(59,130,246,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(59,130,246,0.03) 1px, transparent 1px);
  background-size: 40px 40px;
}

/* ---- Бренд ---- */
.auth-brand {
  position: relative;
  z-index: 1;
  padding: 48px;
  display: flex;
  flex-direction: column;
  gap: 48px;
  background: rgba(8,12,24,0.6);
  backdrop-filter: blur(12px);
  border-right: 1px solid var(--border-subtle);
}

.auth-brand__logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.auth-brand__icon {
  width: 40px; height: 40px;
  background: var(--accent-500);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  color: white;
  box-shadow: 0 0 20px rgba(59,130,246,0.4);
}

.auth-brand__name {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.auth-brand__name em {
  font-style: normal;
  color: var(--accent-400);
}

.auth-brand__tagline h2 {
  font-size: 2rem;
  font-weight: 600;
  line-height: 1.25;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-bottom: 16px;
}

.auth-brand__tagline p {
  color: var(--text-secondary);
  font-size: 1rem;
  line-height: 1.6;
}

.auth-brand__features {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: auto;
}

.auth-brand__features li {
  display: flex;
  align-items: center;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 0.9375rem;
}

.feature-icon {
  width: 32px; height: 32px;
  background: rgba(59,130,246,0.12);
  border: 1px solid rgba(59,130,246,0.2);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--accent-400);
  font-size: 14px;
  flex-shrink: 0;
}

/* ---- Контент ---- */
.auth-content {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
  background: var(--bg-base);
}

.auth-card {
  width: 100%;
  max-width: 420px;
}

/* ---- Светлая тема ---- */
[data-theme="light"] .auth-brand {
  background: rgba(240,244,251,0.8);
  border-right-color: var(--border-default);
}

[data-theme="light"] .auth-content {
  background: var(--bg-surface);
}

[data-theme="light"] .auth-bg__orb--1 {
  background: radial-gradient(circle, #93c5fd, transparent);
  opacity: 0.5;
}

/* ---- Адаптивность ---- */
@media (max-width: 768px) {
  .auth-shell { grid-template-columns: 1fr; }
  .auth-brand { display: none; }
  .auth-content { padding: 32px 20px; }
}
</style>
