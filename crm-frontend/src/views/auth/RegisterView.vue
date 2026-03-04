<template>
  <div class="register">
    <div class="register__header">
      <h1>Создать аккаунт</h1>
      <p>Зарегистрируйтесь для доступа к CRM Cloud</p>
    </div>

    <!-- Шаг 1: Тип пользователя -->
    <div v-if="step === 1" class="register__step animate-fade-in">
      <p class="step-hint">Выберите тип регистрации</p>
      <div class="type-cards">
        <div class="type-card" :class="{ active: form.userType === 'ADMIN' }"
             @click="form.userType = 'ADMIN'">
          <i class="pi pi-building type-card__icon" />
          <strong>Администратор</strong>
          <span>Создаю новую компанию в CRM</span>
        </div>
        <div class="type-card" :class="{ active: form.userType === 'REGULAR' }"
             @click="form.userType = 'REGULAR'">
          <i class="pi pi-user type-card__icon" />
          <strong>Сотрудник</strong>
          <span>Присоединяюсь к существующей компании</span>
        </div>
      </div>

      <div v-if="form.userType === 'REGULAR'" class="field" style="margin-top: 16px">
        <label>Email администратора компании</label>
        <InputText v-model="form.adminEmail" type="email"
          placeholder="admin@company.com" fluid />
        <small class="field-hint">Администратор получит уведомление и одобрит ваш доступ</small>
      </div>

      <Button label="Далее" fluid class="mt-4" @click="nextStep" />
      <p class="register__login">Уже есть аккаунт? <RouterLink to="/auth/login">Войти</RouterLink></p>
    </div>

    <!-- Шаг 2: Личные данные -->
    <div v-else-if="step === 2" class="register__step animate-fade-in">
      <button class="back-btn" @click="step = 1">
        <i class="pi pi-arrow-left" /> Назад
      </button>

      <form class="register__form" @submit.prevent="handleSubmit">
        <div v-if="error" class="form-error">
          <i class="pi pi-exclamation-circle" />
          <span>{{ error }}</span>
        </div>

        <div class="field-row">
          <div class="field">
            <label>Фамилия *</label>
            <InputText v-model="form.lastName" placeholder="Иванов"
              :class="{ 'p-invalid': v$.lastName.$error }" fluid />
            <small v-if="v$.lastName.$error" class="field-error">{{ v$.lastName.$errors[0].$message }}</small>
          </div>
          <div class="field">
            <label>Имя *</label>
            <InputText v-model="form.firstName" placeholder="Иван"
              :class="{ 'p-invalid': v$.firstName.$error }" fluid />
            <small v-if="v$.firstName.$error" class="field-error">{{ v$.firstName.$errors[0].$message }}</small>
          </div>
        </div>

        <div class="field">
          <label>Отчество</label>
          <InputText v-model="form.middleName" placeholder="Иванович" fluid />
        </div>

        <div class="field">
          <label>Email *</label>
          <InputText v-model="form.email" type="email" placeholder="ivan@company.com"
            :class="{ 'p-invalid': v$.email.$error }" fluid />
          <small v-if="v$.email.$error" class="field-error">{{ v$.email.$errors[0].$message }}</small>
        </div>

        <div class="field">
          <label>Телефон *</label>
          <InputText v-model="form.phone" placeholder="+79001234567"
            :class="{ 'p-invalid': v$.phone.$error }" fluid />
          <small v-if="v$.phone.$error" class="field-error">{{ v$.phone.$errors[0].$message }}</small>
        </div>

        <div class="field">
          <label>Пароль *</label>
          <Password v-model="form.password" placeholder="Минимум 8 символов"
            toggle-mask :class="{ 'p-invalid': v$.password.$error }" fluid>
            <template #footer>
              <div class="password-hints">
                <span :class="{ met: form.password.length >= 8 }">• Минимум 8 символов</span>
              </div>
            </template>
          </Password>
          <small v-if="v$.password.$error" class="field-error">{{ v$.password.$errors[0].$message }}</small>
        </div>

        <Button type="submit" label="Создать аккаунт" :loading="auth.loading" fluid class="register__submit" />
      </form>
    </div>

    <!-- Шаг 3: Успех -->
    <div v-else class="register__success animate-fade-in">
      <div class="success-icon">
        <i class="pi pi-check-circle" />
      </div>
      <h2>Аккаунт создан!</h2>
      <p v-if="form.userType === 'ADMIN'">
        На <strong>{{ form.email }}</strong> отправлено письмо с ссылкой для подтверждения.
        Проверьте папку «Входящие».
      </p>
      <p v-else>
        Запрос отправлен администратору. После одобрения вы получите уведомление на {{ form.email }}.
      </p>
      <RouterLink to="/auth/login">
        <Button label="Перейти ко входу" outlined fluid class="mt-4" />
      </RouterLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { useVuelidate } from '@vuelidate/core'
import { required, email, minLength, helpers } from '@vuelidate/validators'
import InputText from 'primevue/inputtext'
import Password from 'primevue/password'
import Button from 'primevue/button'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const step = ref(1)
const error = ref('')

const form = reactive({
  userType: 'ADMIN' as 'ADMIN' | 'REGULAR',
  adminEmail: '',
  firstName: '', lastName: '', middleName: '',
  email: '', phone: '', password: ''
})

const rules = {
  firstName: { required: helpers.withMessage('Обязательно', required) },
  lastName:  { required: helpers.withMessage('Обязательно', required) },
  email:     { required: helpers.withMessage('Email обязателен', required), email: helpers.withMessage('Неверный формат', email) },
  phone:     { required: helpers.withMessage('Телефон обязателен', required), pattern: helpers.withMessage('Формат: +7XXXXXXXXXX', helpers.regex(/^\+[1-9]\d{6,14}$/)) },
  password:  { required: helpers.withMessage('Пароль обязателен', required), minLength: helpers.withMessage('Минимум 8 символов', minLength(8)) }
}
const v$ = useVuelidate(rules, form)

function nextStep() {
  if (form.userType === 'REGULAR' && !form.adminEmail) return
  step.value = 2
}

async function handleSubmit() {
  error.value = ''
  if (!(await v$.value.$validate())) return
  try {
    await auth.register({
      email: form.email, password: form.password,
      firstName: form.firstName, lastName: form.lastName,
      middleName: form.middleName || undefined,
      phone: form.phone, userType: form.userType,
      adminEmail: form.userType === 'REGULAR' ? form.adminEmail : undefined
    })
    step.value = 3
  } catch (e: any) {
    error.value = e?.response?.data?.error?.message ?? 'Ошибка при регистрации'
  }
}
</script>

<style scoped>
.register__header { margin-bottom: 24px; }
.register__header h1 { font-size: 1.625rem; font-weight: 700; color: var(--text-primary); letter-spacing: -0.02em; margin-bottom: 6px; }
.register__header p { color: var(--text-secondary); font-size: 0.9375rem; }

.step-hint { font-size: 0.875rem; color: var(--text-muted); margin-bottom: 14px; }

.type-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

.type-card {
  padding: 16px; border-radius: var(--radius-md);
  border: 1px solid var(--border-default); background: var(--bg-elevated);
  cursor: pointer; display: flex; flex-direction: column; gap: 4px;
  transition: all var(--transition-fast);
}
.type-card:hover { border-color: var(--accent-500); }
.type-card.active { border-color: var(--accent-500); background: rgba(59,130,246,0.08); }
.type-card__icon { font-size: 1.25rem; color: var(--accent-400); margin-bottom: 4px; }
.type-card strong { font-size: 0.9rem; color: var(--text-primary); }
.type-card span { font-size: 0.8rem; color: var(--text-muted); }

.back-btn { display: flex; align-items: center; gap: 6px; background: none; border: none; color: var(--text-secondary); cursor: pointer; font-size: 0.875rem; padding: 0; margin-bottom: 20px; }
.back-btn:hover { color: var(--text-primary); }

.register__form { display: flex; flex-direction: column; gap: 16px; }

.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field label { font-size: 0.875rem; font-weight: 500; color: var(--text-secondary); }
.field-hint { font-size: 0.8rem; color: var(--text-muted); }
.field-error { color: #fca5a5; font-size: 0.8125rem; }

.form-error { display: flex; align-items: center; gap: 8px; padding: 12px 14px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: var(--radius-md); color: #fca5a5; font-size: 0.875rem; }

.password-hints span { display: block; font-size: 0.8rem; color: var(--text-muted); }
.password-hints span.met { color: var(--success); }

.register__submit { height: 42px; margin-top: 4px; }

.register__login { text-align: center; margin-top: 20px; color: var(--text-muted); font-size: 0.875rem; }
.register__login a { color: var(--accent-400); text-decoration: none; font-weight: 500; }

.register__success { text-align: center; padding: 20px 0; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.success-icon { width: 64px; height: 64px; border-radius: 50%; background: rgba(34,197,94,0.12); border: 1px solid rgba(34,197,94,0.2); display: flex; align-items: center; justify-content: center; }
.success-icon .pi { font-size: 2rem; color: var(--success); }
.register__success h2 { font-size: 1.375rem; font-weight: 700; color: var(--text-primary); }
.register__success p { color: var(--text-secondary); font-size: 0.9375rem; line-height: 1.6; }

.mt-4 { margin-top: 16px; }
</style>
