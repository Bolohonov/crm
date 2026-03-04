<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': ui.sidebarCollapsed }">
    <AppSidebar />
    <div class="app-main">
      <AppTopbar />
      <main class="app-content">
        <RouterView v-slot="{ Component }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useUiStore } from '@/stores/ui'
import AppSidebar from '@/components/layout/AppSidebar.vue'
import AppTopbar from '@/components/layout/AppTopbar.vue'

const route = useRoute()
const ui = useUiStore()
</script>

<style scoped>
.app-shell {
  display: grid;
  grid-template-columns: var(--sidebar-width) 1fr;
  min-height: 100vh;
  transition: grid-template-columns var(--transition-slow);
}

.app-shell.sidebar-collapsed {
  grid-template-columns: var(--sidebar-width-collapsed) 1fr;
}

.app-main {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  overflow: hidden;
}

.app-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: var(--bg-base);
}

/* Анимация смены страниц */
.page-enter-active,
.page-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.page-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 768px) {
  .app-shell {
    grid-template-columns: 1fr;
  }
}
</style>
