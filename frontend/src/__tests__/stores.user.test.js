/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

// ---- 依赖桩 ----
const loginApi = vi.fn()
vi.mock('@/api/auth', () => ({ login: (...a) => loginApi(...a) }))

vi.mock('@/utils/avatar', () => ({
  generateAvatarDataUri: (name) => `avatar:${name}`
}))

const get = vi.fn()
vi.mock('@/utils/request', () => ({ default: { get: (...a) => get(...a) } }))

const { useUserStore } = await import('@/stores/user')

const adminInfo = {
  userId: 1,
  username: 'admin',
  role: 'ADMIN',
  roleId: 1,
  roleName: '管理员',
  roleCode: 'ADMIN',
  email: 'a@b.c',
  language: 'zh-CN',
  token: 'tok-1',
  permissions: ['a', 'b']
}

describe('stores/user', () => {
  beforeEach(() => {
    localStorage.clear()
    loginApi.mockReset()
    get.mockReset()
    setActivePinia(createPinia())
  })

  it('初始为未登录状态', () => {
    const store = useUserStore()
    expect(store.isLoggedIn).toBe(false)
    expect(store.isAdmin).toBe(false)
    expect(store.username).toBe('')
    expect(store.userAvatar).toBe('/default-avatar.png')
  })

  it('从未登录状态本地恢复', () => {
    localStorage.setItem('token', 'tok')
    localStorage.setItem('userInfo', JSON.stringify({ username: 'bob', role: 'USER' }))
    localStorage.setItem('permissions', JSON.stringify(['x']))
    setActivePinia(createPinia())
    const store = useUserStore()
    expect(store.isLoggedIn).toBe(true)
    expect(store.username).toBe('bob')
    expect(store.userAvatar).toBe('avatar:bob')
  })

  it('登录成功写入 token 与用户信息', async () => {
    loginApi.mockResolvedValue({ code: 200, data: adminInfo })
    const store = useUserStore()
    const res = await store.login('admin', 'pwd')
    expect(res.success).toBe(true)
    expect(store.token).toBe('tok-1')
    expect(store.isLoggedIn).toBe(true)
    expect(store.isAdmin).toBe(true)
    expect(localStorage.getItem('token')).toBe('tok-1')
    expect(JSON.parse(localStorage.getItem('permissions'))).toEqual(['a', 'b'])
  })

  it('登录成功但无权限字段时清空权限', async () => {
    loginApi.mockResolvedValue({ code: 200, data: { ...adminInfo, permissions: null } })
    const store = useUserStore()
    await store.login('admin', 'pwd')
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem('permissions')).toBe('[]')
  })

  it('登录成功但权限非数组时清空权限', async () => {
    loginApi.mockResolvedValue({ code: 200, data: { ...adminInfo, permissions: 'oops' } })
    const store = useUserStore()
    await store.login('admin', 'pwd')
    expect(store.permissions).toEqual([])
  })

  it('业务码非 200 时返回失败信息', async () => {
    loginApi.mockResolvedValue({ code: 400, message: '账号或密码错误' })
    const store = useUserStore()
    const res = await store.login('admin', 'bad')
    expect(res).toEqual({ success: false, message: '账号或密码错误' })
    expect(store.isLoggedIn).toBe(false)
  })

  it('网络异常时捕获并返回失败信息', async () => {
    loginApi.mockRejectedValue(new Error('network down'))
    const store = useUserStore()
    const res = await store.login('admin', 'pwd')
    expect(res.success).toBe(false)
    expect(res.message).toBe('network down')
  })

  describe('loginWithToken（OIDC 回调登录）', () => {
    it('成功：写入 token、权限与用户资料', async () => {
      get.mockImplementation((url) => {
        if (url === '/auth/permissions') return Promise.resolve({ code: 200, data: ['p1', 'p2'] })
        if (url === '/users/profile') {
          return Promise.resolve({
            code: 200,
            data: { id: 9, username: 'oidcuser', role: 'USER', roleId: 2, roleName: '普通用户', roleCode: 'ROLE_USER', email: 'o@tdp.fan', language: 'zh-CN' }
          })
        }
        return Promise.resolve({ code: 200, data: null })
      })
      const store = useUserStore()
      const res = await store.loginWithToken('tok-oidc')
      expect(res.success).toBe(true)
      expect(store.token).toBe('tok-oidc')
      expect(localStorage.getItem('token')).toBe('tok-oidc')
      expect(store.permissions).toEqual(['p1', 'p2'])
      expect(store.username).toBe('oidcuser')
      expect(store.isLoggedIn).toBe(true)
    })

    it('权限接口返回非 200：清理 token 并返回失败', async () => {
      get.mockResolvedValue({ code: 401, message: 'token 无效' })
      const store = useUserStore()
      const res = await store.loginWithToken('bad-token')
      expect(res).toEqual({ success: false, message: 'token 无效' })
      expect(store.token).toBe('')
      expect(localStorage.getItem('token')).toBeNull()
    })

    it('权限接口非 200 且无 message：使用默认提示', async () => {
      get.mockResolvedValue({ code: 500 })
      const store = useUserStore()
      const res = await store.loginWithToken('bad-token')
      expect(res.success).toBe(false)
      expect(res.message).toBe('OIDC 登录失败')
    })

    it('权限非数组时置为空数组', async () => {
      get.mockImplementation((url) => {
        if (url === '/auth/permissions') return Promise.resolve({ code: 200, data: 'oops' })
        return Promise.resolve({ code: 200, data: null })
      })
      const store = useUserStore()
      const res = await store.loginWithToken('tok')
      expect(res.success).toBe(true)
      expect(store.permissions).toEqual([])
      expect(localStorage.getItem('permissions')).toBe('[]')
    })

    it('资料接口异常不影响登录成功', async () => {
      get.mockImplementation((url) => {
        if (url === '/auth/permissions') return Promise.resolve({ code: 200, data: ['p'] })
        return Promise.reject(new Error('profile down'))
      })
      const store = useUserStore()
      const res = await store.loginWithToken('tok')
      expect(res.success).toBe(true)
      expect(store.permissions).toEqual(['p'])
    })

    it('资料接口返回非 200 时保留空用户信息', async () => {
      get.mockImplementation((url) => {
        if (url === '/auth/permissions') return Promise.resolve({ code: 200, data: ['p'] })
        return Promise.resolve({ code: 404, message: 'not found' })
      })
      const store = useUserStore()
      const res = await store.loginWithToken('tok')
      expect(res.success).toBe(true)
      expect(store.userInfo).toEqual({})
    })

    it('请求抛异常：清理 token 并返回失败', async () => {
      get.mockRejectedValue(new Error('network down'))
      const store = useUserStore()
      const res = await store.loginWithToken('tok')
      expect(res.success).toBe(false)
      expect(res.message).toBe('network down')
      expect(store.token).toBe('')
      expect(localStorage.getItem('token')).toBeNull()
    })
  })

  it('登出清理全部登录态', async () => {
    loginApi.mockResolvedValue({ code: 200, data: adminInfo })
    const store = useUserStore()
    await store.login('admin', 'pwd')
    store.logout()
    expect(store.token).toBe('')
    expect(store.userInfo).toEqual({})
    expect(store.permissions).toEqual([])
    expect(localStorage.getItem('token')).toBeNull()
    expect(localStorage.getItem('userInfo')).toBeNull()
    expect(localStorage.getItem('permissions')).toBeNull()
  })

  it('setToken 同步写入本地存储', () => {
    const store = useUserStore()
    store.setToken('tok-x')
    expect(store.token).toBe('tok-x')
    expect(localStorage.getItem('token')).toBe('tok-x')
  })

  describe('hasPermission / hasAnyPermission', () => {
    it('管理员放行全部权限', async () => {
      loginApi.mockResolvedValue({ code: 200, data: adminInfo })
      const store = useUserStore()
      await store.login('admin', 'pwd')
      expect(store.hasPermission('any')).toBe(true)
      expect(store.hasAnyPermission(['any'])).toBe(true)
    })

    it('普通用户按权限列表判断', () => {
      const store = useUserStore()
      store.permissions = ['p1']
      store.userInfo = { role: 'USER' }
      expect(store.hasPermission('p1')).toBe(true)
      expect(store.hasPermission('p2')).toBe(false)
      expect(store.hasAnyPermission(['p2', 'p1'])).toBe(true)
      expect(store.hasAnyPermission(['p2'])).toBe(false)
    })

    it('非法入参安全返回 false', () => {
      const store = useUserStore()
      store.userInfo = { role: 'USER' }
      expect(store.hasAnyPermission(null)).toBe(false)
      expect(store.hasAnyPermission('p1')).toBe(false)
    })
  })

  describe('refreshPermissions', () => {
    it('刷新成功写入最新权限', async () => {
      get.mockResolvedValue({ code: 200, data: ['r1'] })
      const store = useUserStore()
      await store.refreshPermissions()
      expect(store.permissions).toEqual(['r1'])
      expect(localStorage.getItem('permissions')).toBe('["r1"]')
    })

    it('响应格式不符时保持原权限', async () => {
      get.mockResolvedValue({ code: 200, data: 'oops' })
      const store = useUserStore()
      await store.refreshPermissions()
      expect(store.permissions).toEqual([])
    })

    it('异常时仅告警不抛出', async () => {
      const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
      get.mockRejectedValue(new Error('boom'))
      const store = useUserStore()
      await expect(store.refreshPermissions()).resolves.toBeUndefined()
      expect(warn).toHaveBeenCalled()
      warn.mockRestore()
    })
  })

  it('用户信息变更时头像跟随更新', async () => {
    const store = useUserStore()
    store.userInfo = { username: 'tom' }
    await nextTick()
    expect(store.userAvatar).toBe('avatar:tom')
    store.userInfo = {}
    await nextTick()
    expect(store.userAvatar).toBe('/default-avatar.png')
  })
})
