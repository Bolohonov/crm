<template>
  <div class="products animate-fade-in">

    <!-- Header -->
    <div class="page-header">
      <div>
        <h1>Товары и услуги</h1>
        <p class="text-muted">{{ pageResponse?.totalElements ?? 0 }} позиций</p>
      </div>
      <div class="page-header__actions">
        <div class="view-toggle">
          <Button :class="{ active: viewMode === 'grid' }" icon="pi pi-th-large"
                  text rounded size="small" @click="viewMode = 'grid'" v-tooltip="'Сетка'" />
          <Button :class="{ active: viewMode === 'list' }" icon="pi pi-list"
                  text rounded size="small" @click="viewMode = 'list'" v-tooltip="'Список'" />
        </div>
        <Button v-if="can('PRODUCT_MANAGE')" icon="pi pi-plus" label="Добавить товар"
                @click="openCreate" />
      </div>
    </div>

    <!-- Toolbar -->
    <div class="products__toolbar surface-card">
      <IconField class="toolbar__search">
        <InputIcon class="pi pi-search" />
        <InputText v-model="searchQuery" placeholder="Название или артикул..."
                   @input="onSearchInput" fluid />
      </IconField>
      <div class="toolbar__filters">
        <Button
            :class="['filter-chip', { active: !onlyActive }]"
            label="Все"
            text size="small"
            @click="onlyActive = false; loadProducts()"
        />
        <Button
            :class="['filter-chip', { active: onlyActive }]"
            label="Активные"
            text size="small"
            @click="onlyActive = true; loadProducts()"
        />
      </div>
    </div>

    <!-- Grid view -->
    <div v-if="viewMode === 'grid'" class="products__grid">
      <div v-if="loading" class="products__grid">
        <Skeleton v-for="i in 8" :key="i" height="180px" border-radius="14px" />
      </div>
      <template v-else>
        <div v-for="product in products" :key="product.id"
             class="product-card"
             :class="{ 'product-card--inactive': !product.isActive }"
        >
          <div class="product-card__header">
            <div class="product-card__icon">
              <i class="pi pi-box" />
            </div>
            <div class="product-card__badges">
              <Tag v-if="!product.isActive" value="Неактивен" severity="secondary" />
              <span v-if="product.sku" class="sku-badge font-mono">{{ product.sku }}</span>
            </div>
          </div>
          <div class="product-card__body">
            <h3>{{ product.name }}</h3>
            <p v-if="product.description" class="text-muted">{{ truncate(product.description, 80) }}</p>
          </div>
          <div class="product-card__footer">
            <span class="product-card__price font-mono">
              {{ formatMoney(product.price) }}
              <span class="text-muted" style="font-size:0.75rem"> / {{ product.unit || 'шт' }}</span>
            </span>
            <div class="product-card__actions" v-if="can('PRODUCT_MANAGE')">
              <Button icon="pi pi-pencil" text rounded size="small" @click="openEdit(product)" />
              <Button icon="pi pi-ellipsis-v" text rounded size="small"
                      @click="(e) => openMenu(e, product)" />
            </div>
          </div>
        </div>

        <div v-if="!loading && products.length === 0" class="empty-state surface-card">
          <i class="pi pi-box" />
          <p>Товаров пока нет</p>
          <Button v-if="can('PRODUCT_MANAGE')" label="Добавить первый товар"
                  text size="small" @click="openCreate" />
        </div>
      </template>
    </div>

    <!-- List view -->
    <div v-else class="products__table surface-card">
      <DataTable :value="products" :loading="loading" row-hover>
        <Column field="sku" header="Артикул" style="width:120px">
          <template #body="{ data }">
            <span class="font-mono text-muted" style="font-size:0.8125rem">{{ data.sku || '—' }}</span>
          </template>
        </Column>
        <Column field="name" header="Название">
          <template #body="{ data }">
            <div>
              <div style="font-weight:500;color:var(--text-primary)">{{ data.name }}</div>
              <div v-if="data.description" class="text-muted" style="font-size:0.8rem">
                {{ truncate(data.description, 60) }}
              </div>
            </div>
          </template>
        </Column>
        <Column field="price" header="Цена" style="width:130px">
          <template #body="{ data }">
            <span class="font-mono" style="font-weight:600">{{ formatMoney(data.price) }}</span>
          </template>
        </Column>
        <Column field="unit" header="Ед." style="width:70px">
          <template #body="{ data }"><span class="text-muted">{{ data.unit || 'шт' }}</span></template>
        </Column>
        <Column header="Статус" style="width:110px">
          <template #body="{ data }">
            <Tag :value="data.isActive ? 'Активен' : 'Неактивен'"
                 :severity="data.isActive ? 'success' : 'secondary'" />
          </template>
        </Column>
        <Column style="width:52px" v-if="can('PRODUCT_MANAGE')">
          <template #body="{ data }">
            <Button icon="pi pi-ellipsis-v" text rounded size="small"
                    @click="(e) => openMenu(e, data)" />
          </template>
        </Column>
        <template #empty>
          <div class="table-empty">
            <i class="pi pi-box" />
            <p>Товаров не найдено</p>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- Pagination -->
    <Paginator v-if="(pageResponse?.totalPages ?? 0) > 1"
               :rows="pageSize" :total-records="pageResponse?.totalElements ?? 0"
               :first="currentPage * pageSize" @page="onPageChange"
               :rows-per-page-options="[12,24,48]" class="surface-card" />

    <!-- Context menu -->
    <Menu ref="rowMenu" :model="menuItems" popup />

    <!-- Form dialog -->
    <ProductFormDialog
        v-model:visible="showFormDialog"
        :product="editProduct"
        @saved="onSaved"
    />


  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import IconField from 'primevue/iconfield'
import InputIcon from 'primevue/inputicon'
import Tag from 'primevue/tag'
import Skeleton from 'primevue/skeleton'
import Paginator from 'primevue/paginator'
import Menu from 'primevue/menu'
import { useConfirm } from 'primevue/useconfirm'
import { usePermission } from '@/composables/usePermission'
import { useAppToast } from '@/composables/useAppToast'
import { productsApi, type ProductResponse, type ProductPageResponse } from '@/api/products'
import ProductFormDialog from '@/components/products/ProductFormDialog.vue'

const confirm = useConfirm()
const { can } = usePermission()
const toast   = useAppToast()

const products     = ref<ProductResponse[]>([])
const pageResponse = ref<ProductPageResponse | null>(null)
const loading      = ref(false)
const viewMode     = ref<'grid'|'list'>('grid')
const searchQuery  = ref('')
const onlyActive   = ref(true)
const currentPage  = ref(0)
const pageSize     = ref(24)
const showFormDialog = ref(false)
const editProduct  = ref<ProductResponse | null>(null)
const activeRow    = ref<ProductResponse | null>(null)
const rowMenu      = ref()

async function loadProducts() {
  loading.value = true
  try {
    const { data: res } = await productsApi.list({
      query: searchQuery.value || undefined,
      onlyActive: onlyActive.value,
      page: currentPage.value, size: pageSize.value,
    })
    if (res.data) { products.value = res.data.content; pageResponse.value = res.data }
  } catch { toast.error('Не удалось загрузить товары') }
  finally { loading.value = false }
}

let timer: ReturnType<typeof setTimeout>
function onSearchInput() { clearTimeout(timer); timer = setTimeout(() => { currentPage.value = 0; loadProducts() }, 350) }
function onPageChange(e: any) { currentPage.value = e.page; pageSize.value = e.rows; loadProducts() }

function openCreate() { editProduct.value = null; showFormDialog.value = true }
function openEdit(p: ProductResponse) { editProduct.value = p; showFormDialog.value = true }

const menuItems = computed(() => [
  { label: 'Редактировать', icon: 'pi pi-pencil', command: () => openEdit(activeRow.value!) },
  {
    label: activeRow.value?.isActive ? 'Деактивировать' : 'Активировать',
    icon: activeRow.value?.isActive ? 'pi pi-eye-slash' : 'pi pi-eye',
    command: () => toggleActive(activeRow.value!),
  },
  { separator: true },
  { label: 'Удалить', icon: 'pi pi-trash', command: () => confirmDelete(activeRow.value!) },
])

function openMenu(event: MouseEvent, row: ProductResponse) { activeRow.value = row; rowMenu.value.toggle(event) }

async function toggleActive(p: ProductResponse) {
  try {
    await productsApi.setActive(p.id, !p.isActive)
    loadProducts()
    toast.success(p.isActive ? 'Товар деактивирован' : 'Товар активирован')
  } catch { toast.error('Не удалось изменить статус') }
}

function confirmDelete(p: ProductResponse) {
  confirm.require({
    message: `Удалить товар «${p.name}»?`,
    header: 'Удаление товара',
    icon: 'pi pi-exclamation-triangle',
    acceptClass: 'p-button-danger',
    acceptLabel: 'Удалить', rejectLabel: 'Отмена',
    accept: async () => {
      try { await productsApi.delete(p.id); loadProducts(); toast.success('Удалено') }
      catch { toast.error('Не удалось удалить') }
    }
  })
}

function onSaved() { showFormDialog.value = false; loadProducts() }

function formatMoney(v: number) {
  return new Intl.NumberFormat('ru-RU', { style:'currency', currency:'RUB', maximumFractionDigits:0 }).format(v)
}
function truncate(s: string, n: number) { return s.length > n ? s.slice(0, n) + '…' : s }

onMounted(loadProducts)
</script>

<style scoped>
.products { display: flex; flex-direction: column; gap: 16px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; }
.page-header h1 { font-size: 1.375rem; font-weight: 700; color: var(--text-primary); letter-spacing: -0.02em; margin-bottom: 4px; }
.page-header__actions { display: flex; align-items: center; gap: 10px; }

.view-toggle { display: flex; gap: 2px; padding: 3px; background: var(--bg-elevated); border-radius: var(--radius-md); border: 1px solid var(--border-subtle); }
.view-toggle .p-button.active { background: var(--accent-500) !important; color: white !important; }

.products__toolbar { padding: 12px 16px; display: flex; align-items: center; gap: 12px; }
.toolbar__search { flex: 1; }
.toolbar__filters { display: flex; gap: 6px; }
.filter-chip { border: 1px solid var(--border-default) !important; border-radius: 20px !important; padding: 4px 14px !important; font-size: 0.8125rem !important; }
.filter-chip.active { background: rgba(59,130,246,0.12) !important; border-color: var(--accent-500) !important; color: var(--accent-400) !important; }

/* Grid */
.products__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.product-card {
  background: var(--bg-surface); border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg); padding: 18px;
  display: flex; flex-direction: column; gap: 12px;
  transition: all var(--transition-fast);
}
.product-card:hover { border-color: var(--border-default); box-shadow: var(--shadow-md); transform: translateY(-2px); }
.product-card--inactive { opacity: 0.55; }

.product-card__header { display: flex; align-items: flex-start; justify-content: space-between; }
.product-card__icon { width: 40px; height: 40px; border-radius: 10px; background: rgba(59,130,246,0.1); border: 1px solid rgba(59,130,246,0.15); display: flex; align-items: center; justify-content: center; color: var(--accent-400); font-size: 16px; }
.product-card__badges { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.sku-badge { font-size: 0.6875rem; color: var(--text-muted); background: var(--bg-elevated); padding: 2px 6px; border-radius: 4px; border: 1px solid var(--border-subtle); }

.product-card__body { flex: 1; }
.product-card__body h3 { font-size: 0.9375rem; font-weight: 600; color: var(--text-primary); margin-bottom: 4px; line-height: 1.3; }
.product-card__body p { font-size: 0.8125rem; line-height: 1.4; }

.product-card__footer { display: flex; align-items: center; justify-content: space-between; padding-top: 8px; border-top: 1px solid var(--border-subtle); }
.product-card__price { font-size: 1rem; font-weight: 700; color: var(--text-primary); }
.product-card__actions { display: flex; gap: 2px; }

/* List overrides */
.products__table { overflow: hidden; }
:deep(.p-datatable-tbody > tr) { background: transparent !important; border-bottom: 1px solid var(--border-subtle); transition: background 120ms; }
:deep(.p-datatable-tbody > tr:hover) { background: var(--bg-hover) !important; }
:deep(.p-datatable-thead > tr > th) { background: var(--bg-elevated) !important; color: var(--text-muted) !important; font-size: 0.8125rem; font-weight: 600; border-bottom: 1px solid var(--border-subtle); padding: 10px 16px !important; }
:deep(.p-datatable-tbody > tr > td) { padding: 11px 16px !important; border: none !important; }

.empty-state { grid-column: 1/-1; padding: 56px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; color: var(--text-muted); }
.empty-state .pi { font-size: 2.5rem; }
.table-empty { padding: 48px; text-align: center; display: flex; flex-direction: column; align-items: center; gap: 10px; color: var(--text-muted); }
.table-empty .pi { font-size: 2.5rem; }
</style>
