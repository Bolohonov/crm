<template>
  <Drawer v-model:visible="localVisible" position="right" :style="{ width: '500px' }">
    <template #header>
      <div class="drawer-title">
        <span>Заказ</span>
        <div class="drawer-title__actions">
          <Button v-if="can('ORDER_EDIT')" icon="pi pi-pencil" text rounded size="small"
                  @click="order && $emit('edit', order)" />
          <Button v-if="can('ORDER_CREATE')" icon="pi pi-trash" text rounded size="small"
                  severity="danger" @click="confirmDelete" />
        </div>
      </div>
    </template>

    <div v-if="loading" class="drawer-loading">
      <ProgressSpinner style="width:36px;height:36px" />
    </div>

    <div v-else-if="order" class="order-detail animate-fade-in">

      <!-- Клиент + статус -->
      <div class="order-detail__hero">
        <div class="hero-avatar" :style="{ background: avatarColor(order.customerName || '') }">
          {{ initials(order.customerName || '') }}
        </div>
        <div>
          <h2>{{ order.customerName || '—' }}</h2>
          <div class="hero-meta">
            <Tag
                :value="order.statusName || order.statusCode"
                :style="order.statusColor ? `background:${order.statusColor}22;color:${order.statusColor};border:1px solid ${order.statusColor}44` : ''"
            />
            <span class="text-muted" style="font-size:0.8125rem">
              {{ formatDate(order.createdAt) }}
            </span>
          </div>
        </div>
      </div>

      <!-- Быстрая смена статуса -->
      <div v-if="statuses.length" class="quick-status">
        <span class="section-label">Статус заказа</span>
        <div class="status-btns">
          <Button v-for="s in statuses" :key="s.id"
                  :label="s.name" text size="small"
                  :class="{ active: order.statusId === s.id }"
                  @click="changeStatus(s.id)"
          />
        </div>
      </div>

      <Divider />

      <!-- Позиции -->
      <div class="order-items">
        <span class="section-label">Позиции ({{ order.items?.length ?? 0 }})</span>
        <div class="item-rows">
          <div v-for="item in order.items" :key="item.id" class="item-row">
            <div class="item-row__info">
              <span class="item-row__name">{{ item.productName }}</span>
              <span v-if="item.productSku" class="item-row__sku text-muted">{{ item.productSku }}</span>
            </div>
            <div class="item-row__qty text-muted">{{ item.quantity }} {{ item.productUnit || 'шт' }}</div>
            <div class="item-row__price text-muted">{{ formatMoney(item.price) }}/шт</div>
            <div class="item-row__total font-mono">{{ formatMoney(item.totalPrice) }}</div>
          </div>
        </div>
        <div class="order-total">
          <span>Итого по заказу</span>
          <span class="order-total__value font-mono">{{ formatMoney(order.totalAmount) }}</span>
        </div>
      </div>

      <Divider />

      <!-- Детали -->
      <div class="order-meta">
        <InfoField label="Автор"       :value="order.authorName" />
        <InfoField label="Комментарий" :value="order.comment" />
        <InfoField label="Создан"      :value="formatDateTime(order.createdAt)" />
        <InfoField label="Обновлён"    :value="formatDateTime(order.updatedAt)" />
      </div>
    </div>
  </Drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import Drawer from 'primevue/drawer'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import Divider from 'primevue/divider'
import ProgressSpinner from 'primevue/progressspinner'
import { useConfirm } from 'primevue/useconfirm'
import { usePermission } from '@/composables/usePermission'
import { useAppToast } from '@/composables/useAppToast'
import { ordersApi, type OrderResponse, type OrderStatus } from '@/api/orders'
import InfoField from '@/components/common/InfoField.vue'
import dayjs from 'dayjs'

const props = defineProps<{
  visible: boolean
  orderId?: string | null
  statuses?: OrderStatus[]
}>()

const emit = defineEmits<{
  'update:visible': [boolean]
  'status-change': [order: OrderResponse, statusId: string]
  'edit': [order: OrderResponse]
  'deleted': []
  'status-changed': []
}>()

const localVisible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val)
})

const confirm = useConfirm()
const { can } = usePermission()
const toast   = useAppToast()

const order   = ref<OrderResponse | null>(null)
const loading = ref(false)
const statuses = computed(() => props.statuses ?? [])

async function loadOrder(id: string) {
  loading.value = true
  try {
    const { data: res } = await ordersApi.getById(id)
    order.value = res.data ?? null
  } catch {
    toast.error('Не удалось загрузить заказ')
  } finally {
    loading.value = false
  }
}

watch([() => props.orderId, () => props.visible], async ([id, visible]) => {
  if (!id || !visible) return
  await loadOrder(id)
}, { immediate: true })

watch(() => props.visible, (v) => {
  if (!v) order.value = null
})

async function changeStatus(statusId: string) {
  if (!order.value || !props.orderId) return
  emit('status-change', order.value, statusId)
  await new Promise(r => setTimeout(r, 400))
  await loadOrder(props.orderId)
  emit('status-changed')
}

function confirmDelete() {
  confirm.require({
    message: `Удалить заказ клиента «${order.value?.customerName}»?`,
    header: 'Удаление заказа',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    acceptLabel: 'Удалить', rejectLabel: 'Отмена',
    accept: async () => {
      try {
        await ordersApi.delete(order.value!.id)
        emit('deleted')
        emit('update:visible', false)
      } catch { toast.error('Не удалось удалить') }
    }
  })
}

const COLORS = ['#3b82f6','#8b5cf6','#ec4899','#f59e0b','#22c55e','#06b6d4']
function avatarColor(n: string) { let h=0; for(const c of n) h=(h*31+c.charCodeAt(0))|0; return COLORS[Math.abs(h)%COLORS.length] }
function initials(n: string) { return n?.trim().split(' ').slice(0,2).map(p=>p[0]?.toUpperCase()??'').join('') || '?' }
function formatMoney(v: number | null | undefined) {
  if (v == null) return '—'
  return new Intl.NumberFormat('ru-RU', { style:'currency', currency:'RUB', maximumFractionDigits:0 }).format(v)
}
function formatDate(iso: string) { return dayjs(iso).format('D MMMM YYYY') }
function formatDateTime(iso: string) { return dayjs(iso).format('D MMM YYYY, HH:mm') }
</script>

<style scoped>
.drawer-title { display: flex; align-items: center; justify-content: space-between; width: 100%; }
.drawer-title__actions { display: flex; gap: 4px; }
.drawer-loading { display: flex; justify-content: center; padding: 40px; }
.order-detail { display: flex; flex-direction: column; gap: 16px; padding-bottom: 24px; }
.order-detail__hero { display: flex; align-items: center; gap: 14px; }
.hero-avatar { width: 52px; height: 52px; border-radius: 14px; display: flex; align-items: center; justify-content: center; color: white; font-size: 1.125rem; font-weight: 700; flex-shrink: 0; }
.order-detail__hero h2 { font-size: 1.125rem; font-weight: 700; color: var(--text-primary); margin-bottom: 6px; }
.hero-meta { display: flex; align-items: center; gap: 10px; }
.section-label { font-size: 0.75rem; font-weight: 600; letter-spacing: 0.08em; text-transform: uppercase; color: var(--text-muted); }
.quick-status { display: flex; flex-direction: column; gap: 8px; }
.status-btns { display: flex; gap: 6px; flex-wrap: wrap; }
.status-btns .p-button { border: 1px solid var(--border-default) !important; border-radius: var(--radius-md) !important; }
.status-btns .p-button.active { background: var(--bg-elevated) !important; border-color: var(--accent-500) !important; color: var(--accent-400) !important; }
.order-items { display: flex; flex-direction: column; gap: 10px; }
.item-rows { display: flex; flex-direction: column; gap: 6px; }
.item-row { display: flex; align-items: center; gap: 10px; padding: 8px 10px; background: var(--bg-elevated); border-radius: var(--radius-sm); }
.item-row__info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
.item-row__name { font-size: 0.875rem; font-weight: 500; color: var(--text-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.item-row__sku { font-size: 0.75rem; }
.item-row__qty, .item-row__price { font-size: 0.8125rem; white-space: nowrap; }
.item-row__total { font-size: 0.875rem; font-weight: 600; color: var(--text-primary); white-space: nowrap; min-width: 80px; text-align: right; }
.order-total { display: flex; justify-content: space-between; align-items: center; padding: 12px; border-top: 1px solid var(--border-subtle); font-size: 0.875rem; color: var(--text-secondary); }
.order-total__value { font-size: 1.0625rem; font-weight: 700; color: var(--text-primary); }
.order-meta { display: flex; flex-direction: column; gap: 12px; }
</style>