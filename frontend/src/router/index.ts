import { createRouter, createWebHistory } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
        },
        {
          path: 'activities/:activityId',
          name: 'activity-detail',
          component: () => import('@/views/ActivityDetailView.vue'),
        },
        {
          path: 'me',
          name: 'profile',
          component: () => import('@/views/ProfileView.vue'),
        },
        {
          path: 'records',
          name: 'records',
          component: () => import('@/views/RecordsView.vue'),
        },
        {
          path: 'my-activities',
          name: 'my-activities',
          component: () => import('@/views/MyActivitiesView.vue'),
        },
        {
          path: 'my-activities/new',
          name: 'activity-create',
          component: () => import('@/views/ActivityEditorView.vue'),
        },
        {
          path: 'my-activities/:activityId/edit',
          name: 'activity-edit',
          component: () => import('@/views/ActivityEditorView.vue'),
        },
        {
          path: 'my-activities/:activityId',
          name: 'activity-manage',
          component: () => import('@/views/ActivityManageView.vue'),
        },
        {
          path: 'admin',
          name: 'admin',
          component: () => import('@/views/AdminView.vue'),
          meta: { requiresAdmin: true },
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/signup',
      name: 'signup',
      component: () => import('@/views/SignupView.vue'),
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/ForgotPasswordView.vue'),
    },
  ],
})

router.beforeEach((to) => {
  const authStore = useAuthStore()

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.meta.requiresAdmin && !authStore.isAdmin) {
    return { name: 'home' }
  }

  if (['login', 'signup', 'forgot-password'].includes(String(to.name)) && authStore.isLoggedIn) {
    return { name: 'home' }
  }
})

export default router
