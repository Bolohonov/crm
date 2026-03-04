import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { usePreferredDark } from '@vueuse/core'

export type Theme = 'dark' | 'light'

export const useUiStore = defineStore('ui', () => {
  const prefersDark = usePreferredDark()
  const savedTheme = localStorage.getItem('theme') as Theme | null
  const theme = ref<Theme>(savedTheme ?? (prefersDark.value ? 'dark' : 'light'))
  const sidebarCollapsed = ref(false)

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  // Применяем тему к <html> и сохраняем
  watch(theme, val => {
    document.documentElement.setAttribute('data-theme', val)
    document.documentElement.classList.toggle('dark', val === 'dark')
    localStorage.setItem('theme', val)
  }, { immediate: true })

  return { theme, sidebarCollapsed, toggleTheme, toggleSidebar }
})
