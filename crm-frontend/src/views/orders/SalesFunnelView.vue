<template>
  <div class="funnel-page animate-fade-in">

    <!-- ── Заголовок и фильтры ──────────────────────────────────── -->
    <div class="page-header">
      <div class="page-header__left">
        <h1>Воронка продаж</h1>
        <p class="text-muted">{{ totalOrders }} заказов · {{ fmtMoney(totalRevenue) }}</p>
      </div>
      <div class="page-header__actions">
        <Select
          v-model="periodFilter"
          :options="periodOptions"
          option-label="label"
          option-value="value"
          placeholder="Период"
          size="small"
          style="width:160px"
        />
        <Button
          v-if="auth.can('ORDER_CREATE')"
          icon="pi pi-plus"
          label="Новый заказ"
          size="small"
          @click="showNewOrder = true"
        />
      </div>
    </div>

    <!-- ── Сводные метрики ──────────────────────────────────────── -->
    <div class="funnel-metrics">
      <div
        v-for="stage in stages"
        :key="stage.code"
        class="metric-card"
        :class="{ 'metric-card--active': activeStage === stage.code }"
        :style="{ '--sc': stage.color }"
        @click="activeStage = activeStage === stage.code ? null : stage.code"
      >
        <div class="metric-card__header">
          <span class="metric-card__name">{{ stage.name }}</span>
          <i :class="stage.icon" class="metric-card__icon" />
        </div>
        <div class="metric-card__count font-mono">{{ stage.orders.length }}</div>
        <div class="metric-card__amount font-mono">{{ fmtMoney(stageRevenue(stage)) }}</div>
        <div class="metric-card__bar">
          <div
            class="metric-card__bar-fill"
            :style="{ width: `${stagePct(stage)}%` }"
          />
        </div>
      </div>
    </div>

    <!-- ── Kanban-колонки ───────────────────────────────────────── -->
    <div class="kanban" :class="{ 'kanban--filtered': activeStage }">
      <div
        v-for="stage in visibleStages"
        :key="stage.code"
        class="kanban-col"
        :style="{ '--sc': stage.color }"
        @dragover.prevent
        @drop="onDrop($event, stage.code)"
      >
        <!-- Заголовок колонки -->
        <div class="kanban-col__header">
          <div class="kanban-col__title">
            <span class="kanban-col__dot" />
            <span>{{ stage.name }}</span>
            <span class="kanban-col__count font-mono">{{ stage.orders.length }}</span>
          </div>
          <div class="kanban-col__rev font-mono">{{ fmtMoney(stageRevenue(stage)) }}</div>
        </div>

        <!-- Карточки заказов -->
        <div class="kanban-col__cards">
          <div
            v-for="order in stage.orders"
            :key="order.id"
            class="order-card"
            :class="{ 'order-card--large': order.amount >= 300_000 }"
            draggable="true"
            @dragstart="onDragStart($event, order, stage.code)"
            @click="openOrder(order)"
          >
            <!-- Сумма заказа — главный акцент -->
            <div class="order-card__amount font-mono">{{ fmtMoney(order.amount) }}</div>

            <!-- Клиент -->
            <div class="order-card__customer">
              <div class="order-card__avatar" :style="{ background: avatarColor(order.customerName) }">
                {{ initials(order.customerName) }}
              </div>
              <div class="order-card__customer-info">
                <span class="order-card__customer-name">{{ order.customerName }}</span>
                <span class="order-card__items text-muted">{{ order.itemsCount }} {{ pluralItems(order.itemsCount) }}</span>
              </div>
            </div>

            <!-- Мета -->
            <div class="order-card__meta">
              <span class="order-card__date text-muted">
                <i class="pi pi-calendar" />
                {{ fmtDate(order.createdAt) }}
              </span>
              <span
                v-if="order.isWaiting"
                class="order-card__wait-badge"
                title="Ожидает действия"
              >
                <i class="pi pi-clock" /> ждёт
              </span>
            </div>

            <!-- Комментарий -->
            <div v-if="order.comment" class="order-card__comment text-muted">
              {{ truncate(order.comment, 60) }}
            </div>

            <!-- Действия при hover -->
            <div v-if="auth.can('ORDER_EDIT')" class="order-card__actions">
              <button
                v-for="target in moveTargets(stage.code)"
                :key="target.code"
                class="order-card__move-btn"
                :style="{ '--tc': target.color }"
                :title="`→ ${target.name}`"
                @click.stop="moveOrder(order, target.code)"
              >
                <i class="pi pi-arrow-right" />
                {{ target.name }}
              </button>
            </div>
          </div>

          <!-- Пустая колонка -->
          <div v-if="stage.orders.length === 0" class="kanban-col__empty">
            <i class="pi pi-inbox" />
            <span>Пусто</span>
          </div>

          <!-- Добавить заказ -->
          <button
            v-if="auth.can('ORDER_CREATE') && stage.code === 'NEW'"
            class="kanban-col__add"
            @click="showNewOrder = true"
          >
            <i class="pi pi-plus" /> Добавить заказ
          </button>
        </div>
      </div>
    </div>

    <!-- ── Нижняя аналитика ─────────────────────────────────────── -->
    <div class="funnel-analytics surface-card">
      <div class="analytics-title">
        <i class="pi pi-chart-bar" /> Конверсия по этапам
      </div>
      <div class="conv-flow">
        <div
          v-for="(stage, idx) in stages.filter(s => s.code !== 'CANCELLED')"
          :key="stage.code"
          class="conv-step"
        >
          <!-- Блок этапа -->
          <div class="conv-step__block" :style="{ '--sc': stage.color }">
            <div class="conv-step__name">{{ stage.name }}</div>
            <div class="conv-step__count font-mono">{{ stage.orders.length }}</div>
          </div>

          <!-- Стрелка и конверсия -->
          <div v-if="idx < stages.filter(s => s.code !== 'CANCELLED').length - 1"
               class="conv-step__arrow">
            <div class="conv-step__pct font-mono"
                 :class="convPct(idx) >= 50 ? 'pct--good' : 'pct--low'">
              {{ convPct(idx) }}%
            </div>
            <i class="pi pi-arrow-right" />
          </div>
        </div>

        <!-- Отменённые -->
        <div class="conv-cancelled">
          <i class="pi pi-times-circle" style="color:#ef4444" />
          <span class="font-mono">{{ cancelledStage?.orders.length ?? 0 }}</span>
          <span class="text-muted">отменено</span>
        </div>
      </div>
    </div>

    <!-- ── Диалог нового заказа (заглушка-роутинг) ──────────────── -->
    <Dialog
      v-model:visible="showNewOrder"
      header="Новый заказ"
      modal
      :style="{ width: '460px' }"
    >
      <p class="text-muted" style="margin-bottom:16px">
        Создать новый заказ в статусе «Новый»:
      </p>
      <div class="dialog-actions">
        <Button label="Перейти к заказам" icon="pi pi-external-link"
          @click="$router.push('/orders'); showNewOrder = false" />
        <Button label="Отмена" outlined @click="showNewOrder = false" />
      </div>
    </Dialog>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import dayjs from 'dayjs'
import 'dayjs/locale/ru'
import { useAuthStore } from '@/stores/auth'
import { ordersApi } from '@/api/orders'

dayjs.locale('ru')

const auth   = useAuthStore()
const router = useRouter()

// ── Типы ─────────────────────────────────────────────────────────
interface FunnelOrder {
  id: string
  customerName: string
  amount: number
  itemsCount: number
  createdAt: string
  comment?: string
  statusCode: string
  isWaiting: boolean
}

interface Stage {
  code:    string
  name:    string
  color:   string
  icon:    string
  orders:  FunnelOrder[]
}

// ── Состояние ─────────────────────────────────────────────────────
const showNewOrder  = ref(false)
const activeStage   = ref<string | null>(null)
const periodFilter  = ref('all')
const dragging      = ref<{ order: FunnelOrder; fromCode: string } | null>(null)

const periodOptions = [
  { label: 'Всё время',   value: 'all'   },
  { label: 'Этот месяц',  value: 'month' },
  { label: 'Квартал',     value: 'quarter'},
  { label: 'Этот год',    value: 'year'  },
]

// ── Начальные данные (из демо-seed) ──────────────────────────────
const stages = ref<Stage[]>([
  {
    code: 'NEW', name: 'Новый', color: '#3b82f6', icon: 'pi pi-inbox',
    orders: [
      { id:'55', customerName:'ИП Муратов Р.И.',            amount:95_000,   itemsCount:1, createdAt:'2026-02-22', comment:'Запрос на аудит безопасности сайта', statusCode:'NEW', isWaiting:false },
      { id:'56', customerName:'ИП Лисицын Н.В.',            amount:145_000,  itemsCount:1, createdAt:'2026-02-23', comment:'Коммутатор + монтаж в офисе',       statusCode:'NEW', isWaiting:false },
      { id:'57', customerName:'ИП Нестеров Ф.О.',           amount:350_000,  itemsCount:1, createdAt:'2026-02-24', comment:'Портал для физиков (первичный контакт)', statusCode:'NEW', isWaiting:false },
      { id:'58', customerName:'Соловьёва Е.П.',             amount:68_000,   itemsCount:1, createdAt:'2026-02-24', comment:'ИБП для домашнего сервера',         statusCode:'NEW', isWaiting:false },
      { id:'59', customerName:'Тарасов Е.М.',               amount:8_500,    itemsCount:1, createdAt:'2026-02-25', comment:'Консультация по выбору оборудования',statusCode:'NEW', isWaiting:false },
      { id:'60', customerName:'ООО «ЭдуТех»',               amount:72_000,   itemsCount:1, createdAt:'2026-02-25', comment:'Продление лицензии CRM',            statusCode:'NEW', isWaiting:false },
      { id:'63', customerName:'АО «ОптТрейд»',              amount:890_000,  itemsCount:1, createdAt:'2026-02-26', comment:'Серверная стойка под расширение',   statusCode:'NEW', isWaiting:false },
      { id:'64', customerName:'ПАО «Инфосистемы»',          amount:180_000,  itemsCount:1, createdAt:'2026-02-27', comment:'Внедрение 1С:Бухгалтерия',          statusCode:'NEW', isWaiting:false },
      { id:'71', customerName:'ООО «НейроТех»',             amount:480_000,  itemsCount:1, createdAt:'2026-02-28', comment:'Нейросетевая платформа на Flask',   statusCode:'NEW', isWaiting:false },
      { id:'72', customerName:'Лукьянова Т.С.',             amount:45_000,   itemsCount:1, createdAt:'2026-02-28', comment:'Поддержка 24/7 на 1 месяц',         statusCode:'NEW', isWaiting:false },
    ],
  },
  {
    code: 'IN_PROGRESS', name: 'В работе', color: '#f59e0b', icon: 'pi pi-sync',
    orders: [
      { id:'21', customerName:'АО «Мегастрой»',             amount:350_000,  itemsCount:1, createdAt:'2025-11-28', comment:'Веб-приложение для управления объектами', statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'22', customerName:'ООО «АвтоПласт»',            amount:180_000,  itemsCount:1, createdAt:'2025-11-28', comment:'Внедрение 1С:Управление производством',  statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'23', customerName:'АО «ОптТрейд»',              amount:240_000,  itemsCount:1, createdAt:'2025-12-07', comment:'CRM Enterprise + интеграция с сайтом',    statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'24', customerName:'ЗАО «ФармаЛаб»',             amount:480_000,  itemsCount:1, createdAt:'2025-12-14', comment:'Мобильное приложение для фармацевтов',    statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'25', customerName:'АО «Грузовые Системы»',      amount:85_000,   itemsCount:1, createdAt:'2025-12-21', comment:'Kubernetes для логистической платформы',   statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'26', customerName:'ЗАО «СилаТок»',              amount:890_000,  itemsCount:1, createdAt:'2026-01-11', comment:'Серверная инфраструктура + ПО',           statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'30', customerName:'ООО «Путешествия»',           amount:350_000,  itemsCount:1, createdAt:'2026-01-25', comment:'Система бронирования туров',              statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'36', customerName:'ООО «Диджитал Агентство»',   amount:350_000,  itemsCount:1, createdAt:'2026-02-14', comment:'Сайт + CRM интеграция для агентства',     statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'37', customerName:'ООО «ИнноВаль»',             amount:480_000,  itemsCount:1, createdAt:'2026-02-14', comment:'Мобильное приложение для стартапа',       statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'39', customerName:'Климова В.Д.',                amount:115_000,  itemsCount:1, createdAt:'2026-02-20', comment:'Рабочая станция для дизайнера',           statusCode:'IN_PROGRESS', isWaiting:false },
      { id:'40', customerName:'Волкова К.И.',                amount:68_000,   itemsCount:1, createdAt:'2026-02-23', comment:'UPS для домашнего офиса',                 statusCode:'IN_PROGRESS', isWaiting:false },
    ],
  },
  {
    code: 'WAITING', name: 'Ожидает', color: '#8b5cf6', icon: 'pi pi-clock',
    orders: [
      { id:'43', customerName:'ООО «Маркет Мейкер»',        amount:240_000,  itemsCount:1, createdAt:'2026-02-07', comment:'Ожидаем подписания договора',             statusCode:'WAITING', isWaiting:true },
      { id:'44', customerName:'ООО «Отель Плюс»',           amount:85_000,   itemsCount:1, createdAt:'2026-02-14', comment:'Клиент уточняет бюджет',                  statusCode:'WAITING', isWaiting:true },
      { id:'45', customerName:'ООО «НейроТех»',             amount:350_000,  itemsCount:1, createdAt:'2026-02-14', comment:'Ожидаем ТЗ от разработчиков клиента',     statusCode:'WAITING', isWaiting:true },
      { id:'46', customerName:'Абрамов Г.Е.',               amount:145_000,  itemsCount:1, createdAt:'2026-02-18', comment:'Клиент рассматривает предложение',         statusCode:'WAITING', isWaiting:true },
      { id:'49', customerName:'Прохоров В.С.',              amount:120_000,  itemsCount:1, createdAt:'2026-02-23', comment:'Ожидаем технических данных',               statusCode:'WAITING', isWaiting:true },
      { id:'50', customerName:'Нестеров Ф.О. (тендер)',    amount:890_000,  itemsCount:1, createdAt:'2026-02-24', comment:'Крупный проект, ожидаем тендерное решение',statusCode:'WAITING', isWaiting:true },
      { id:'52', customerName:'Антонова Ю.В.',              amount:480_000,  itemsCount:1, createdAt:'2026-02-26', comment:'Ожидаем дизайн-макеты',                   statusCode:'WAITING', isWaiting:true },
    ],
  },
  {
    code: 'DONE', name: 'Выполнен', color: '#22c55e', icon: 'pi pi-check-circle',
    orders: [
      { id:'1',  customerName:'ООО «ТехноПрогресс»',        amount:350_000,  itemsCount:1, createdAt:'2025-02-28', comment:'Разработка корпоративного портала',       statusCode:'DONE', isWaiting:false },
      { id:'3',  customerName:'ООО «СтройГрупп»',           amount:890_000,  itemsCount:1, createdAt:'2025-04-28', comment:'Сервер HPE + настройка',                  statusCode:'DONE', isWaiting:false },
      { id:'4',  customerName:'ООО «ФинГрупп»',             amount:240_000,  itemsCount:1, createdAt:'2025-04-28', comment:'Лицензия CRM Enterprise',                 statusCode:'DONE', isWaiting:false },
      { id:'5',  customerName:'ФГУП «Гос. Технологии»',     amount:580_000,  itemsCount:3, createdAt:'2025-05-28', comment:'Внедрение 1С + обучение персонала',       statusCode:'DONE', isWaiting:false },
      { id:'10', customerName:'ООО «ТрансЛог»',             amount:28_000,   itemsCount:1, createdAt:'2025-07-28', comment:'Облачный хостинг, 1 год',                 statusCode:'DONE', isWaiting:false },
      { id:'11', customerName:'ЗАО «МашПром»',              amount:480_000,  itemsCount:1, createdAt:'2025-08-28', comment:'Мобильное приложение для производства',   statusCode:'DONE', isWaiting:false },
      { id:'15', customerName:'ООО «ТоргСеть»',             amount:72_000,   itemsCount:1, createdAt:'2025-10-28', comment:'Лицензия CRM 1 год',                      statusCode:'DONE', isWaiting:false },
      { id:'16', customerName:'ООО «ЭнергоТех»',            amount:135_000,  itemsCount:2, createdAt:'2025-11-28', comment:'UPS + NAS для серверной',                 statusCode:'DONE', isWaiting:false },
      { id:'17', customerName:'АО «СтрахКом»',              amount:255_000,  itemsCount:3, createdAt:'2025-11-28', comment:'Поддержка + хостинг + VPN на год',       statusCode:'DONE', isWaiting:false },
      { id:'19', customerName:'Кожевников А.Б.',            amount:55_000,   itemsCount:1, createdAt:'2025-12-28', comment:'SEO-оптимизация',                         statusCode:'DONE', isWaiting:false },
      { id:'20', customerName:'ООО «DataBridge»',           amount:145_000,  itemsCount:1, createdAt:'2026-01-14', comment:'Коммутатор Cisco + монтаж',               statusCode:'DONE', isWaiting:false },
    ],
  },
  {
    code: 'CANCELLED', name: 'Отменён', color: '#ef4444', icon: 'pi pi-times-circle',
    orders: [
      { id:'73', customerName:'АО «Экспресс Доставка»',    amount:480_000,  itemsCount:1, createdAt:'2025-06-28', comment:'Клиент выбрал другого подрядчика',        statusCode:'CANCELLED', isWaiting:false },
      { id:'74', customerName:'ООО «ТехноПрогресс»',       amount:95_000,   itemsCount:1, createdAt:'2025-08-28', comment:'Бюджет сокращён, проект заморожен',       statusCode:'CANCELLED', isWaiting:false },
      { id:'75', customerName:'Абрамов Г.Е.',              amount:215_000,  itemsCount:1, createdAt:'2025-09-28', comment:'Клиент обанкротился',                     statusCode:'CANCELLED', isWaiting:false },
      { id:'76', customerName:'АО «Мегастрой»',            amount:350_000,  itemsCount:1, createdAt:'2025-10-28', comment:'Не согласовали ТЗ',                       statusCode:'CANCELLED', isWaiting:false },
      { id:'77', customerName:'ИП Петренко А.С.',          amount:72_000,   itemsCount:1, createdAt:'2025-11-28', comment:'Клиент нашёл дешевле',                    statusCode:'CANCELLED', isWaiting:false },
      { id:'79', customerName:'Борисов С.А.',              amount:60_000,   itemsCount:1, createdAt:'2026-01-14', comment:'Изменились требования',                   statusCode:'CANCELLED', isWaiting:false },
      { id:'80', customerName:'Муратов Р.И.',              amount:28_000,   itemsCount:1, createdAt:'2026-02-14', comment:'Решил обойтись своими силами',             statusCode:'CANCELLED', isWaiting:false },
    ],
  },
])

// ── Computed ──────────────────────────────────────────────────────
const visibleStages = computed(() =>
  activeStage.value
    ? stages.value.filter(s => s.code === activeStage.value)
    : stages.value
)

const cancelledStage = computed(() => stages.value.find(s => s.code === 'CANCELLED'))

const totalOrders = computed(() =>
  stages.value.reduce((n, s) => n + s.orders.length, 0)
)
const totalRevenue = computed(() =>
  stages.value.filter(s => s.code !== 'CANCELLED')
    .reduce((n, s) => n + stageRevenue(s), 0)
)

function stageRevenue(stage: Stage) {
  return stage.orders.reduce((n, o) => n + o.amount, 0)
}
function stagePct(stage: Stage) {
  const first = stages.value[0]
  if (!first.orders.length) return 0
  return Math.round((stage.orders.length / first.orders.length) * 100)
}
function convPct(idx: number) {
  const nonCancelled = stages.value.filter(s => s.code !== 'CANCELLED')
  const from = nonCancelled[idx]
  const to   = nonCancelled[idx + 1]
  if (!from || !to || from.orders.length === 0) return 0
  return Math.round((to.orders.length / from.orders.length) * 100)
}

function moveTargets(fromCode: string): { code: string; name: string; color: string }[] {
  return stages.value
    .filter(s => s.code !== fromCode)
    .map(s => ({ code: s.code, name: s.name, color: s.color }))
}

// ── Drag & Drop ───────────────────────────────────────────────────
function onDragStart(e: DragEvent, order: FunnelOrder, fromCode: string) {
  dragging.value = { order, fromCode }
  e.dataTransfer?.setData('text/plain', order.id)
}

function onDrop(e: DragEvent, toCode: string) {
  if (!dragging.value) return
  const { order, fromCode } = dragging.value
  if (fromCode === toCode) { dragging.value = null; return }
  moveOrder(order, toCode)
  dragging.value = null
}

function moveOrder(order: FunnelOrder, toCode: string) {
  // Убираем из исходной колонки
  const from = stages.value.find(s => s.code === order.statusCode)
  if (from) from.orders = from.orders.filter(o => o.id !== order.id)
  // Добавляем в целевую
  const to = stages.value.find(s => s.code === toCode)
  if (to) {
    order.statusCode = toCode
    order.isWaiting  = toCode === 'WAITING'
    to.orders.unshift(order)
  }
  // В реальном приложении здесь: ordersApi.updateStatus(order.id, toCode)
}

function openOrder(order: FunnelOrder) {
  router.push('/orders')
}

// ── Хелперы ───────────────────────────────────────────────────────
function fmtMoney(v: number) {
  if (v >= 1_000_000) return `₽${(v / 1_000_000).toFixed(1)}М`
  if (v >= 1_000)     return `₽${Math.round(v / 1_000)}К`
  return `₽${v}`
}
function fmtDate(d: string) { return dayjs(d).format('D MMM') }
function truncate(s: string, n: number) { return s.length > n ? s.slice(0, n) + '…' : s }
function pluralItems(n: number) {
  if (n % 10 === 1 && n % 100 !== 11) return 'позиция'
  if ([2,3,4].includes(n % 10) && ![12,13,14].includes(n % 100)) return 'позиции'
  return 'позиций'
}

const COLORS = ['#3b82f6','#8b5cf6','#ec4899','#22c55e','#f59e0b','#06b6d4','#f97316']
function avatarColor(name: string) {
  let h = 0; for (const c of name) h = (h * 31 + c.charCodeAt(0)) & 0xffffffff
  return COLORS[Math.abs(h) % COLORS.length]
}
function initials(name: string) {
  return name.replace(/[«»ООО АО ЗАО ПАО ФГУП ИП]/g, ' ')
    .trim().split(/\s+/).slice(0, 2).map(w => w[0] ?? '').join('').toUpperCase() || '??'
}
</script>

<style scoped>
.funnel-page { display:flex; flex-direction:column; gap:20px; }

/* Заголовок */
.page-header { display:flex; align-items:center; justify-content:space-between; flex-wrap:wrap; gap:12px; }
.page-header h1 { font-size:1.375rem; font-weight:700; color:var(--text-primary); letter-spacing:-0.02em; margin-bottom:3px; }
.page-header__actions { display:flex; gap:8px; align-items:center; }

/* ── Метрики этапов ─────────────────────────────────────────────── */
.funnel-metrics {
  display:grid;
  grid-template-columns:repeat(5, 1fr);
  gap:10px;
}
.metric-card {
  background:var(--bg-surface);
  border:1px solid var(--border-subtle);
  border-top:3px solid var(--sc);
  border-radius:var(--radius-lg);
  padding:14px 16px;
  cursor:pointer;
  transition:all var(--transition-fast);
}
.metric-card:hover      { border-color:var(--sc); box-shadow:var(--shadow-md); }
.metric-card--active    { border-color:var(--sc); box-shadow:0 0 0 1px var(--sc), var(--shadow-md); }

.metric-card__header    { display:flex; align-items:center; justify-content:space-between; margin-bottom:8px; }
.metric-card__name      { font-size:0.8rem; font-weight:600; color:var(--text-secondary); }
.metric-card__icon      { font-size:14px; color:var(--sc); }
.metric-card__count     { font-size:1.5rem; font-weight:700; color:var(--text-primary); letter-spacing:-0.02em; }
.metric-card__amount    { font-size:0.8rem; color:var(--text-muted); margin-top:2px; margin-bottom:10px; }
.metric-card__bar       { height:3px; background:var(--bg-elevated); border-radius:2px; overflow:hidden; }
.metric-card__bar-fill  { height:100%; background:var(--sc); opacity:0.7; border-radius:2px; }

/* ── Kanban ──────────────────────────────────────────────────────── */
.kanban {
  display:grid;
  grid-template-columns:repeat(5, 1fr);
  gap:12px;
  align-items:start;
}
.kanban--filtered { grid-template-columns:1fr; }

.kanban-col {
  background:color-mix(in srgb, var(--sc) 5%, var(--bg-elevated));
  border:1px solid color-mix(in srgb, var(--sc) 15%, var(--border-subtle));
  border-top:2px solid var(--sc);
  border-radius:var(--radius-lg);
  padding:12px;
  min-height:200px;
}

.kanban-col__header {
  display:flex; align-items:center; justify-content:space-between;
  margin-bottom:10px; padding-bottom:10px;
  border-bottom:1px solid var(--border-subtle);
}
.kanban-col__title  { display:flex; align-items:center; gap:6px; font-size:0.875rem; font-weight:600; color:var(--text-primary); }
.kanban-col__dot    { width:8px; height:8px; border-radius:50%; background:var(--sc); }
.kanban-col__count  { font-size:0.75rem; background:color-mix(in srgb, var(--sc) 15%, transparent); color:var(--sc); padding:1px 6px; border-radius:10px; }
.kanban-col__rev    { font-size:0.75rem; color:var(--text-muted); }

.kanban-col__cards  { display:flex; flex-direction:column; gap:8px; }

/* ── Карточка заказа ─────────────────────────────────────────────── */
.order-card {
  background:var(--bg-surface);
  border:1px solid var(--border-subtle);
  border-radius:var(--radius-md);
  padding:12px;
  cursor:pointer;
  position:relative;
  overflow:hidden;
  transition:all var(--transition-fast);
}
.order-card:hover {
  border-color:var(--sc);
  box-shadow:var(--shadow-sm);
  transform:translateY(-1px);
}
.order-card--large { border-left:3px solid var(--sc); }

.order-card__amount {
  font-size:1.0625rem; font-weight:700;
  color:var(--text-primary); letter-spacing:-0.01em;
  margin-bottom:8px;
}

.order-card__customer { display:flex; align-items:center; gap:8px; margin-bottom:8px; }
.order-card__avatar {
  width:26px; height:26px; border-radius:6px; flex-shrink:0;
  display:flex; align-items:center; justify-content:center;
  font-size:0.6rem; font-weight:700; color:white;
}
.order-card__customer-info { min-width:0; }
.order-card__customer-name {
  font-size:0.8125rem; font-weight:500; color:var(--text-primary);
  display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;
}
.order-card__items { font-size:0.7rem; }

.order-card__meta {
  display:flex; align-items:center; justify-content:space-between;
  font-size:0.7rem; margin-bottom:4px;
}
.order-card__date { display:flex; align-items:center; gap:3px; }
.order-card__date .pi { font-size:10px; }
.order-card__wait-badge {
  background:rgba(139,92,246,0.12); color:#8b5cf6;
  padding:1px 6px; border-radius:8px;
  display:flex; align-items:center; gap:3px; font-size:0.7rem;
}

.order-card__comment {
  font-size:0.7rem; line-height:1.4;
  margin-top:4px; padding-top:6px;
  border-top:1px solid var(--border-subtle);
}

/* Действия при hover */
.order-card__actions {
  position:absolute; inset:0;
  background:color-mix(in srgb, var(--bg-surface) 92%, transparent);
  backdrop-filter:blur(2px);
  display:flex; flex-wrap:wrap; gap:4px; align-items:center; justify-content:center; padding:8px;
  opacity:0; transition:opacity var(--transition-fast);
}
.order-card:hover .order-card__actions { opacity:1; }

.order-card__move-btn {
  display:flex; align-items:center; gap:4px;
  font-size:0.7rem; font-weight:600; padding:4px 8px;
  border-radius:8px; border:1px solid var(--tc);
  background:color-mix(in srgb, var(--tc) 10%, transparent);
  color:var(--tc); cursor:pointer;
  transition:background var(--transition-fast);
}
.order-card__move-btn:hover { background:color-mix(in srgb, var(--tc) 20%, transparent); }

/* Пустая */
.kanban-col__empty {
  display:flex; flex-direction:column; align-items:center; gap:6px;
  padding:24px 0; color:var(--text-muted); font-size:0.8rem;
}
.kanban-col__empty .pi { font-size:1.5rem; opacity:0.35; }

/* Добавить */
.kanban-col__add {
  width:100%; padding:8px; border-radius:var(--radius-md);
  border:1px dashed var(--border-default);
  color:var(--text-muted); font-size:0.8125rem;
  display:flex; align-items:center; justify-content:center; gap:6px;
  cursor:pointer; background:transparent;
  transition:all var(--transition-fast);
}
.kanban-col__add:hover { border-color:var(--sc); color:var(--sc); background:color-mix(in srgb, var(--sc) 5%, transparent); }

/* ── Аналитика конверсии ─────────────────────────────────────────── */
.funnel-analytics { padding:20px; }
.analytics-title  { display:flex; align-items:center; gap:8px; font-size:0.9375rem; font-weight:600; color:var(--text-primary); margin-bottom:20px; }
.analytics-title .pi { font-size:14px; color:var(--accent-400); }

.conv-flow {
  display:flex; align-items:center; gap:0; flex-wrap:wrap;
  position:relative;
}

.conv-step { display:flex; align-items:center; }
.conv-step__block {
  text-align:center; padding:12px 20px;
  background:color-mix(in srgb, var(--sc) 8%, var(--bg-elevated));
  border:1px solid color-mix(in srgb, var(--sc) 20%, var(--border-subtle));
  border-radius:10px;
}
.conv-step__name  { display:block; font-size:0.75rem; color:var(--text-muted); margin-bottom:4px; }
.conv-step__count { font-size:1.375rem; font-weight:700; color:var(--text-primary); }

.conv-step__arrow {
  display:flex; flex-direction:column; align-items:center; gap:2px;
  padding:0 10px; color:var(--text-muted);
}
.conv-step__pct  { font-size:0.75rem; font-weight:700; }
.pct--good { color:var(--success); }
.pct--low  { color:var(--danger); }

.conv-cancelled {
  display:flex; align-items:center; gap:6px;
  margin-left:auto; padding:12px 16px;
  background:rgba(239,68,68,0.06); border:1px solid rgba(239,68,68,0.15);
  border-radius:10px; font-size:0.875rem;
}

/* Диалог */
.dialog-actions { display:flex; gap:8px; justify-content:flex-end; }

/* Адаптив */
@media (max-width:1200px) {
  .funnel-metrics { grid-template-columns:repeat(3,1fr); }
  .kanban         { grid-template-columns:1fr 1fr 1fr; }
}
@media (max-width:768px)  {
  .funnel-metrics { grid-template-columns:1fr 1fr; }
  .kanban         { grid-template-columns:1fr 1fr; }
  .conv-flow      { gap:8px; }
}
</style>
