<template>
  <div class="login">
    <div class="login__header">
      <h1>Добро пожаловать</h1>
      <p>Войдите в свой аккаунт CRM Cloud</p>
    </div>

    <form class="login__form" @submit.prevent="handleSubmit">
      <div v-if="error" class="form-error">
        <i class="pi pi-exclamation-circle" />
        <span>{{ error }}</span>
      </div>

      <div class="field">
        <label>Email</label>
        <InputText v-model="form.email" type="email" placeholder="you@company.com"
          :class="{ 'p-invalid': v$.email.$error }" fluid />
        <small v-if="v$.email.$error" class="field-error">{{ v$.email.$errors[0].$message }}</small>
      </div>

      <div class="field">
        <div class="field-label-row">
          <label>Пароль</label>
          <a class="forgot-link">Забыли пароль?</a>
        </div>
        <Password v-model="form.password" placeholder="••••••••" :feedback="false"
          toggle-mask :class="{ 'p-invalid': v$.password.$error }" fluid />
        <small v-if="v$.password.$error" class="field-error">{{ v$.password.$errors[0].$message }}</small>
      </div>

      <Button type="submit" label="Войти" :loading="auth.loading" fluid class="login__submit" />
    </form>

    <div class="login__divider"><span>или</span></div>

    <div class="oauth-buttons">
      <Button class="oauth-btn" outlined fluid @click="oauthLogin('google')">
        <svg width="16" height="16" viewBox="0 0 24 24">
          <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
          <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
          <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z"/>
          <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
        </svg>
        Войти через Google
      </Button>
    </div>

    <p class="login__register">
      Нет аккаунта? <RouterLink to="/auth/register">Зарегистрироваться</RouterLink>
    </p>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useVuelidate } from '@vuelidate/core'
import { required, email, minLength, helpers } from '@vuelidate/validators'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const error = ref('')
const form = reactive({ email: '', password: '' })

const rules = {
  email:    { required: helpers.withMessage('Email обязателен', required), email: helpers.withMessage('Неверный формат', email) },
  password: { required: helpers.withMessage('Пароль обязателен', required), minLength: helpers.withMessage('Минимум 8 символов', minLength(8)) }
}
const v$ = useVuelidate(rules, form)

async function handleSubmit() {
  error.value = ''
  if (!(await v$.value.$validate())) return
  try {
    await auth.login({ email: form.email, password: form.password })
    router.push('/dashboard')
  } catch (e: any) {
    error.value = e?.response?.data?.error?.message ?? 'Неверный email или пароль'
  }
}

function oauthLogin(provider: string) {
  window.location.href = `/api/v1/auth/oauth2/authorize/${provider}`
}
</script>

<style scoped>
.login__header { margin-bottom: 28px; }
.login__header h1 { font-size: 1.625rem; font-weight: 700; color: var(--text-primary); letter-spacing: -0.02em; margin-bottom: 6px; }
.login__header p { color: var(--text-secondary); font-size: 0.9375rem; }
.login__form { display: flex; flex-direction: column; gap: 18px; }
.form-error { display: flex; align-items: center; gap: 8px; padding: 12px 14px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: var(--radius-md); color: #fca5a5; font-size: 0.875rem; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field label { font-size: 0.875rem; font-weight: 500; color: var(--text-secondary); }
.field-label-row { display: flex; justify-content: space-between; align-items: center; }
.forgot-link { font-size: 0.8125rem; color: var(--accent-400); cursor: pointer; }
.field-error { color: #fca5a5; font-size: 0.8125rem; }
.login__submit { margin-top: 4px; height: 42px; }
.login__divider { display: flex; align-items: center; gap: 12px; margin: 20px 0; color: var(--text-muted); font-size: 0.8125rem; }
.login__divider::before, .login__divider::after { content: ''; flex: 1; height: 1px; background: var(--border-subtle); }
.oauth-buttons { display: flex; flex-direction: column; gap: 10px; }
.oauth-btn { height: 40px; display: flex !important; align-items: center; gap: 10px; border-color: var(--border-default) !important; color: var(--text-secondary) !important; background: var(--bg-elevated) !important; font-size: 0.875rem !important; }
.login__register { text-align: center; margin-top: 20px; color: var(--text-muted); font-size: 0.875rem; }
.login__register a { color: var(--accent-400); text-decoration: none; font-weight: 500; }
</style>
