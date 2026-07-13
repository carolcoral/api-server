/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'
import Welcome from '@/views/Welcome.vue'

// 路由配置
const routes = [
  {
    path: '/',
    name: 'Welcome',
    component: Welcome,
    meta: { requiresAuth: false }
  },
  {
    path: '/changelog',
    name: 'Changelog',
    component: () => import('@/views/Changelog.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: { requiresAuth: false }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/layout/DashboardLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/Home.vue'),
        meta: { requiresAuth: true, requiredPermission: 'dashboard:view' }
      },
      {
        path: '/projects',
        name: 'Projects',
        component: () => import('@/views/Projects.vue'),
        meta: { requiresAuth: true, requiredPermission: 'project:view' }
      },
      {
        path: '/projects/:projectId/apis',
        name: 'ProjectApis',
        component: () => import('@/views/ProjectApis.vue'),
        meta: { requiresAuth: true, requiredPermission: 'project:view' }
      },
      {
        path: '/apis',
        name: 'Apis',
        component: () => import('@/views/Apis.vue'),
        meta: { requiresAuth: true, requiredPermission: 'api:view' }
      },
      {
        path: '/users',
        name: 'Users',
        component: () => import('@/views/Users.vue'),
        meta: { requiresAuth: true, requiredPermission: 'user:view' }
      },
      {
        path: '/settings',
        name: 'Settings',
        component: () => import('@/views/Settings.vue'),
        meta: { requiresAuth: true, requiredPermission: 'settings:view' }
      },
      {
        path: '/statistics',
        name: 'Statistics',
        component: () => import('@/views/Statistics.vue'),
        meta: { requiresAuth: true, requiredPermission: 'statistics:view' }
      },
      {
        path: '/debug-panel',
        name: 'DebugPanel',
        component: () => import('@/views/DebugPanel.vue'),
        meta: { requiresAuth: true, requiredPermission: 'debug-panel:view' }
      },
      {
        path: '/profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { requiresAuth: true }
      },
      {
        path: '/code-templates',
        name: 'CodeTemplates',
        component: () => import('@/views/CodeTemplates.vue'),
        meta: { requiresAuth: true, requiredPermission: 'code-template:view' }
      },
      {
        path: '/guide',
        name: 'Guide',
        component: () => import('@/views/Guide.vue'),
        meta: { requiresAuth: false }
      },
      {
        path: '/email-templates',
        name: 'EmailTemplates',
        component: () => import('@/views/EmailTemplates.vue'),
        meta: { requiresAuth: true, requiredPermission: 'email-template:view' }
      },
      {
        path: '/ai-settings',
        name: 'AiSettings',
        component: () => import('@/views/AiSettings.vue'),
        meta: { requiresAuth: true, requiredPermission: 'ai-settings:view' }
      },
      {
        path: '/ai-chat',
        name: 'AiChat',
        component: () => import('@/views/AiChat.vue'),
        meta: { requiresAuth: true, requiredPermission: 'ai-chat:view' }
      },
      {
        path: '/roles',
        name: 'Roles',
        component: () => import('@/views/Roles.vue'),
        meta: { requiresAuth: true, requiredPermission: 'role:view' }
      },
      {
        path: '/permissions',
        name: 'Permissions',
        component: () => import('@/views/Permissions.vue'),
        meta: { requiresAuth: true, requiredPermission: 'permission:view' }
      },
      {
        path: '/ops-monitor',
        name: 'OpsMonitor',
        component: () => import('@/views/OpsMonitor.vue'),
        meta: { requiresAuth: true, requiredPermission: 'ops:view' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  
  // 获取用户有权限访问的第一个路由，作为重定向兜底
  const getFallbackRoute = () => {
    if (userStore.hasPermission('dashboard:view')) return '/dashboard'
    if (userStore.hasPermission('project:view')) return '/projects'
    if (userStore.hasPermission('api:view')) return '/apis'
    if (userStore.hasPermission('code-template:view')) return '/code-templates'
    if (userStore.hasPermission('statistics:view')) return '/statistics'
    if (userStore.hasPermission('debug-panel:view')) return '/debug-panel'
    if (userStore.hasPermission('user:view')) return '/users'
    if (userStore.hasPermission('role:view')) return '/roles'
    if (userStore.hasPermission('permission:view')) return '/permissions'
    if (userStore.hasPermission('email-template:view')) return '/email-templates'
    if (userStore.hasPermission('ai-settings:view')) return '/ai-settings'
    if (userStore.hasPermission('ops:view')) return '/ops-monitor'
    if (userStore.hasPermission('settings:view')) return '/settings'
    if (userStore.hasPermission('ai-chat:view')) return '/ai-chat'
    // 终极兜底：个人信息页无需权限
    return '/profile'
  }
  
  // 检查是否需要认证
  if (to.meta.requiresAuth) {
    if (!userStore.isLoggedIn) {
      next('/login')
      return
    }
    
    // 检查是否需要管理员权限（hasPermission 内部会检查 isAdmin，确保管理员始终通过）
    if (to.meta.requiresAdmin && !userStore.hasPermission(to.meta.requiredPermission || 'dashboard:view')) {
      next(getFallbackRoute())
      return
    }

    // 检查是否需要特定权限
    if (to.meta.requiredPermission && !userStore.hasPermission(to.meta.requiredPermission)) {
      next(getFallbackRoute())
      return
    }
  }
  
  // 如果已登录，不允许访问登录页和注册页
  if ((to.path === '/login' || to.path === '/register') && userStore.isLoggedIn) {
    next(getFallbackRoute())
    return
  }
  
  next()
})

export default router
