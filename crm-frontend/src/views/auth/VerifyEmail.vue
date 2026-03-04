<template>
  <div class="auth-card" style="text-align:center">
    <div class="auth-logo" style="justify-content:center">
      <div class="logo-icon"><i class="pi pi-cloud" /></div>
      <span>CRM <strong>Cloud</strong></span>
    </div>
    <div v-if="loading" class="state">
      <ProgressSpinner style="width:48px;height:48px" />
      <p>Проверяем токен...</p>
    </div>
    <div v-else-if="success" class="state">
      <div class="icon-circle success"><i class="pi pi-check" /></div>
      <h2>Email подтверждён!</h2>
      <p>Ваш аккаунт активирован. Можете войти в систему.</p>
      <Button label="Войти" @click="router.push('/auth/login')" style="margin-top:16px" />
    </div>
    <div v-else class="state">
      <div class="icon-circle error"><i class="pi pi-times" /></div>
      <h2>Недействительная ссылка</h2>
      <p>{{ errorMsg }}</p>
      <Button label="На главную" outlined @click="router.push('/auth/login')" style="margin-top:16px" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const success = ref(false)
const errorMsg = ref('')

onMounted(async () => {
  const token = route.query.token as string
  if (!token) { errorMsg.value = 'Токен не найден.'; loading.value = false; return }
  try {
    await authApi.verifyEmail(token)
    success.value = true
  } catch (e: any) {
    errorMsg.value = e.response?.data?.error?.message ?? 'Срок действия ссылки истёк.'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.state { display:flex; flex-direction:column; align-items:center; gap:12px; padding:24px 0; }
.icon-circle { width:64px; height:64px; border-radius:50%; display:flex; align-items:center; justify-content:center; font-size:28px; }
.icon-circle.success { background:#dcfce7; color:#16a34a; }
.icon-circle.error { background:#fee2e2; color:#dc2626; }
h2 { font-size:18px; font-weight:600; }
p { color:var(--color-text-muted); font-size:14px; }
</style>
