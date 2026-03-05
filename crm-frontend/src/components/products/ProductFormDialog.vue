<template>
  <Dialog
    v-model:visible="localVisible"
    :header="isEdit ? 'Редактировать товар' : 'Новый товар'"
    modal :style="{ width: '520px' }" :draggable="false"
  >
    <form @submit.prevent="handleSubmit" class="product-form">

      <div v-if="error" class="form-error">
        <i class="pi pi-exclamation-circle" /> {{ error }}
      </div>

      <div class="field">
        <label>Название *</label>
        <InputText v-model="form.name" placeholder='Например: "Консультация 1 час"'
          :class="{ 'p-invalid': v$.name.$error }" fluid />
        <small v-if="v$.name.$error" class="field-error">{{ v$.name.$errors[0].$message }}</small>
      </div>

      <div class="field-row">
        <div class="field">
          <label>Артикул (SKU)</label>
          <InputText v-model="form.sku" placeholder="CONS-001" fluid />
        </div>
        <div class="field">
          <label>Единица измерения</label>
          <Select v-model="form.unit" :options="unitOptions"
            option-label="label" option-value="value" fluid />
        </div>
      </div>

      <div class="field">
        <label>Цена *</label>
        <InputNumber
          v-model="form.price"
          :min="0" mode="currency" currency="RUB" locale="ru-RU"
          :class="{ 'p-invalid': v$.price.$error }"
          fluid
        />
        <small v-if="v$.price.$error" class="field-error">{{ v$.price.$errors[0].$message }}</small>
      </div>

      <div class="field">
        <label>Описание</label>
        <Textarea v-model="form.description" placeholder="Подробное описание товара или услуги..."
          rows="3" auto-resize fluid />
      </div>

      <div class="field field--toggle">
        <label>Активен</label>
        <ToggleSwitch v-model="form.isActive" />
        <span class="toggle-hint text-muted">
          {{ form.isActive ? 'Товар доступен для выбора в заказах' : 'Товар скрыт из каталога' }}
        </span>
      </div>

      <div class="dialog-footer">
        <Button label="Отмена" text type="button" @click="close" />
        <Button type="submit"
          :label="isEdit ? 'Сохранить' : 'Создать товар'"
          :loading="saving" />
      </div>
    </form>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import ToggleSwitch from 'primevue/toggleswitch'
import { useVuelidate } from '@vuelidate/core'
import { required, helpers, minValue } from '@vuelidate/validators'
import { productsApi, type ProductResponse } from '@/api/products'
import { useAppToast } from '@/composables/useAppToast'

const props = defineProps<{ visible: boolean; product?: ProductResponse | null }>()
const emit  = defineEmits<{ 'update:visible': [boolean]; 'saved': [] }>()

const toast  = useAppToast()
const saving = ref(false)
const error  = ref('')
const isEdit = computed(() => !!props.product)

const form = reactive({
  name:        '',
  description: '',
  sku:         '',
  price:       0 as number | null,
  unit:        'шт',
  isActive:    true,
})

const unitOptions = [
  { label: 'шт',   value: 'шт' },
  { label: 'кг',   value: 'кг' },
  { label: 'л',    value: 'л' },
  { label: 'м',    value: 'м' },
  { label: 'м²',   value: 'м²' },
  { label: 'час',  value: 'час' },
  { label: 'день', value: 'день' },
  { label: 'усл.', value: 'усл.' },
]

const rules = {
  name:  { required: helpers.withMessage('Название обязательно', required) },
  price: { required: helpers.withMessage('Цена обязательна', required), minValue: helpers.withMessage('Цена ≥ 0', minValue(0)) },
}
const v$ = useVuelidate(rules, form)

watch(() => props.product, (p) => {
  if (!p) { Object.assign(form, { name:'', description:'', sku:'', price:0, unit:'шт', isActive:true }); return }
  form.name        = p.name
  form.description = p.description ?? ''
  form.sku         = p.sku ?? ''
  form.price       = p.price
  form.unit        = p.unit ?? 'шт'
  form.isActive    = p.isActive
}, { immediate: true })

async function handleSubmit() {
  error.value = ''
  if (!(await v$.value.$validate())) return
  saving.value = true
  try {
    const payload = {
      name: form.name, description: form.description || undefined,
      sku: form.sku || undefined, price: form.price!,
      unit: form.unit, isActive: form.isActive,
    }
    if (isEdit.value && props.product) {
      await productsApi.update(props.product.id, payload)
    } else {
      await productsApi.create(payload)
    }
    toast.success(isEdit.value ? 'Товар обновлён' : 'Товар создан')
    emit('saved')
  } catch (e: any) {
    error.value = e?.response?.data?.error?.message ?? 'Ошибка сохранения'
  } finally {
    saving.value = false
  }
}

function close() { emit('update:visible', false); v$.value.$reset(); error.value = '' }
</script>

<style scoped>
.product-form { display: flex; flex-direction: column; gap: 16px; }
.field { display: flex; flex-direction: column; gap: 5px; }
.field label { font-size: 0.8125rem; font-weight: 500; color: var(--text-secondary); }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field-error { color: #fca5a5; font-size: 0.8rem; }
.form-error { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: var(--radius-md); color: #fca5a5; font-size: 0.875rem; }
.field--toggle { flex-direction: row; align-items: center; gap: 12px; flex-wrap: wrap; }
.field--toggle label { min-width: 60px; margin-bottom: 0; }
.toggle-hint { font-size: 0.8rem; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 16px; border-top: 1px solid var(--border-subtle); }
</style>
