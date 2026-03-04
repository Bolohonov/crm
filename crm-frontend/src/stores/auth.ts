import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import type { MeResponse, LoginRequest, RegisterRequest } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<MeResponse | null>(null)
  const loading = ref(false)
  const initialized = ref(false)

  const isAuthenticated = computed(() => !!user.value)
  const isAdmin = computed(() => user.value?.userType === 'ADMIN')
  const permissions = computed(() => new Set(user.value?.permissions ?? []))
  const enabledModules = computed(() => new Set(user.value?.enabledModules ?? []))

  function can(permission: string): boolean {
    if (isAdmin.value) return true
    return permissions.value.has(permission)
  }

  function canAny(...perms: string[]): boolean {
    if (isAdmin.value) return true
    return perms.some(p => permissions.value.has(p))
  }

  function hasModule(moduleCode: string): boolean {
    return enabledModules.value.has(moduleCode)
  }

  async function login(credentials: LoginRequest) {
    loading.value = true
    try {
      const { data: res } = await authApi.login(credentials)
      if (!res.data) throw new Error(res.error?.message ?? 'Login failed')
      localStorage.setItem('accessToken', res.data.accessToken)
      localStorage.setItem('refreshToken', res.data.refreshToken)
      await fetchMe()
    } finally {
      loading.value = false
    }
  }

  async function register(payload: RegisterRequest) {
    loading.value = true
    try {
      const { data: res } = await authApi.register(payload)
      if (!res.data) throw new Error(res.error?.message ?? 'Registration failed')
      return res.data
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try { await authApi.logout() } finally {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
      user.value = null
    }
  }

  async function fetchMe() {
    const { data: res } = await authApi.me()
    if (res.data) user.value = res.data
  }

  async function init() {
    if (initialized.value) return
    initialized.value = true
    if (!localStorage.getItem('accessToken')) return
    try { await fetchMe() } catch {
      localStorage.removeItem('accessToken')
      localStorage.removeItem('refreshToken')
    }
  }

  return {
    user, loading, initialized,
    isAuthenticated, isAdmin, permissions, enabledModules,
    can, canAny, hasModule,
    login, register, logout, fetchMe, init
  }
})
