/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { login as loginApi } from '@/api/auth'
import { generateAvatarDataUri } from '@/utils/avatar'

export const useUserStore = defineStore('user', () => {
  // 状态
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
  const permissions = ref(JSON.parse(localStorage.getItem('permissions') || '[]'))

  // 头像 URL（本地生成 SVG，离线可用，无外部网络依赖）
  const userAvatar = ref('/default-avatar.png')

  const updateAvatarUrl = () => {
    // 没有用户信息时使用默认头像
    const name = userInfo.value.username || userInfo.value.email || ''
    userAvatar.value = name ? generateAvatarDataUri(name) : '/default-avatar.png'
  }

  // 监听用户信息变更，自动更新头像
  watch(userInfo, () => {
    updateAvatarUrl()
  }, { immediate: true, deep: true })

  // 计算属性
  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => userInfo.value.role === 'ADMIN')
  const username = computed(() => userInfo.value.username || '')

  // 检查是否拥有指定权限
  const hasPermission = (permCode) => {
    // 管理员拥有所有权限
    if (isAdmin.value) return true
    return permissions.value.includes(permCode)
  }

  // 检查是否拥有任意一个权限
  const hasAnyPermission = (permCodes) => {
    if (isAdmin.value) return true
    if (!permCodes || !Array.isArray(permCodes)) return false
    return permCodes.some(code => permissions.value.includes(code))
  }

  // 登录
  const login = async (username, password) => {
    try {
      const response = await loginApi({ username, password })
      if (response.code === 200) {
        const { token: userToken, userId, username: name, role, email, language, permissions: perms } = response.data

        // 保存token
        token.value = userToken
        localStorage.setItem('token', userToken)

        // 保存用户信息
        userInfo.value = { id: userId, username: name, role, email, language }
        localStorage.setItem('userInfo', JSON.stringify(userInfo.value))

        // 保存权限列表
        if (perms && Array.isArray(perms)) {
          permissions.value = perms
          localStorage.setItem('permissions', JSON.stringify(perms))
        } else {
          permissions.value = []
          localStorage.setItem('permissions', '[]')
        }

        return { success: true }
      } else {
        return { success: false, message: response.message }
      }
    } catch (error) {
      return { success: false, message: error.message }
    }
  }

  // 登出
  const logout = () => {
    token.value = ''
    userInfo.value = {}
    permissions.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    localStorage.removeItem('permissions')
  }

  // 设置token
  const setToken = (userToken) => {
    token.value = userToken
    localStorage.setItem('token', userToken)
  }

  // 刷新权限（页面加载时从后端获取最新权限）
  const refreshPermissions = async () => {
    try {
      const request = (await import('@/utils/request')).default
      const response = await request.get('/auth/permissions')
      if (response.code === 200 && Array.isArray(response.data)) {
        permissions.value = response.data
        localStorage.setItem('permissions', JSON.stringify(response.data))
      }
    } catch (error) {
      console.warn('刷新权限失败:', error)
    }
  }

  return {
    token,
    userInfo,
    permissions,
    isLoggedIn,
    isAdmin,
    username,
    userAvatar,
    hasPermission,
    hasAnyPermission,
    login,
    logout,
    setToken,
    refreshPermissions
  }
})
