<!--
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
-->

<template>
  <div class="dashboard-layout">
    <!-- 侧边栏 -->
    <el-aside :width="collapsed ? '64px' : '220px'" class="sidebar" :class="{ collapsed }">
      <!-- 动态线条背景 -->
      <canvas ref="sidebarCanvas" class="sidebar-canvas"></canvas>
      <div class="logo">
        <svg class="sidebar-logo-icon" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
          <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/>
          <polyline points="3.27 6.96 12 12.01 20.73 6.96"/>
          <line x1="12" y1="22.08" x2="12" y2="12"/>
        </svg>
        <h2 v-show="!collapsed">Mock Server</h2>
      </div>
      <el-menu
        :default-active="activeMenu"
        :default-openeds="defaultOpeneds"
        :collapse="collapsed"
        class="el-menu-vertical"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <!-- 仪表盘 - 一级菜单（根据权限显示） -->
        <el-menu-item index="/dashboard" v-if="userStore.hasPermission('dashboard:view')">
          <el-icon><HomeFilled /></el-icon>
          <span>{{ $t('nav.home') }}</span>
        </el-menu-item>

        <!-- 业务管理 - 可折叠分组（根据权限显示） -->
        <el-sub-menu index="sub-business" v-if="userStore.hasAnyPermission(['project:view', 'api:view', 'code-template:view'])">
          <template #title>
            <el-icon><Monitor /></el-icon>
            <span>{{ $t('nav.businessManagement') }}</span>
          </template>
          <el-menu-item index="/projects" v-if="userStore.hasPermission('project:view')">
            <el-icon><Folder /></el-icon>
            <span>{{ $t('nav.projects') }}</span>
          </el-menu-item>
          <el-menu-item index="/apis" v-if="userStore.hasPermission('api:view')">
            <el-icon><Connection /></el-icon>
            <span>{{ $t('nav.apis') }}</span>
          </el-menu-item>
          <el-menu-item index="/code-templates" v-if="userStore.hasPermission('code-template:view')">
            <el-icon><Document /></el-icon>
            <span>{{ $t('nav.codeTemplates') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- AI 对话 - 一级菜单（根据权限显示） -->
        <el-menu-item index="/ai-chat" v-if="userStore.hasPermission('ai-chat:view')">
          <el-icon><ChatDotSquare /></el-icon>
          <span>{{ $t('nav.aiChat') }}</span>
        </el-menu-item>

        <!-- 数据统计 - 一级菜单（根据权限显示） -->
        <el-menu-item index="/statistics" v-if="userStore.hasPermission('statistics:view')">
          <el-icon><DataAnalysis /></el-icon>
          <span>{{ $t('nav.statistics') }}</span>
        </el-menu-item>

        <!-- 权限管理 - 根据权限显示 -->
        <el-sub-menu index="sub-permission" v-if="userStore.hasAnyPermission(['user:view', 'role:view', 'permission:view'])">
          <template #title>
            <el-icon><Lock /></el-icon>
            <span>{{ $t('nav.permissionManagement') }}</span>
          </template>
          <el-menu-item index="/users" v-if="userStore.hasPermission('user:view')">
            <el-icon><User /></el-icon>
            <span>{{ $t('nav.userManagement') }}</span>
          </el-menu-item>
          <el-menu-item index="/roles" v-if="userStore.hasPermission('role:view')">
            <el-icon><Avatar /></el-icon>
            <span>{{ $t('permission.roleManagement') }}</span>
          </el-menu-item>
          <el-menu-item index="/permissions" v-if="userStore.hasPermission('permission:view')">
            <el-icon><Key /></el-icon>
            <span>{{ $t('permission.permissionManagement') }}</span>
          </el-menu-item>
        </el-sub-menu>

        <!-- 系统管理 - 根据权限显示 -->
        <el-sub-menu index="sub-system" v-if="userStore.hasAnyPermission(['email-template:view', 'ai-settings:view', 'settings:view'])">
          <template #title>
            <el-icon><Tools /></el-icon>
            <span>{{ $t('nav.systemManagement') }}</span>
          </template>
          <el-menu-item index="/email-templates" v-if="userStore.hasPermission('email-template:view')">
            <el-icon><Message /></el-icon>
            <span>{{ $t('nav.emailTemplates') }}</span>
          </el-menu-item>
          <el-menu-item index="/ai-settings" v-if="userStore.hasPermission('ai-settings:view')">
            <el-icon><Cpu /></el-icon>
            <span>{{ $t('nav.aiSettings') }}</span>
          </el-menu-item>
          <el-menu-item index="/settings" v-if="userStore.hasPermission('settings:view')">
            <el-icon><Setting /></el-icon>
            <span>{{ $t('nav.settings') }}</span>
          </el-menu-item>
        </el-sub-menu>
      </el-menu>
      <!-- 展开/收缩切换按钮 -->
      <div class="sidebar-toggle" @click="collapsed = !collapsed">
        <el-icon :size="18">
          <Fold v-if="!collapsed" />
          <Expand v-else />
        </el-icon>
      </div>
      <!-- 版本号 -->
      <div class="sidebar-version" v-show="!collapsed">
        <span class="version-text">{{ appVersion }}</span>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-container class="main-container">
      <!-- 头部 -->
      <el-header class="header">
        <div class="header-left">
          <h3>{{ route.meta.title || 'Mock Server' }}</h3>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" :src="avatarSrc" @error="handleAvatarError">
                <el-icon><UserFilled /></el-icon>
              </el-avatar>
              <span class="username">{{ userStore.username }}</span>
              <span class="role-badge" :style="getRoleTagStyleByRoleName(currentRoleName)">{{ currentRoleName }}</span>
              <el-icon class="el-icon--right"><arrow-down /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">{{ $t('user.profile') }}</el-dropdown-item>
                <el-dropdown-item command="password">{{ $t('user.changePassword') }}</el-dropdown-item>
                <el-dropdown-item divided command="logout">{{ $t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="main-content" :class="{ 'main-content-chat': route.path === '/ai-chat' }">
        <router-view />
      </el-main>

      <!-- 页脚 -->
      <el-footer v-if="hasFooterContent" class="footer" height="auto">
        <div class="footer-content">
          <div v-if="footerConfig.enableCopyright" class="footer-copyright">
            <span>{{ footerConfig.copyright || '&copy; 2026 carolcoral，Author: LXW' }}</span>
          </div>
          <div class="footer-links">
            <!-- 友情链接 -->
            <a v-if="footerConfig.enableFriendLink && footerConfig.friendLinkUrl" :href="footerConfig.friendLinkUrl" target="_blank" rel="noopener noreferrer" :title="footerConfig.friendLinkTitle || $t('footer.friendLink')" class="footer-link">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#E74C3C" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>
                <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>
              </svg>
            </a>
            <!-- 博客 -->
            <a v-if="footerConfig.enableBlog && footerConfig.blogUrl" :href="footerConfig.blogUrl" target="_blank" rel="noopener noreferrer" :title="footerConfig.blogTitle || $t('footer.blog')" class="footer-link">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#E67E22" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                <line x1="8" y1="7" x2="16" y2="7"/>
                <line x1="8" y1="11" x2="14" y2="11"/>
              </svg>
            </a>
            <!-- GitHub -->
            <a v-if="footerConfig.enableGithub && footerConfig.githubUrl" :href="footerConfig.githubUrl" target="_blank" rel="noopener noreferrer" :title="footerConfig.githubTitle || $t('footer.github')" class="footer-link">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="#24292E">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
            </a>
            <!-- 邮箱 -->
            <a v-if="footerConfig.enableEmail && footerConfig.emailAddress" :href="'mailto:' + footerConfig.emailAddress" :title="footerConfig.emailTitle || $t('footer.email')" class="footer-link">
              <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#3498DB" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="2" y="4" width="20" height="16" rx="2"/>
                <path d="M22 4L12 13 2 4"/>
              </svg>
            </a>
            <!-- 自定义链接 -->
            <a v-if="footerConfig.enableCustomLinks" v-for="(link, idx) in footerConfig.customLinks" :key="'custom-'+idx" :href="link.url" target="_blank" rel="noopener noreferrer" :title="link.title" class="footer-link">
              <span v-if="link.svgIcon" v-html="link.svgIcon" class="footer-link-custom-svg"></span>
              <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="#909399" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/>
                <line x1="2" y1="12" x2="22" y2="12"/>
                <path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/>
              </svg>
            </a>
          </div>
        </div>
      </el-footer>
    </el-container>

    <!-- 系统访问地址同步提示弹窗（仅管理员） -->
    <el-dialog v-model="siteUrlDialogVisible" :title="$t('settings.siteUrlSyncTitle')" width="520px" :close-on-click-modal="false" :close-on-press-escape="false">
      <div style="line-height: 1.8; font-size: 14px; color: #606266;">
        <p>{{ $t('settings.siteUrlSyncMessage') }}</p>
        <el-divider />
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('settings.siteUrlSyncCurrent')">
            <el-tag type="warning">{{ currentSiteUrl }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('settings.siteUrlSyncSaved')">
            <el-tag type="info">{{ savedSiteUrl || '(' + $t('settings.siteUrlSyncEmpty') + ')' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="siteUrlDialogVisible = false">{{ $t('settings.siteUrlSyncLater') }}</el-button>
        <el-button type="primary" @click="syncSiteUrl" :loading="syncingSiteUrl">
          {{ $t('settings.siteUrlSyncButton') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" :title="$t('user.changePassword')" width="520px" :close-on-click-modal="false">
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-width="160px"
      >
        <el-form-item :label="$t('user.oldPassword')" prop="oldPassword">
          <el-input
            v-model="passwordForm.oldPassword"
            type="password"
            show-password
            :placeholder="$t('user.oldPasswordPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('user.newPassword')" prop="newPassword">
          <el-input
            v-model="passwordForm.newPassword"
            type="password"
            show-password
            :placeholder="$t('user.newPasswordPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('user.confirmPassword')" prop="confirmPassword">
          <el-input
            v-model="passwordForm.confirmPassword"
            type="password"
            show-password
            :placeholder="$t('user.confirmPasswordPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="submitChangePassword" :loading="changingPassword">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getRoleTagStyle as getRoleTagStyleByRoleName } from '@/utils/roleColors'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { logout } from '@/api/auth'
import request from '@/utils/request'
import {
  HomeFilled,
  Folder,
  Connection,
  User,
  Setting,
  UserFilled,
  ArrowDown,
  DataAnalysis,
  Document,
  Fold,
  Expand,
  Message,
  Monitor,
  Tools,
  Cpu,
  ChatDotSquare,
  Lock,
  Avatar,
  Key
} from '@element-plus/icons-vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ========== 右上角角色铭牌 ==========
// 角色列表（拥有 user:view 权限时获取，用于解析自定义角色名）
const roleOptions = ref([])
// 当前用户角色名：优先自定义角色名，兜底本地化基础角色名（离线可用）
const currentRoleName = computed(() => {
  const role = userStore.userInfo?.role
  if (roleOptions.value.length && role) {
    const matched = roleOptions.value.find(r => r.code === 'ROLE_' + role)
    if (matched) return matched.name
  }
  if (role === 'ADMIN') return t('profile.admin')
  return t('profile.normalUser') || role || ''
})
// 获取角色列表用于铭牌展示（无权限则静默跳过，铭牌走本地化兜底）
const fetchRolesForBadge = async () => {
  if (!userStore.hasPermission('user:view')) return
  try {
    const response = await request.get('/users/roles')
    if (response.code === 200) {
      roleOptions.value = response.data || []
    }
  } catch (error) {
    // 静默失败，铭牌使用本地化兜底
  }
}

// 侧边栏展开/收缩状态（默认展开）
const collapsed = ref(false)

// 当前激活的菜单
const activeMenu = computed(() => route.path)

// 根据当前路由自动展开对应的子菜单分组
const defaultOpeneds = computed(() => {
  const path = route.path
  const opened = []
  if (['/projects', '/apis', '/code-templates'].some(p => path === p || path.startsWith(p + '/'))) {
    opened.push('sub-business')
  }
  if (['/users', '/roles', '/permissions'].some(p => path === p || path.startsWith(p + '/'))) {
    opened.push('sub-permission')
  }
  if (['/email-templates', '/ai-settings', '/settings'].some(p => path === p || path.startsWith(p + '/'))) {
    opened.push('sub-system')
  }
  return opened
})

// 版本号
const appVersion = ref('')

// 页脚配置
const footerConfig = reactive({
  enableCopyright: true,
  copyright: '',
  enableFriendLink: true,
  friendLinkUrl: '',
  friendLinkTitle: '',
  enableBlog: true,
  blogUrl: '',
  blogTitle: '',
  enableGithub: true,
  githubUrl: '',
  githubTitle: '',
  enableEmail: true,
  emailAddress: '',
  emailTitle: '',
  enableCustomLinks: true,
  customLinks: []
})

// 获取版本号并检测版本变更
const fetchVersion = async () => {
  try {
    const response = await request.get('/system/version')
    if (response.code === 200 && response.data) {
      const serverVersion = response.data.version || ''
      appVersion.value = 'v' + serverVersion

      // 检测版本缓存，如果浏览器缓存的版本与后台不一致则弹窗
      const cachedVersion = localStorage.getItem('app_version')
      if (cachedVersion && cachedVersion !== serverVersion) {
        showVersionMismatchDialog(serverVersion)
      }
      // 更新本地缓存的版本号
      localStorage.setItem('app_version', serverVersion)
    }
  } catch (error) {
    console.warn('获取版本号失败:', error)
    appVersion.value = ''
  }
}

// 版本不一致弹窗
const showVersionMismatchDialog = (newVersion) => {
  ElMessageBox.alert(
    '检测到系统版本已更新，建议清理浏览器缓存以确保页面正常显示。',
    '版本更新提示',
    {
      confirmButtonText: '一键清理缓存并刷新',
      cancelButtonText: '稍后处理',
      showCancelButton: true,
      type: 'warning',
      center: true,
      distinguishCancelAndClose: true,
      closeOnClickModal: false,
      closeOnPressEscape: false
    }
  ).then(() => {
    // 用户点击了确认 - 清理缓存并刷新
    clearCacheAndReload()
  }).catch(() => {
    // 用户点击了取消或关闭 - 不做处理
  })
}

// 清理浏览器缓存并刷新页面
const clearCacheAndReload = () => {
  try {
    // 清除所有 localStorage
    localStorage.clear()
    // 清除所有 sessionStorage
    sessionStorage.clear()
    // 清除所有 cookies
    document.cookie.split(';').forEach(cookie => {
      const eqPos = cookie.indexOf('=')
      const name = eqPos > -1 ? cookie.substring(0, eqPos).trim() : cookie.trim()
      document.cookie = name + '=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/'
    })
    // 通过 Service Worker 清除缓存（如果可用）
    if ('caches' in window) {
      caches.keys().then(names => {
        names.forEach(name => caches.delete(name))
      })
    }
  } catch (e) {
    console.warn('清理缓存时出错:', e)
  }
  // 刷新页面
  window.location.reload(true)
}

// 判断是否有页脚内容需要展示
const hasFooterContent = computed(() => {
  return (footerConfig.enableCopyright && footerConfig.copyright) ||
    (footerConfig.enableFriendLink && footerConfig.friendLinkUrl) ||
    (footerConfig.enableBlog && footerConfig.blogUrl) ||
    (footerConfig.enableGithub && footerConfig.githubUrl) ||
    (footerConfig.enableEmail && footerConfig.emailAddress) ||
    (footerConfig.enableCustomLinks && footerConfig.customLinks && footerConfig.customLinks.length > 0)
})

// 获取页脚配置
const fetchFooterConfig = async () => {
  try {
    const response = await request.get('/system-config/footer')
    if (response.code === 200 && response.data) {
      Object.assign(footerConfig, response.data)
    }
  } catch (error) {
    console.warn('获取页脚配置失败:', error)
  }
}

// ========== 系统访问地址自动检测与同步 ==========
const siteUrlDialogVisible = ref(false)
const currentSiteUrl = ref(window.location.origin)
const savedSiteUrl = ref('')
const syncingSiteUrl = ref(false)

// 检测并提示同步系统访问地址
const checkAndSyncSiteUrl = async () => {
  // 仅拥有系统设置权限的用户执行此检测
  if (!userStore.hasPermission('settings:view')) return

  try {
    const response = await request.get('/system-config')
    if (response.code === 200 && response.data) {
      const saved = response.data.siteBaseUrl || ''
      savedSiteUrl.value = saved

      // 对比当前 URI 与保存的地址
      if (saved !== currentSiteUrl.value) {
        // 不一致则弹窗提示
        siteUrlDialogVisible.value = true
      }
    }
  } catch (error) {
    console.warn('检测系统访问地址失败:', error)
  }
}

// 一键同步系统访问地址
const syncSiteUrl = async () => {
  syncingSiteUrl.value = true
  try {
    const response = await request.post('/system-config/registration', {
      siteBaseUrl: currentSiteUrl.value
    })
    if (response.code === 200) {
      savedSiteUrl.value = currentSiteUrl.value
      ElMessage.success(t('settings.siteUrlSyncSuccess'))
      siteUrlDialogVisible.value = false
    } else {
      ElMessage.error(response.message || t('common.error'))
    }
  } catch (error) {
    console.error('同步系统访问地址失败:', error)
    ElMessage.error(t('common.error'))
  } finally {
    syncingSiteUrl.value = false
  }
}

// 修改密码对话框
const passwordDialogVisible = ref(false)
const changingPassword = ref(false)
const passwordFormRef = ref(null)
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value === '') {
    callback(new Error(t('user.confirmPasswordRequired')))
  } else if (value !== passwordForm.newPassword) {
    callback(new Error(t('user.passwordMismatch')))
  } else {
    callback()
  }
}

// 使用 computed 确保验证消息响应语言切换
const passwordRules = computed(() => ({
  oldPassword: [
    { required: true, message: t('user.oldPasswordRequired'), trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: t('user.newPasswordRequired'), trigger: 'blur' },
    { min: 8, message: t('user.passwordMinLength'), trigger: 'blur' },
    { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?])/, message: t('user.passwordStrengthHint'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('user.confirmPasswordRequired'), trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' }
  ]
}))

// 处理用户操作
const handleCommand = (command) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'password':
      // 打开修改密码对话框
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      passwordFormRef.value?.resetFields()
      passwordDialogVisible.value = true
      break
    case 'logout':
      handleLogout()
      break
  }
}

// 提交修改密码
const submitChangePassword = async () => {
  if (!passwordFormRef.value) return

  try {
    await passwordFormRef.value.validate()
  } catch {
    return
  }

  // 二次确认
  try {
    await ElMessageBox.confirm(
      t('user.confirmChangePassword'),
      t('common.warning'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
  } catch {
    return
  }

  changingPassword.value = true
  try {
    const response = await request.post('/users/change-password', {
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword
    })
    if (response.code === 200) {
      ElMessage.success(t('user.passwordChanged'))
      passwordDialogVisible.value = false
      // 退出登录
      setTimeout(() => {
        handleLogout()
      }, 1500)
    } else {
      ElMessage.error(response.message || t('user.passwordChangeFailed'))
    }
  } catch (error) {
    console.error('修改密码失败:', error)
    ElMessage.error(t('user.passwordChangeFailed'))
  } finally {
    changingPassword.value = false
  }
}

// 头像加载错误处理 - 回退到本地默认头像
const avatarSrc = ref(userStore.userAvatar)
const handleAvatarError = () => {
  avatarSrc.value = '/default-avatar.png'
}

// 用户信息变更时重置头像源
watch(() => userStore.userAvatar, (newVal) => {
  avatarSrc.value = newVal
})

// 退出登录
const handleLogout = async () => {
  try {
    // 调用后端登出接口（清理后端状态，如果有的话）
    await logout()
  } catch (error) {
    console.warn('后端登出调用失败，继续前端登出', error)
  }
  
  // 前端清理token
  userStore.logout()
  ElMessage.success(t('common.logoutSuccess'))
  // 使用硬跳转确保页面状态完全重置，避免 Vue Router 导航被取消
  window.location.href = '/'
}

// 监听页脚配置变更事件
const handleFooterConfigUpdated = () => {
  fetchFooterConfig()
}

// 侧边栏折叠/展开时重新调整 canvas 尺寸
watch(collapsed, () => {
  setTimeout(() => {
    const canvas = sidebarCanvas.value
    if (!canvas) return
    const sidebar = canvas.parentElement
    const rect = sidebar.getBoundingClientRect()
    canvas.width = rect.width * devicePixelRatio
    canvas.height = rect.height * devicePixelRatio
    canvas.style.width = rect.width + 'px'
    canvas.style.height = rect.height + 'px'
  }, 350) // 等待 CSS transition 完成
})

// ========== 侧边栏动态线条动画 ==========
const sidebarCanvas = ref(null)
let animationId = null

class FloatingLine {
  constructor(w, h) {
    this.w = w
    this.h = h
    this.reset()
  }

  reset() {
    // 随机起始位置（偏左侧区域）
    this.x = Math.random() * this.w * 0.9
    this.y = Math.random() * this.h
    // 随机方向角度
    this.angle = Math.random() * Math.PI * 2
    this.speed = 0.3 + Math.random() * 0.6
    // 线段长度
    this.length = 20 + Math.random() * 60
    // 线条颜色（蓝紫青之间）
    const hues = [220, 260, 280, 180, 200]
    this.hue = hues[Math.floor(Math.random() * hues.length)]
    this.alpha = 0.03 + Math.random() * 0.07
    this.lineWidth = 0.5 + Math.random() * 1.2
    // 轨迹点
    this.trail = []
    this.maxTrail = 30 + Math.floor(Math.random() * 50)
    // 方向变化计时
    this.dirChangeTimer = 0
    this.dirChangeInterval = 60 + Math.random() * 120
    // 目标角度
    this.targetAngle = this.angle
  }

  update() {
    // 平滑转向
    this.dirChangeTimer++
    if (this.dirChangeTimer >= this.dirChangeInterval) {
      this.dirChangeTimer = 0
      this.dirChangeInterval = 60 + Math.random() * 120
      this.targetAngle = this.angle + (Math.random() - 0.5) * Math.PI * 0.8
    }
    this.angle += (this.targetAngle - this.angle) * 0.02

    // 边界反弹
    this.x += Math.cos(this.angle) * this.speed
    this.y += Math.sin(this.angle) * this.speed

    if (this.x < -10 || this.x > this.w + 10) {
      this.angle = Math.PI - this.angle
      this.targetAngle = this.angle
    }
    if (this.y < -10 || this.y > this.h + 10) {
      this.angle = -this.angle
      this.targetAngle = this.angle
    }

    // 记录轨迹
    this.trail.push({ x: this.x, y: this.y })
    if (this.trail.length > this.maxTrail) {
      this.trail.shift()
    }

    // 偶尔重置
    if (Math.random() < 0.0003) {
      this.reset()
    }
  }

  draw(ctx) {
    if (this.trail.length < 2) return

    for (let i = 1; i < this.trail.length; i++) {
      const p0 = this.trail[i - 1]
      const p1 = this.trail[i]
      const progress = i / this.trail.length
      const alpha = this.alpha * progress * 0.6

      ctx.beginPath()
      ctx.moveTo(p0.x, p0.y)
      ctx.lineTo(p1.x, p1.y)
      ctx.strokeStyle = `hsla(${this.hue}, 70%, 60%, ${alpha})`
      ctx.lineWidth = this.lineWidth * progress
      ctx.lineCap = 'round'
      ctx.stroke()
    }
  }
}

const initSidebarCanvas = () => {
  const canvas = sidebarCanvas.value
  if (!canvas) return

  const sidebar = canvas.parentElement
  const resize = () => {
    const rect = sidebar.getBoundingClientRect()
    canvas.width = rect.width * devicePixelRatio
    canvas.height = rect.height * devicePixelRatio
    canvas.style.width = rect.width + 'px'
    canvas.style.height = rect.height + 'px'
  }
  resize()

  const ctx = canvas.getContext('2d')
  ctx.scale(devicePixelRatio, devicePixelRatio)

  // 创建 6-8 条随机游走线条
  const lines = []
  const lineCount = 6 + Math.floor(Math.random() * 3)
  for (let i = 0; i < lineCount; i++) {
    lines.push(new FloatingLine(canvas.width / devicePixelRatio, canvas.height / devicePixelRatio))
  }

  const animate = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)

    lines.forEach(line => {
      line.update()
      line.draw(ctx)
    })

    animationId = requestAnimationFrame(animate)
  }

  animate()

  // 窗口大小变化时重新调整
  window.addEventListener('resize', resize)
}

const destroySidebarCanvas = () => {
  if (animationId) {
    cancelAnimationFrame(animationId)
    animationId = null
  }
}

// 同步 AI 超时配置到 localStorage（供 request.js 拦截器动态读取）
// 使用 /ai-config/enabled 接口（所有认证用户可访问），避免普通用户调用 /ai-config 触发 403
async function syncAiTimeout() {
  try {
    const res = await request.get('/ai-config/enabled')
    if (res.code === 200 && res.data) {
      const enabled = res.data
      if (enabled.timeout) {
        localStorage.setItem('aiTimeout', enabled.timeout * 1000)
      }
    }
  } catch (e) {
    // 静默失败，使用 request.js 中的默认值
  }
}

// 页面加载时获取数据
onMounted(() => {
  // 刷新权限（从后端获取最新权限，覆盖 localStorage 缓存）
  userStore.refreshPermissions()
  fetchVersion()
  fetchFooterConfig()
  checkAndSyncSiteUrl()
  syncAiTimeout()  // 同步 AI 超时配置，确保所有 AI 生成功能使用正确的超时时间
  fetchRolesForBadge()  // 获取角色列表用于右上角铭牌展示
  window.addEventListener('footer-config-updated', handleFooterConfigUpdated)
  // 延迟初始化 canvas，等 DOM 渲染完成
  setTimeout(() => initSidebarCanvas(), 100)
})

// 组件卸载时清理事件监听
onBeforeUnmount(() => {
  window.removeEventListener('footer-config-updated', handleFooterConfigUpdated)
  destroySidebarCanvas()
})
</script>

<style scoped>
.dashboard-layout {
  display: flex;
  height: 100vh;
  background-color: #f0f2f5;
}

.sidebar {
  background: linear-gradient(180deg, #1a1a2e 0%, #16213e 40%, #0f3460 100%);
  overflow-y: auto;
  box-shadow: 4px 0 24px rgba(0, 0, 0, 0.3), inset -1px 0 0 rgba(255, 255, 255, 0.05);
  position: relative;
  display: flex;
  flex-direction: column;
  transition: width 0.35s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 动态线条 Canvas 层 */
.sidebar-canvas {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.55;
}

/* 侧边栏顶部光晕 */
.sidebar::after {
  content: '';
  position: absolute;
  top: -60px;
  left: -30px;
  width: 180px;
  height: 180px;
  background: radial-gradient(circle, rgba(102, 126, 234, 0.15) 0%, transparent 70%);
  pointer-events: none;
  z-index: 0;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: linear-gradient(180deg, rgba(255,255,255,0.06) 0%, transparent 100%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  color: #fff;
  position: relative;
  z-index: 1;
}

.logo h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 1px;
  background: linear-gradient(
    135deg,
    #667eea 0%,
    #e83e8c 30%,
    #fd7e14 60%,
    #20c997 100%
  );
  background-size: 200% 200%;
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  animation: sidebarGradientShift 5s ease infinite;
}

.sidebar-logo-icon {
  flex-shrink: 0;
  color: #667eea;
}

@keyframes sidebarGradientShift {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.el-menu-vertical {
  border-right: none;
  position: relative;
  z-index: 1;
  padding: 8px 0;
}

/* Element Plus 菜单背景覆盖 */
.sidebar :deep(.el-menu) {
  background-color: transparent !important;
  border-right: none !important;
}

/* 子菜单标题样式 */
.sidebar :deep(.el-sub-menu__title) {
  height: 50px;
  line-height: 50px;
  margin: 2px 10px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.55) !important;
  background-color: transparent !important;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  font-size: 13px;
  letter-spacing: 0.5px;
}

.sidebar :deep(.el-sub-menu__title):hover {
  color: rgba(255, 255, 255, 0.8) !important;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.05) 100%) !important;
}

/* 子菜单标题前的小竖线指示器 */
.sidebar :deep(.el-sub-menu__title)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 0;
  background: linear-gradient(180deg, #667eea, #764ba2);
  border-radius: 0 3px 3px 0;
  transform: translateY(-50%);
  transition: height 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar :deep(.el-sub-menu.is-opened .el-sub-menu__title) {
  color: rgba(255, 255, 255, 0.75) !important;
}

.sidebar :deep(.el-sub-menu.is-opened .el-sub-menu__title)::before {
  height: 40%;
}

/* 子菜单内菜单项缩进 */
.sidebar :deep(.el-sub-menu .el-menu-item) {
  padding-left: 52px !important;
  height: 44px;
  line-height: 44px;
  margin: 1px 10px 1px 10px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
}

.sidebar :deep(.el-sub-menu .el-menu-item):hover {
  color: rgba(255, 255, 255, 0.8) !important;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.06) 100%) !important;
}

.sidebar :deep(.el-sub-menu .el-menu-item.is-active) {
  color: #fff !important;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.18) 0%, rgba(118, 75, 162, 0.12) 100%) !important;
  box-shadow: 0 1px 8px rgba(102, 126, 234, 0.12), inset 0 0 0 1px rgba(102, 126, 234, 0.2);
  font-weight: 600;
}

/* 折叠态子菜单弹出面板样式 */
.sidebar.collapsed :deep(.el-sub-menu .el-menu-item) {
  padding-left: 20px !important;
}

/* 折叠态：子菜单标题图标居中 */
.sidebar.collapsed :deep(.el-sub-menu__title) {
  justify-content: center;
  padding: 0 !important;
  margin: 2px 8px;
}

.sidebar.collapsed :deep(.el-sub-menu__title) span {
  display: none;
}

.sidebar.collapsed :deep(.el-sub-menu__title) .el-sub-menu__icon-arrow {
  display: none;
}

.sidebar.collapsed :deep(.el-sub-menu__title)::before {
  display: none;
}

/* 子菜单箭头颜色 */
.sidebar :deep(.el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.35);
  transition: color 0.3s ease;
}

.sidebar :deep(.el-sub-menu__title):hover .el-sub-menu__icon-arrow {
  color: rgba(255, 255, 255, 0.65);
}

.sidebar :deep(.el-sub-menu.is-opened .el-sub-menu__icon-arrow) {
  color: rgba(255, 255, 255, 0.55);
}

.sidebar :deep(.el-menu-item) {
  height: 50px;
  line-height: 50px;
  margin: 2px 10px;
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.65);
  background-color: transparent !important;
  transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  overflow: hidden;
}

/* 菜单项 hover 光效 */
.sidebar :deep(.el-menu-item)::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  width: 3px;
  height: 0;
  background: linear-gradient(180deg, #667eea, #764ba2, #e83e8c);
  border-radius: 0 3px 3px 0;
  transform: translateY(-50%);
  transition: height 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.sidebar :deep(.el-menu-item:hover) {
  color: #fff !important;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.12) 0%, rgba(118, 75, 162, 0.08) 100%) !important;
  transform: translateX(2px);
}

.sidebar :deep(.el-menu-item:hover)::before {
  height: 60%;
}

/* 选中态 */
.sidebar :deep(.el-menu-item.is-active) {
  color: #fff !important;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.2) 0%, rgba(118, 75, 162, 0.15) 100%) !important;
  box-shadow: 0 2px 12px rgba(102, 126, 234, 0.15), inset 0 0 0 1px rgba(102, 126, 234, 0.25);
  font-weight: 600;
}

.sidebar :deep(.el-menu-item.is-active)::before {
  height: 80%;
  background: linear-gradient(180deg, #667eea, #e83e8c);
}

/* 菜单图标动效 */
.sidebar :deep(.el-menu-item .el-icon) {
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), color 0.3s ease;
}

.sidebar :deep(.el-menu-item:hover .el-icon) {
  transform: scale(1.15);
}

.sidebar :deep(.el-menu-item.is-active .el-icon) {
  transform: scale(1.1);
}

.sidebar-version {
  padding: 14px 0;
  text-align: center;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  z-index: 1;
}

.sidebar-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  margin-top: auto;
  cursor: pointer;
  color: rgba(255, 255, 255, 0.45);
  background: linear-gradient(0deg, rgba(0,0,0,0.15) 0%, transparent 100%);
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  z-index: 2;
  transition: color 0.3s ease, background-color 0.3s ease;
  position: relative;
  flex-shrink: 0;
}

.sidebar-toggle:hover {
  color: #fff;
  background: linear-gradient(0deg, rgba(102, 126, 234, 0.15) 0%, rgba(102, 126, 234, 0.05) 100%);
}

/* 折叠态：菜单项居中适配 */
.sidebar.collapsed :deep(.el-menu-item) {
  justify-content: center;
  padding: 0 !important;
  margin: 2px 8px;
}

.sidebar.collapsed :deep(.el-menu-item span) {
  display: none;
}

.version-text {
  font-size: 12px;
  font-weight: 500;
  background: linear-gradient(135deg, #409EFF 0%, #67C23A 50%, #E6A23C 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 60px !important;
}

.header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 8px 12px;
  border-radius: 4px;
  transition: background-color 0.3s;
}

.user-info:hover {
  background-color: #f5f7fa;
}

.username {
  margin-left: 8px;
  margin-right: 4px;
  font-size: 14px;
  color: #606266;
}

/* 右上角角色铭牌：背景色取自 Issue #13 色板，字体固定黑色 */
.role-badge {
  display: inline-block;
  margin-left: 4px;
  margin-right: 4px;
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  line-height: 18px;
  border: 1px solid transparent;
  white-space: nowrap;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
}

.main-content {
  padding: 20px;
  overflow-y: auto;
  background-color: #f0f2f5;
  flex: 1;
}

/* AI 对话页面：去除 overflow 和 padding，让子组件自行管理高度 */
.main-content-chat {
  overflow-y: hidden;
  padding: 12px 20px;
}

.footer {
  background-color: #fff;
  border-top: 1px solid #e6e6e6;
  padding: 16px 20px;
}

.footer-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.footer-copyright {
  font-size: 13px;
  color: #909399;
}

.footer-links {
  display: flex;
  align-items: center;
  gap: 20px;
}

.footer-link {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: #f5f7fa;
  text-decoration: none;
  transition: all 0.3s ease;
}

.footer-link:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 8px rgba(0, 0, 0, 0.1);
}

.footer-link svg {
  display: block;
}

.footer-link-custom-svg :deep(svg) {
  display: block;
  width: 20px;
  height: 20px;
}

@media (max-width: 768px) {
  .sidebar {
    width: 180px !important;
  }

  .main-content {
    padding: 10px;
  }

  .footer-content {
    gap: 10px;
  }

  .footer-links {
    gap: 16px;
  }
}
</style>
