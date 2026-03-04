<template>
  <RouterLink :to="item.to" class="nav-item" :class="{ 'nav-item--active': isActive }">
    <span class="nav-item__icon">
      <i :class="item.icon" />
    </span>
    <Transition name="label">
      <span v-if="!collapsed" class="nav-item__label">{{ item.label }}</span>
    </Transition>
    <!-- Индикатор активного пункта -->
    <span v-if="isActive" class="nav-item__indicator" />
  </RouterLink>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'

interface NavItem { label: string; to: string; icon: string }

const props = defineProps<{ item: NavItem; collapsed: boolean }>()
const route = useRoute()
const isActive = computed(() => route.path.startsWith(props.item.to) && (props.item.to !== '/' || route.path === '/'))
</script>

<style scoped>
.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 8px;
  border-radius: var(--radius-md);
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 0.9rem;
  font-weight: 450;
  transition: background var(--transition-fast), color var(--transition-fast);
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.nav-item--active {
  background: var(--sidebar-item-active-bg);
  color: var(--accent-400);
  font-weight: 500;
}

.nav-item__icon {
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  flex-shrink: 0;
}

.nav-item__label { flex: 1; }

.nav-item__indicator {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: var(--sidebar-item-active-border);
  border-radius: 2px 0 0 2px;
}

.label-enter-active, .label-leave-active { transition: opacity 120ms; }
.label-enter-from, .label-leave-to { opacity: 0; }
</style>
