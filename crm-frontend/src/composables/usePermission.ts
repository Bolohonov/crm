import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

export function usePermission() {
  const auth = useAuthStore()
  return {
    can:       (p: string) => auth.can(p),
    canAny:    (...pp: string[]) => auth.canAny(...pp),
    isAdmin:   computed(() => auth.isAdmin),
    hasModule: (m: string) => auth.hasModule(m),
  }
}
