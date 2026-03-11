<template>
  <div class="modules animate-fade-in">

    <p class="modules-hint text-muted">
      Включайте только нужные модули — неиспользуемые разделы не будут отображаться у сотрудников.
    </p>

    <div class="modules-grid">
      <div
          v-for="mod in modules"
          :key="mod.code"
          class="module-card surface-card"
          :class="{
          'module-card--active': mod.enabled,
          'module-card--system': mod.isSystem,
        }"
      >
        <div class="module-card__header">
          <div class="module-icon" :style="{ '--c': mod.color }">
            <i :class="mod.icon" />
          </div>
          <ToggleSwitch
              v-model="mod.enabled"
              :disabled="mod.isSystem"
              @change="onToggle(mod)"
          />
        </div>

        <div class="module-card__body">
          <h3>{{ mod.name }}</h3>
          <p class="text-muted">{{ mod.description }}</p>
        </div>

        <div class="module-card__footer">
          <Tag v-if="mod.isSystem" value="Системный" severity="secondary" />
          <div v-else-if="mod.enabled" class="module-status module-status--on">
            <span class="status-dot" />Активен
          </div>
          <div v-else class="module-status module-status--off">
            <span class="status-dot" />Отключён
          </div>
          <span class="module-users text-muted" v-if="mod.activeUsers">
            <i class="pi pi-users" />{{ mod.activeUsers }} пользоват.
          </span>
        </div>
      </div>
    </div>


  </div>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import Tag from 'primevue/tag'
import ToggleSwitch from 'primevue/toggleswitch'
import { useConfirm } from 'primevue/useconfirm'
import { useAppToast } from '@/composables/useAppToast'

const confirm = useConfirm()
const toast   = useAppToast()

interface Module {
  code: string; name: string; description: string
  icon: string; color: string; enabled: boolean
  isSystem: boolean; activeUsers?: number
}

const modules = reactive<Module[]>([
  { code: 'CORE',      name: 'Ядро системы',   description: 'Авторизация, пользователи, роли и базовые настройки. Не может быть отключено.',         icon: 'pi pi-cog',           color: '#6b7280', enabled: true,  isSystem: true,  activeUsers: 12 },
  { code: 'CUSTOMERS', name: 'Клиенты',         description: 'Карточки клиентов, история взаимодействий, поиск по ФИО, ИНН, телефону.',               icon: 'pi pi-users',         color: '#3b82f6', enabled: true,  isSystem: false, activeUsers: 10 },
  { code: 'TASKS',     name: 'Задачи',           description: 'Список задач, планировщик, календарь, назначение исполнителей, комментарии.',           icon: 'pi pi-calendar',      color: '#8b5cf6', enabled: true,  isSystem: false, activeUsers: 8  },
  { code: 'ORDERS',    name: 'Заказы и товары',  description: 'Управление заказами, каталог товаров и услуг, история продаж, статистика выручки.',     icon: 'pi pi-shopping-cart', color: '#22c55e', enabled: true,  isSystem: false, activeUsers: 6  },
  { code: 'ANALYTICS', name: 'Аналитика',        description: 'Дашборды, воронка продаж, отчёты по клиентам и сотрудникам, экспорт в Excel.',         icon: 'pi pi-chart-line',    color: '#f59e0b', enabled: false, isSystem: false },
  { code: 'TELEPHONY', name: 'Телефония',        description: 'Интеграция с АТС, автодозвон, запись разговоров, всплывающая карточка при звонке.',     icon: 'pi pi-phone',         color: '#06b6d4', enabled: false, isSystem: false },
  { code: 'EMAIL',     name: 'Email-маркетинг',  description: 'Рассылки, шаблоны писем, автоматические триггерные сообщения по событиям.',            icon: 'pi pi-envelope',      color: '#ec4899', enabled: false, isSystem: false },
  { code: 'DOCUMENTS', name: 'Документы',        description: 'Генерация договоров и счетов по шаблонам, электронная подпись, хранилище файлов.',     icon: 'pi pi-file-pdf',      color: '#f97316', enabled: false, isSystem: false },
])

function onToggle(mod: Module) {
  if (mod.isSystem) return
  if (!mod.enabled) {
    confirm.require({
      message: `Отключить модуль «${mod.name}»? Сотрудники потеряют доступ к этому разделу.`,
      header: 'Отключение модуля',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Отключить',
      rejectLabel: 'Отмена',
      accept: () => {
        toast.success(`Модуль «${mod.name}» отключён`)
      },
      reject: () => {
        mod.enabled = true // откатываем
      }
    })
  } else {
    toast.success(`Модуль «${mod.name}» включён`)
  }
}
</script>

<style scoped>
.modules { display: flex; flex-direction: column; gap: 16px; }
.modules-hint { font-size: 0.875rem; padding: 10px 14px; background: var(--bg-surface); border: 1px solid var(--border-subtle); border-radius: var(--radius-md); border-left: 3px solid var(--accent-500); }

.modules-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.module-card {
  padding: 20px; display: flex; flex-direction: column; gap: 14px;
  border-radius: var(--radius-lg);
  transition: all var(--transition-fast);
  border: 1px solid var(--border-subtle);
}
.module-card--active { border-color: color-mix(in srgb, var(--border-subtle) 60%, rgba(59,130,246,0.3) 40%); }
.module-card--system { opacity: 0.8; }
.module-card:hover { box-shadow: var(--shadow-sm); }

.module-card__header { display: flex; align-items: center; justify-content: space-between; }
.module-icon {
  width: 44px; height: 44px; border-radius: 12px;
  background: color-mix(in srgb, var(--c) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--c) 20%, transparent);
  display: flex; align-items: center; justify-content: center;
  color: var(--c); font-size: 1.125rem;
}

.module-card__body h3 { font-size: 1rem; font-weight: 600; color: var(--text-primary); margin-bottom: 5px; }
.module-card__body p { font-size: 0.8125rem; line-height: 1.5; }

.module-card__footer { display: flex; align-items: center; justify-content: space-between; padding-top: 10px; border-top: 1px solid var(--border-subtle); }
.module-status { display: flex; align-items: center; gap: 6px; font-size: 0.8125rem; }
.module-status--on  { color: #22c55e; }
.module-status--off { color: var(--text-muted); }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; }
.module-users { display: flex; align-items: center; gap: 5px; font-size: 0.8125rem; }
.module-users .pi { font-size: 11px; }

@media (max-width: 768px) { .modules-grid { grid-template-columns: 1fr; } }
</style>
