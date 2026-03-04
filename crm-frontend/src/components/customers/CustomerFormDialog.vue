<template>
  <Dialog
    v-model:visible="visible"
    :header="isEdit ? 'Редактировать клиента' : 'Новый клиент'"
    modal
    :style="{ width: '580px' }"
    :draggable="false"
  >
    <!-- Шаг 1: выбор типа (только при создании) -->
    <div v-if="!isEdit && step === 1" class="step step--type animate-fade-in">
      <p class="step-hint">Выберите тип клиента</p>
      <div class="type-grid">
        <div
          v-for="t in typeOptions"
          :key="t.value"
          class="type-card"
          :class="{ active: form.customerType === t.value }"
          @click="form.customerType = t.value as any"
        >
          <i :class="t.icon" class="type-card__icon" />
          <strong>{{ t.label }}</strong>
          <span>{{ t.desc }}</span>
        </div>
      </div>
      <div class="dialog-footer">
        <Button label="Отмена" text @click="close" />
        <Button label="Далее →" @click="step = 2" :disabled="!form.customerType" />
      </div>
    </div>

    <!-- Шаг 2 / режим редактирования: форма -->
    <div v-else class="step animate-fade-in">
      <form @submit.prevent="handleSubmit">

        <!-- Персональные данные -->
        <fieldset v-if="needPersonal" class="form-section">
          <legend>Персональные данные</legend>
          <div class="field-row">
            <div class="field">
              <label>Фамилия *</label>
              <InputText v-model="form.personalData.lastName" placeholder="Иванов"
                :class="{ 'p-invalid': v$.personalData?.lastName?.$error }" fluid />
              <small v-if="v$.personalData?.lastName?.$error" class="field-error">
                {{ v$.personalData.lastName.$errors[0].$message }}
              </small>
            </div>
            <div class="field">
              <label>Имя *</label>
              <InputText v-model="form.personalData.firstName" placeholder="Иван"
                :class="{ 'p-invalid': v$.personalData?.firstName?.$error }" fluid />
              <small v-if="v$.personalData?.firstName?.$error" class="field-error">
                {{ v$.personalData.firstName.$errors[0].$message }}
              </small>
            </div>
          </div>
          <div class="field">
            <label>Отчество</label>
            <InputText v-model="form.personalData.middleName" placeholder="Иванович" fluid />
          </div>
          <div class="field-row">
            <div class="field">
              <label>Телефон *</label>
              <InputText v-model="form.personalData.phone" placeholder="+79001234567"
                :class="{ 'p-invalid': v$.personalData?.phone?.$error }" fluid />
              <small v-if="v$.personalData?.phone?.$error" class="field-error">
                {{ v$.personalData.phone.$errors[0].$message }}
              </small>
            </div>
            <div class="field">
              <label>Должность</label>
              <InputText v-model="form.personalData.position" placeholder="Менеджер" fluid />
            </div>
          </div>
          <div class="field">
            <label>Адрес</label>
            <InputText v-model="form.personalData.address" placeholder="г. Москва, ул. ..." fluid />
          </div>
        </fieldset>

        <!-- Данные организации -->
        <fieldset v-if="needOrg" class="form-section">
          <legend>Реквизиты организации</legend>
          <div class="field">
            <label>Название *</label>
            <InputText v-model="form.orgData.orgName" placeholder='ООО "Ромашка"'
              :class="{ 'p-invalid': v$.orgData?.orgName?.$error }" fluid />
            <small v-if="v$.orgData?.orgName?.$error" class="field-error">
              {{ v$.orgData.orgName.$errors[0].$message }}
            </small>
          </div>
          <div class="field-row">
            <div class="field">
              <label>ИНН *</label>
              <InputText v-model="form.orgData.inn" placeholder="1234567890" fluid
                :class="{ 'p-invalid': v$.orgData?.inn?.$error }" />
              <small v-if="v$.orgData?.inn?.$error" class="field-error">
                {{ v$.orgData.inn.$errors[0].$message }}
              </small>
            </div>
            <div class="field">
              <label>КПП</label>
              <InputText v-model="form.orgData.kpp" placeholder="123456789" fluid />
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <label>ОГРН *</label>
              <InputText v-model="form.orgData.ogrn" placeholder="1234567890123" fluid
                :class="{ 'p-invalid': v$.orgData?.ogrn?.$error }" />
              <small v-if="v$.orgData?.ogrn?.$error" class="field-error">
                {{ v$.orgData.ogrn.$errors[0].$message }}
              </small>
            </div>
            <div class="field">
              <label>Правовая форма</label>
              <Select v-model="form.orgData.legalFormId" :options="legalForms"
                option-label="name" option-value="id" placeholder="ООО, АО..." fluid />
            </div>
          </div>
          <div class="field">
            <label>Адрес</label>
            <InputText v-model="form.orgData.address" placeholder="г. Москва, ул. ..." fluid />
          </div>
        </fieldset>

        <!-- Статус -->
        <div class="field">
          <label>Статус</label>
          <Select v-model="form.status" :options="statusOptions"
            option-label="label" option-value="value" fluid />
        </div>

        <div class="dialog-footer">
          <Button v-if="!isEdit && step === 2" label="← Назад" text @click="step = 1" />
          <Button v-else label="Отмена" text @click="close" />
          <Button
            type="submit"
            :label="isEdit ? 'Сохранить' : 'Создать клиента'"
            :loading="saving"
          />
        </div>
      </form>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import { useVuelidate } from '@vuelidate/core'
import { required, helpers, minLength } from '@vuelidate/validators'
import { customersApi, type CustomerResponse } from '@/api/customers'
import { useAppToast } from '@/composables/useAppToast'

const props = defineProps<{
  visible: boolean
  customer?: CustomerResponse   // передаём для редактирования
}>()

const emit = defineEmits<{
  'update:visible': [boolean]
  'saved': []
}>()

const toast   = useAppToast()
const saving  = ref(false)
const step    = ref(1)

const isEdit = computed(() => !!props.customer)

// ---- Form state ----
const form = reactive({
  customerType: '' as 'INDIVIDUAL' | 'LEGAL_ENTITY' | 'SOLE_TRADER' | '',
  status: 'NEW',
  personalData: { firstName: '', lastName: '', middleName: '', phone: '', position: '', address: '' },
  orgData:      { orgName: '', inn: '', kpp: '', ogrn: '', legalFormId: null as string|null, address: '' },
})

const needPersonal = computed(() => form.customerType !== 'LEGAL_ENTITY')
const needOrg      = computed(() => form.customerType !== 'INDIVIDUAL')

// ---- Validation ----
const rules = computed(() => ({
  ...(needPersonal.value ? {
    personalData: {
      firstName: { required: helpers.withMessage('Обязательно', required) },
      lastName:  { required: helpers.withMessage('Обязательно', required) },
      phone:     { required: helpers.withMessage('Обязательно', required) },
    }
  } : {}),
  ...(needOrg.value ? {
    orgData: {
      orgName: { required: helpers.withMessage('Обязательно', required) },
      inn:     { required: helpers.withMessage('Обязательно', required) },
      ogrn:    { required: helpers.withMessage('Обязательно', required) },
    }
  } : {}),
}))

const v$ = useVuelidate(rules, form)

// ---- Options ----
const typeOptions = [
  { value: 'INDIVIDUAL',   label: 'Физическое лицо', icon: 'pi pi-user',     desc: 'Частный клиент' },
  { value: 'LEGAL_ENTITY', label: 'Юридическое лицо', icon: 'pi pi-building', desc: 'ООО, АО, ПАО...' },
  { value: 'SOLE_TRADER',  label: 'ИП',               icon: 'pi pi-briefcase', desc: 'Индивидуальный предприниматель' },
]

const statusOptions = [
  { label: 'Новый',          value: 'NEW' },
  { label: 'Потенциальный',  value: 'POTENTIAL' },
  { label: 'Активный',       value: 'ACTIVE' },
  { label: 'Неактивный',     value: 'INACTIVE' },
]

// Placeholder — в реальности загружается из /dictionaries/LEGAL_FORM
const legalForms = [
  { id: null, name: '— не выбрано —' },
  { id: 'ooo', name: 'ООО' },
  { id: 'ao',  name: 'АО' },
  { id: 'pao', name: 'ПАО' },
  { id: 'ip',  name: 'ИП' },
]

// ---- Sync with edit data ----
watch(() => props.customer, (c) => {
  if (!c) return
  form.customerType = c.customerType
  form.status       = c.status
  if (c.personalData) {
    form.personalData.firstName  = c.personalData.firstName
    form.personalData.lastName   = c.personalData.lastName
    form.personalData.middleName = c.personalData.middleName ?? ''
    form.personalData.phone      = c.personalData.phone
    form.personalData.position   = c.personalData.position ?? ''
    form.personalData.address    = c.personalData.address  ?? ''
  }
  if (c.orgData) {
    form.orgData.orgName    = c.orgData.orgName
    form.orgData.inn        = c.orgData.inn
    form.orgData.kpp        = c.orgData.kpp ?? ''
    form.orgData.ogrn       = c.orgData.ogrn
    form.orgData.address    = c.orgData.address ?? ''
  }
}, { immediate: true })

// ---- Submit ----
async function handleSubmit() {
  const valid = await v$.value.$validate()
  if (!valid) return

  saving.value = true
  try {
    const payload = {
      customerType: form.customerType as any,
      status:       form.status,
      personalData: needPersonal.value ? { ...form.personalData } : undefined,
      orgData:      needOrg.value      ? { ...form.orgData }      : undefined,
    }

    if (isEdit.value && props.customer) {
      await customersApi.update(props.customer.id, payload)
    } else {
      await customersApi.create(payload)
    }

    emit('saved')
  } catch (e: any) {
    const msg = e?.response?.data?.error?.message ?? 'Ошибка сохранения'
    toast.error(msg)
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:visible', false)
  step.value = 1
  v$.value.$reset()
}
</script>

<style scoped>
.step { display: flex; flex-direction: column; gap: 0; }
.step-hint { font-size: 0.875rem; color: var(--text-muted); margin-bottom: 14px; }

/* Type selection */
.type-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.type-card {
  padding: 16px 12px; border-radius: var(--radius-md);
  border: 1px solid var(--border-default); background: var(--bg-elevated);
  cursor: pointer; display: flex; flex-direction: column; align-items: center;
  gap: 6px; text-align: center; transition: all var(--transition-fast);
}
.type-card:hover { border-color: var(--accent-500); background: var(--bg-hover); }
.type-card.active { border-color: var(--accent-500); background: rgba(59,130,246,0.08); }
.type-card__icon { font-size: 1.5rem; color: var(--accent-400); }
.type-card strong { font-size: 0.875rem; color: var(--text-primary); }
.type-card span { font-size: 0.75rem; color: var(--text-muted); }

/* Form sections */
.form-section {
  border: none; padding: 0; margin: 0 0 20px;
  display: flex; flex-direction: column; gap: 14px;
}
legend {
  font-size: 0.8125rem; font-weight: 600; letter-spacing: 0.06em;
  text-transform: uppercase; color: var(--text-muted);
  padding-bottom: 10px; margin-bottom: 4px;
  border-bottom: 1px solid var(--border-subtle); width: 100%;
}

.field { display: flex; flex-direction: column; gap: 5px; }
.field label { font-size: 0.8125rem; font-weight: 500; color: var(--text-secondary); }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field-error { color: #fca5a5; font-size: 0.8rem; }

/* Footer */
.dialog-footer {
  display: flex; justify-content: flex-end; gap: 10px;
  padding-top: 20px; margin-top: 8px;
  border-top: 1px solid var(--border-subtle);
}
</style>
