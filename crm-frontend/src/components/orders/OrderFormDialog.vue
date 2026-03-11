<template>
  <Dialog
      v-model:visible="localVisible"
      :header="isEdit ? 'Редактировать заказ' : 'Новый заказ'"
      modal
      :style="{ width: '680px' }"
      :draggable="false"
  >
    <form @submit.prevent="handleSubmit" class="order-form">

      <div v-if="error" class="form-error">
        <i class="pi pi-exclamation-circle" />{{ error }}
      </div>

      <!-- Клиент -->
      <div class="field">
        <label>Клиент *</label>
        <AutoComplete
            v-model="customerSearch"
            :suggestions="customerSuggestions"
            option-label="displayName"
            placeholder="Введите имя или название организации..."
            @complete="searchCustomers"
            @option-select="onCustomerSelect"
            force-selection
            fluid
            :class="{ 'p-invalid': v$.customerId.$error }"
        />
        <small v-if="v$.customerId.$error" class="field-error">Клиент обязателен</small>
      </div>

      <!-- Статус + комментарий -->
      <div class="field-row">
        <div class="field">
          <label>Статус</label>
          <Select
              v-model="form.statusId"
              :options="statusOptions"
              option-label="name"
              option-value="id"
              placeholder="Выберите статус"
              :loading="loadingStatuses"
              fluid
          />
        </div>
        <div class="field">
          <label>Комментарий</label>
          <InputText v-model="form.comment" placeholder="Заметки к заказу..." fluid />
        </div>
      </div>

      <!-- Позиции заказа -->
      <div class="items-section">
        <div class="items-header">
          <span class="section-label">Позиции заказа</span>
          <Button icon="pi pi-plus" label="Добавить товар" text size="small"
                  type="button" @click="showProductPicker = true" />
        </div>

        <div v-if="form.items.length === 0" class="items-empty">
          <i class="pi pi-box" />
          <span>Нажмите «Добавить товар» чтобы добавить позиции</span>
        </div>

        <div class="items-list" v-else>
          <div v-for="(item, idx) in form.items" :key="idx" class="order-item">
            <div class="order-item__product">
              <span class="order-item__name">{{ item.productName }}</span>
              <span class="order-item__sku text-muted" v-if="item.sku">{{ item.sku }}</span>
            </div>
            <div class="order-item__qty">
              <InputNumber
                  v-model="item.quantity"
                  :min="0.001" :max="99999"
                  :min-fraction-digits="0" :max-fraction-digits="3"
                  @update:model-value="recalc(item)"
                  style="width: 100px"
              />
              <span class="text-muted" style="font-size:0.8125rem">{{ item.unit || 'шт' }}</span>
            </div>
            <div class="order-item__price">
              <InputNumber
                  v-model="item.price"
                  :min="0" mode="currency" currency="RUB" locale="ru-RU"
                  @update:model-value="recalc(item)"
                  style="width: 130px"
              />
            </div>
            <div class="order-item__total font-mono">
              {{ formatMoney(item.totalPrice) }}
            </div>
            <Button icon="pi pi-times" text rounded size="small"
                    severity="danger" type="button" @click="removeItem(idx)" />
          </div>

          <div class="items-total">
            <span>Итого:</span>
            <span class="items-total__value font-mono">{{ formatMoney(orderTotal) }}</span>
          </div>
        </div>
      </div>

      <div class="dialog-footer">
        <Button label="Отмена" text type="button" @click="close" />
        <Button type="submit"
                :label="isEdit ? 'Сохранить' : 'Создать заказ'"
                :loading="saving"
                :disabled="form.items.length === 0" />
      </div>
    </form>

    <!-- Пикер товаров -->
    <Dialog v-model:visible="showProductPicker" header="Выбрать товар"
            modal :style="{ width: '520px' }" :draggable="false">
      <div class="product-picker">
        <IconField>
          <InputIcon class="pi pi-search" />
          <InputText v-model="productSearch" placeholder="Поиск товара..."
                     @input="searchProducts" fluid />
        </IconField>
        <div class="product-list">
          <div v-for="p in filteredProducts" :key="p.id"
               class="product-row" @click="addProduct(p)">
            <div class="product-row__info">
              <span class="product-row__name">{{ p.name }}</span>
              <span class="product-row__sku text-muted" v-if="p.sku">{{ p.sku }}</span>
            </div>
            <div class="product-row__price font-mono">{{ formatMoney(p.price) }}</div>
            <Button icon="pi pi-plus" text rounded size="small" type="button" />
          </div>
          <div v-if="filteredProducts.length === 0" class="picker-empty text-muted">
            Товары не найдены
          </div>
        </div>
      </div>
    </Dialog>

  </Dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import Dialog from 'primevue/dialog'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import AutoComplete from 'primevue/autocomplete'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import { useVuelidate } from '@vuelidate/core'
import { required, helpers } from '@vuelidate/validators'
import { ordersApi, type OrderResponse, type OrderStatus } from '@/api/orders'
import { productsApi, type ProductResponse } from '@/api/products'
import { customersApi } from '@/api/customers'
import { useAppToast } from '@/composables/useAppToast'

const props = defineProps<{ visible: boolean; order?: OrderResponse | null }>()
const emit  = defineEmits<{ 'update:visible': [boolean]; 'saved': [] }>()

const localVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const toast  = useAppToast()
const saving = ref(false)
const error  = ref('')
const isEdit = computed(() => !!props.order)

interface LineItem {
  productId: string; productName: string; sku?: string; unit?: string
  quantity: number; price: number; totalPrice: number
}

const form = reactive({
  customerId: '',
  statusId:   null as string | null,
  comment:    '',
  items:      [] as LineItem[],
})

const customerSearch      = ref<any>('')
const customerSuggestions = ref<any[]>([])
const showProductPicker   = ref(false)
const productSearch       = ref('')
const allProducts         = ref<ProductResponse[]>([])
const filteredProducts    = ref<ProductResponse[]>([])

// Статусы с бэка
const statusOptions   = ref<OrderStatus[]>([])
const loadingStatuses = ref(false)

onMounted(async () => {
  loadingStatuses.value = true
  try {
    const { data: res } = await ordersApi.getStatuses()
    statusOptions.value = res.data ?? []
  } catch {
    toast.error('Не удалось загрузить статусы')
  } finally {
    loadingStatuses.value = false
  }
})

const rules = { customerId: { required: helpers.withMessage('Обязательно', required) } }
const v$ = useVuelidate(rules, form)

const orderTotal = computed(() =>
    form.items.reduce((sum, i) => sum + (i.totalPrice || 0), 0)
)

// Заполнение при редактировании
watch(() => props.order, (o) => {
  if (!o) return
  form.customerId = o.customerId
  form.statusId   = o.statusId ?? null
  form.comment    = o.comment ?? ''
  customerSearch.value = { displayName: o.customerName }
  form.items = (o.items ?? []).map(i => ({
    productId:   i.productId,
    productName: i.productName,
    sku:         i.productSku,
    unit:        i.productUnit,
    quantity:    i.quantity,
    price:       i.price,
    totalPrice:  i.totalPrice,
  }))
}, { immediate: true })

// Загружаем товары при открытии пикера (lazy)
watch(showProductPicker, async (open) => {
  if (!open || allProducts.value.length) return
  try {
    const { data: res } = await productsApi.list({ onlyActive: true, size: 200 })
    allProducts.value = res.data?.content ?? []
    filteredProducts.value = allProducts.value
  } catch { toast.error('Не удалось загрузить товары') }
})

async function searchCustomers(event: { query: string }) {
  try {
    const { data: res } = await customersApi.search({ query: event.query, size: 10 })
    customerSuggestions.value = res.data?.content ?? []
  } catch { customerSuggestions.value = [] }
}

function onCustomerSelect(event: { value: any }) {
  form.customerId = event.value.id
}

function searchProducts() {
  const q = productSearch.value.toLowerCase()
  filteredProducts.value = allProducts.value.filter(p =>
      p.name.toLowerCase().includes(q) || (p.sku ?? '').toLowerCase().includes(q)
  )
}

function addProduct(p: ProductResponse) {
  const existing = form.items.find(i => i.productId === p.id)
  if (existing) {
    existing.quantity += 1
    recalc(existing)
  } else {
    form.items.push({
      productId: p.id, productName: p.name,
      sku: p.sku, unit: p.unit,
      quantity: 1, price: p.price, totalPrice: p.price,
    })
  }
  showProductPicker.value = false
  productSearch.value = ''
}

function removeItem(idx: number) { form.items.splice(idx, 1) }

function recalc(item: LineItem) {
  item.totalPrice = Number((item.quantity * item.price).toFixed(2))
}

async function handleSubmit() {
  error.value = ''
  if (!(await v$.value.$validate())) return
  if (form.items.length === 0) { error.value = 'Добавьте хотя бы одну позицию'; return }

  saving.value = true
  try {
    const payload = {
      customerId: form.customerId,
      statusId:   form.statusId || undefined,
      comment:    form.comment  || undefined,
      items: form.items.map(i => ({
        productId: i.productId,
        quantity:  i.quantity,
        price:     i.price,
      })),
    }
    if (isEdit.value && props.order) {
      await ordersApi.update(props.order.id, payload)
    } else {
      await ordersApi.create(payload)
    }
    emit('saved')
    close()
  } catch (e: any) {
    error.value = e?.response?.data?.error?.message ?? 'Ошибка сохранения'
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:visible', false)
  v$.value.$reset()
  error.value     = ''
  form.customerId = ''
  form.statusId   = null
  form.comment    = ''
  form.items      = []
  customerSearch.value = ''
}

function formatMoney(val: number | null | undefined) {
  if (val == null) return '—'
  return new Intl.NumberFormat('ru-RU', { style:'currency', currency:'RUB', maximumFractionDigits:0 }).format(val)
}
</script>

<style scoped>
.order-form { display: flex; flex-direction: column; gap: 18px; }
.field { display: flex; flex-direction: column; gap: 5px; }
.field label { font-size: 0.8125rem; font-weight: 500; color: var(--text-secondary); }
.field-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.field-error { color: #fca5a5; font-size: 0.8rem; }
.form-error { display: flex; align-items: center; gap: 8px; padding: 10px 14px; background: rgba(239,68,68,0.1); border: 1px solid rgba(239,68,68,0.2); border-radius: var(--radius-md); color: #fca5a5; font-size: 0.875rem; }

.section-label { font-size: 0.75rem; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: var(--text-muted); }
.items-section { display: flex; flex-direction: column; gap: 10px; }
.items-header { display: flex; align-items: center; justify-content: space-between; }
.items-empty { padding: 24px; text-align: center; border: 1px dashed var(--border-default); border-radius: var(--radius-md); display: flex; flex-direction: column; align-items: center; gap: 8px; color: var(--text-muted); font-size: 0.875rem; }
.items-empty .pi { font-size: 1.5rem; }
.items-list { display: flex; flex-direction: column; gap: 6px; }

.order-item { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: var(--radius-md); background: var(--bg-elevated); border: 1px solid var(--border-subtle); }
.order-item__product { flex: 1; display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.order-item__name { font-size: 0.875rem; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.order-item__sku { font-size: 0.75rem; }
.order-item__qty { display: flex; align-items: center; gap: 6px; }
.order-item__price { flex-shrink: 0; }
.order-item__total { min-width: 100px; text-align: right; font-size: 0.9rem; font-weight: 600; color: var(--text-primary); }

.items-total { display: flex; justify-content: flex-end; align-items: center; gap: 16px; padding: 12px; border-top: 1px solid var(--border-subtle); font-size: 0.875rem; color: var(--text-secondary); }
.items-total__value { font-size: 1.0625rem; font-weight: 700; color: var(--text-primary); }

.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; padding-top: 16px; border-top: 1px solid var(--border-subtle); }

.product-picker { display: flex; flex-direction: column; gap: 12px; }
.product-list { display: flex; flex-direction: column; gap: 4px; max-height: 360px; overflow-y: auto; }
.product-row { display: flex; align-items: center; gap: 12px; padding: 10px 12px; border-radius: var(--radius-md); cursor: pointer; transition: background var(--transition-fast); }
.product-row:hover { background: var(--bg-hover); }
.product-row__info { flex: 1; display: flex; flex-direction: column; gap: 2px; }
.product-row__name { font-size: 0.875rem; font-weight: 500; color: var(--text-primary); }
.product-row__sku { font-size: 0.75rem; }
.product-row__price { font-size: 0.875rem; font-weight: 600; color: var(--text-primary); font-family: 'DM Mono', monospace; }
.picker-empty { padding: 24px; text-align: center; font-size: 0.875rem; }
</style>
