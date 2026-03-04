<template>
  <div class="verify">
    <div v-if="loading" class="verify__state">
      <ProgressSpinner />
      <p>Подтверждение email...</p>
    </div>
    <div v-else-if="success" class="verify__state verify__state--success animate-fade-in">
      <div class="state-icon state-icon--success"><i class="pi pi-check-circle" /></div>
      <h2>Email подтверждён!</h2>
      <p>Ваш аккаунт активирован. Теперь вы можете войти в систему.</p>
      <RouterLink to="/auth/login">
        <Button label="Войти в систему" fluid style="margin-top:16px" />
      </RouterLink>
    </div>
    <div v-else class="verify__state verify__state--error animate-fade-in">
      <div class="state-icon state-icon--error"><i class="pi pi-times-circle" /></div>
      <h2>Ссылка недействительна</h2>
      <p>Токен истёк или уже был использован. Попробуйте зарегистрироваться снова.</p>
      <RouterLink to="/auth/register">
        <Button label="К регистрации" outlined fluid style="margin-top:16px" />
      </RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import Button from 'primevue/button'
import ProgressSpinner from 'primevue/progressspinner'
import { authApi } from '@/api/auth'

const route = useRoute()
const loading = ref(true)
const success = ref(false)

onMounted(async () => {
  try {
    await authApi.verifyEmail(route.query.token as string)
    success.value = true
  } catch {
    success.value = false
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.verify { text-align: center; padding: 20px 0; }
.verify__state { display: flex; flex-direction: column; align-items: center; gap: 14px; }
.state-icon { width: 72px; height: 72px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.state-icon--success { background: rgba(34,197,94,0.12); border: 1px solid rgba(34,197,94,0.2); }
.state-icon--success .pi { font-size: 2rem; color: #22c55e; }
.state-icon--error { background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); }
.state-icon--error .pi { font-size: 2rem; color: #ef4444; }
h2 { font-size: 1.375rem; font-weight: 700; color: var(--text-primary); }
p { color: var(--text-secondary); line-height: 1.6; }
</style>
