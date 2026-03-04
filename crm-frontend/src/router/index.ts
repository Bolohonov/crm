import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/auth',
      component: () => import('@/layouts/AuthLayout.vue'),
      meta: { guest: true },
      children: [
        { path: 'login',    name: 'login',    component: () => import('@/views/auth/LoginView.vue') },
        { path: 'register', name: 'register', component: () => import('@/views/auth/RegisterView.vue') },
        { path: 'verify',   name: 'verify',   component: () => import('@/views/auth/VerifyView.vue') }
      ]
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        { path: '', redirect: '/dashboard' },
        {
          path: 'dashboard', name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue')
        },
        {
          path: 'customers', name: 'customers',
          component: () => import('@/views/customers/CustomersView.vue'),
          meta: { module: 'CUSTOMERS', permission: 'CUSTOMER_VIEW' }
        },
        {
          path: 'customers/:id', name: 'customer-detail',
          component: () => import('@/views/customers/CustomerDetailView.vue'),
          meta: { module: 'CUSTOMERS', permission: 'CUSTOMER_VIEW' }
        },
        {
          path: 'tasks', name: 'tasks',
          component: () => import('@/views/tasks/TasksView.vue'),
          meta: { module: 'TASKS', permission: 'TASK_VIEW' }
        },
        {
          path: 'orders', name: 'orders',
          component: () => import('@/views/orders/OrdersView.vue'),
          meta: { module: 'ORDERS', permission: 'ORDER_VIEW' }
        },
        {
          path: 'products', name: 'products',
          component: () => import('@/views/orders/ProductsView.vue'),
          meta: { module: 'ORDERS', permission: 'PRODUCT_VIEW' }
        },
        {
          path: 'funnel', name: 'funnel',
          component: () => import('@/views/orders/SalesFunnelView.vue'),
          meta: { module: 'ORDERS', permission: 'ORDER_VIEW' }
        },
        // ── Admin section — вложенные маршруты ──────────────────────
        {
          path: 'admin',
          component: () => import('@/views/admin/AdminView.vue'),
          meta: { adminOnly: true },
          redirect: '/admin/users',
          children: [
            {
              path: 'users', name: 'admin-users',
              component: () => import('@/views/admin/UsersView.vue'),
              meta: { adminOnly: true }
            },
            {
              path: 'roles', name: 'admin-roles',
              component: () => import('@/views/admin/RolesView.vue'),
              meta: { adminOnly: true }
            },
            {
              path: 'tenant', name: 'admin-tenant',
              component: () => import('@/views/admin/TenantView.vue'),
              meta: { adminOnly: true }
            },
            {
              path: 'modules', name: 'admin-modules',
              component: () => import('@/views/admin/ModulesView.vue'),
              meta: { adminOnly: true }
            }
          ]
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  if (!auth.initialized) await auth.init()
  if (to.meta.guest && auth.isAuthenticated) return '/dashboard'
  if (to.meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.adminOnly && !auth.isAdmin) return '/dashboard'
  if (to.meta.module && !auth.hasModule(to.meta.module as string)) return '/dashboard'
  if (to.meta.permission && !auth.can(to.meta.permission as string)) return '/dashboard'
  return true
})

export default router
