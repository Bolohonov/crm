<template>
  <div class="tenant-view animate-fade-in">

    <div class="section-header">
      <div>
        <h2 class="section-title">Настройки организации</h2>
        <p class="section-sub">Профиль компании и подписка</p>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <ProgressSpinner style="width:36px;height:36px" />
    </div>

    <template v-else-if="profile">
      <!-- ── План и лимиты ─────────────────────────────────────────── -->
      <div class="plan-card">
        <div class="plan-card__left">
          <div class="plan-badge" :class="`plan-badge--${profile.plan.toLowerCase()}`">
            <i class="pi pi-crown" /> {{ planLabel(profile.plan) }}
          </div>
          <p class="plan-card__schema">Схема: <code>{{ profile.schemaName }}</code></p>
        </div>
        <div class="plan-card__stats">
          <div class="plan-stat">
            <div class="plan-stat__value">{{ profile.currentUsers }}</div>
            <div class="plan-stat__label">из {{ profile.maxUsers === 2147483647 ? '∞' : profile.maxUsers }} пользователей</div>
          </div>
          <div class="plan-stat__bar">
            <div
              class="plan-stat__fill"
              :style="{ width: `${Math.min(100, (profile.currentUsers / (profile.maxUsers || 1)) * 100)}%` }"
              :class="{ 'plan-stat__fill--warn': profile.currentUsers / profile.maxUsers > .8 }"
            />
          </div>
        </div>
      </div>

      <!-- ── Реквизиты ─────────────────────────────────────────────── -->
      <div class="settings-card">
        <div class="settings-card__header">
          <h3>Реквизиты компании</h3>
          <Button icon="pi pi-pencil" :label="editing ? '' : 'Редактировать'" text size="small" @click="toggleEdit" />
        </div>

        <div class="settings-grid">
          <div class="field">
            <label>Название компании</label>
            <InputText v-if="editing" v-model="form.companyName" style="width:100%" />
            <div v-else class="field-value">{{ profile.companyName || '—' }}</div>
          </div>
          <div class="field">
            <label>Email для связи</label>
            <InputText v-if="editing" v-model="form.contactEmail" style="width:100%" type="email" />
            <div v-else class="field-value">{{ profile.contactEmail || '—' }}</div>
          </div>
          <div class="field">
            <label>Телефон</label>
            <InputText v-if="editing" v-model="form.contactPhone" style="width:100%" />
            <div v-else class="field-value">{{ profile.contactPhone || '—' }}</div>
          </div>
          <div class="field">
            <label>Сайт</label>
            <InputText v-if="editing" v-model="form.website" style="width:100%" />
            <div v-else class="field-value">{{ profile.website || '—' }}</div>
          </div>
          <div class="field">
            <label>Часовой пояс</label>
            <Select
              v-if="editing"
              v-model="form.timezone"
              :options="timezones"
              option-label="label"
              option-value="value"
              style="width:100%"
            />
            <div v-else class="field-value">{{ profile.timezone || 'Europe/Moscow' }}</div>
          </div>
          <div class="field">
            <label>Валюта</label>
            <Select
              v-if="editing"
              v-model="form.currency"
              :options="currencies"
              option-label="label"
              option-value="value"
              style="width:100%"
            />
            <div v-else class="field-value">{{ profile.currency || 'RUB' }}</div>
          </div>
        </div>

        <div class="settings-card__footer" v-if="editing">
          <Button label="Отмена" text @click="cancelEdit" />
          <Button label="Сохранить" :loading="saving" @click="saveSettings" />
        </div>
      </div>

      <!-- ── Опасная зона ──────────────────────────────────────────── -->
      <div class="danger-zone">
        <h3 class="danger-zone__title"><i class="pi pi-exclamation-triangle" /> Опасная зона</h3>
        <div class="danger-zone__item">
          <div>
            <div class="danger-zone__item-title">Удалить тенант</div>
            <div class="danger-zone__item-desc">Все данные будут безвозвратно удалены. Это действие нельзя отменить.</div>
          </div>
          <Button label="Удалить тенант" severity="danger" outlined disabled />
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useToast } from 'primevue/usetoast'
import api from '@/api/client'

const toast = useToast()

const profile  = ref<any>(null)
const loading  = ref(false)
const editing  = ref(false)
const saving   = ref(false)

const form = reactive({
  companyName: '', contactEmail: '', contactPhone: '',
  website: '', timezone: 'Europe/Moscow', currency: 'RUB',
})

const timezones = [
  { label: 'Москва (UTC+3)',        value: 'Europe/Moscow' },
  { label: 'Екатеринбург (UTC+5)',  value: 'Asia/Yekaterinburg' },
  { label: 'Новосибирск (UTC+7)',   value: 'Asia/Novosibirsk' },
  { label: 'Владивосток (UTC+10)',  value: 'Asia/Vladivostok' },
  { label: 'UTC',                   value: 'UTC' },
]

const currencies = [
  { label: '₽ Рубль (RUB)',   value: 'RUB' },
  { label: '$ Доллар (USD)',  value: 'USD' },
  { label: '€ Евро (EUR)',    value: 'EUR' },
  { label: '₸ Тенге (KZT)',   value: 'KZT' },
]

async function loadProfile() {
  loading.value = true
  try {
    const { data: res } = await api.get('/tenant')
    profile.value = res.data
    Object.assign(form, {
      companyName: res.data.companyName ?? '',
      contactEmail: res.data.contactEmail ?? '',
      contactPhone: res.data.contactPhone ?? '',
      website: res.data.website ?? '',
      timezone: res.data.timezone ?? 'Europe/Moscow',
      currency: res.data.currency ?? 'RUB',
    })
  } catch {
    toast.add({ severity: 'error', summary: 'Не удалось загрузить настройки', life: 3000 })
  } finally {
    loading.value = false
  }
}

function toggleEdit() { editing.value = true }
function cancelEdit() { editing.value = false; Object.assign(form, profile.value) }

async function saveSettings() {
  saving.value = true
  try {
    await api.put('/tenant/settings', { ...form })
    toast.add({ severity: 'success', summary: 'Настройки сохранены', life: 2500 })
    editing.value = false
    await loadProfile()
  } catch {
    toast.add({ severity: 'error', summary: 'Ошибка сохранения', life: 3000 })
  } finally {
    saving.value = false
  }
}

function planLabel(plan: string): string {
  return { FREE: 'Бесплатный', STANDARD: 'Стандарт', ENTERPRISE: 'Enterprise' }[plan] ?? plan
}

onMounted(loadProfile)
</script>

<style scoped>
.tenant-view { display: flex; flex-direction: column; gap: 20px; }
.section-header { display: flex; justify-content: space-between; align-items: flex-start; }
.section-title  { font-size: 1.125rem; font-weight: 700; margin: 0 0 4px; }
.section-sub    { font-size: .875rem; color: var(--color-text-muted); margin: 0; }

.plan-card { background: linear-gradient(135deg, #1d4ed8 0%, #4f46e5 100%); border-radius: 14px; padding: 24px; display: flex; justify-content: space-between; align-items: center; gap: 20px; color: #fff; }
.plan-badge { display: inline-flex; align-items: center; gap: 8px; padding: 6px 16px; border-radius: 20px; background: rgba(255,255,255,.2); font-weight: 700; font-size: .9rem; margin-bottom: 10px; }
.plan-badge--enterprise { background: rgba(251,191,36,.3); }
.plan-card__schema { font-size: .8rem; opacity: .7; margin: 0; }
.plan-card__schema code { font-family: 'JetBrains Mono', monospace; background: rgba(255,255,255,.1); padding: 2px 6px; border-radius: 4px; }
.plan-card__stats { text-align: right; min-width: 200px; }
.plan-stat__value { font-size: 2rem; font-weight: 800; line-height: 1; }
.plan-stat__label { font-size: .8rem; opacity: .7; margin: 4px 0 10px; }
.plan-stat__bar { height: 6px; background: rgba(255,255,255,.2); border-radius: 3px; overflow: hidden; }
.plan-stat__fill { height: 100%; background: #fff; border-radius: 3px; transition: width .4s; }
.plan-stat__fill--warn { background: #fbbf24; }

.settings-card { background: var(--color-bg-card); border: 1px solid var(--color-border); border-radius: 12px; overflow: hidden; }
.settings-card__header { display: flex; justify-content: space-between; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--color-border); }
.settings-card__header h3 { font-size: 1rem; font-weight: 600; margin: 0; }
.settings-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; padding: 20px; }
.field { display: flex; flex-direction: column; gap: 6px; }
.field label { font-size: .78rem; font-weight: 600; color: var(--color-text-muted); text-transform: uppercase; letter-spacing: .04em; }
.field-value { font-size: .9rem; color: var(--color-text); padding: 6px 0; border-bottom: 1px solid transparent; }
.settings-card__footer { display: flex; justify-content: flex-end; gap: 8px; padding: 14px 20px; border-top: 1px solid var(--color-border); background: var(--color-bg-hover); }

.danger-zone { border: 1px solid color-mix(in srgb, var(--color-danger) 40%, transparent); border-radius: 12px; overflow: hidden; }
.danger-zone__title { padding: 14px 20px; background: color-mix(in srgb, var(--color-danger) 8%, transparent); font-size: .9rem; font-weight: 600; color: var(--color-danger); display: flex; align-items: center; gap: 8px; margin: 0; }
.danger-zone__item { display: flex; justify-content: space-between; align-items: center; gap: 20px; padding: 16px 20px; }
.danger-zone__item-title { font-weight: 600; margin-bottom: 4px; }
.danger-zone__item-desc  { font-size: .8rem; color: var(--color-text-muted); }

.loading-state { display: flex; justify-content: center; padding: 60px; }

.animate-fade-in { animation: fadeIn .25s ease; }
@keyframes fadeIn { from { opacity:0; transform:translateY(6px) } to { opacity:1; transform:none } }
</style>
