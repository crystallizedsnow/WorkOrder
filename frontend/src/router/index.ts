import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AppLayout from '@/layouts/AppLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    {
      path: '/', component: AppLayout, redirect: '/dashboard',
      children: [
        { path: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'work-orders', component: () => import('@/views/work-orders/WorkOrderListView.vue') },
        { path: 'work-orders/new', component: () => import('@/views/work-orders/WorkOrderCreateView.vue') },
        { path: 'work-orders/:id', component: () => import('@/views/work-orders/WorkOrderDetailView.vue') },
        { path: 'flows', component: () => import('@/views/flows/FlowListView.vue') },
        { path: 'flows/new', component: () => import('@/views/flows/FlowEditorView.vue') },
        { path: 'flows/:id/edit', component: () => import('@/views/flows/FlowEditorView.vue') },
        { path: 'organization', component: () => import('@/views/OrganizationView.vue') },
        { path: 'messages', component: () => import('@/views/MessagesView.vue') },
        { path: 'assistant', component: () => import('@/views/assistant/AssistantView.vue') },
        { path: 'profile', component: () => import('@/views/ProfileView.vue') },
        { path: 'admin/staff', component: () => import('@/views/admin/StaffAdminView.vue'), meta: { role: 'admin' } },
        { path: 'admin/organization', component: () => import('@/views/admin/OrganizationAdminView.vue'), meta: { role: 'admin' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.initialized) await auth.restoreSession()
  if (to.meta.public) return auth.isAuthenticated ? '/dashboard' : true
  if (!auth.isAuthenticated) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.meta.role === 'admin' && !auth.isAdmin) return '/dashboard'
  return true
})

export default router
