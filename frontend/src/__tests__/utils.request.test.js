/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect, vi, beforeEach } from 'vitest'

// ---- 依赖桩：Element Plus 消息、用户 Store ----
const errorMsg = vi.fn()
vi.mock('element-plus', () => ({ ElMessage: { error: (...a) => errorMsg(...a) } }))

const logout = vi.fn()
vi.mock('@/stores/user', () => ({
  useUserStore: () => ({ token: 'test-token', logout })
}))

const requestHandlers = { request: [], response: [] }
vi.mock('axios', () => {
  const instance = {
    interceptors: {
      request: { use: (ok, err) => requestHandlers.request.push({ ok, err }) },
      response: { use: (ok, err) => requestHandlers.response.push({ ok, err }) }
    },
    defaults: {}
  }
  return { default: { create: () => instance } }
})

const service = (await import('@/utils/request')).default
const onRequest = requestHandlers.request[0].ok
const onRequestError = requestHandlers.request[0].err
const onResponse = requestHandlers.response[0].ok
const onResponseError = requestHandlers.response[0].err

describe('utils/request', () => {
  beforeEach(() => {
    localStorage.clear()
    errorMsg.mockClear()
    logout.mockClear()
  })

  it('导出 axios 实例', () => {
    expect(service).toBeTruthy()
  })

  it('请求拦截器注入 Authorization', async () => {
    const config = { url: '/projects', headers: {} }
    const result = await onRequest(config)
    expect(result.headers.Authorization).toBe('Bearer test-token')
  })

  it('AI 接口使用更长的超时时间', async () => {
    localStorage.setItem('aiTimeout', '60000')
    const config = { url: '/ai/chat', headers: {} }
    const result = await onRequest(config)
    expect(result.timeout).toBe(60000)
  })

  it('AI 接口无自定义超时回退默认值', async () => {
    const config = { url: '/ai/chat-suggestions', headers: {} }
    const result = await onRequest(config)
    expect(result.timeout).toBe(900000)
  })

  it('普通接口不覆盖超时', async () => {
    const config = { url: '/projects', headers: {} }
    const result = await onRequest(config)
    expect(result.timeout).toBeUndefined()
  })

  it('请求拦截器错误分支透传异常', async () => {
    const err = new Error('boom')
    await expect(onRequestError(err)).rejects.toThrow('boom')
  })

  describe('响应拦截器', () => {
    it('code=200 正常返回', () => {
      const res = onResponse({ data: { code: 200, data: { ok: true } }, config: { url: '/projects' } })
      expect(res.data.ok).toBe(true)
    })

    it('blob 响应直接返回原始响应', () => {
      const payload = { data: new Blob(['x']), config: { url: '/export', responseType: 'blob' } }
      expect(onResponse(payload)).toBe(payload)
    })

    it('actuator 端点直接返回裸数据', () => {
      const payload = { data: { status: 'UP' }, config: { url: '/actuator/health' } }
      expect(onResponse(payload)).toEqual({ status: 'UP' })
    })

    it('登录接口错误不触发全局提示', () => {
      const res = onResponse({ data: { code: 400, message: '密码错误' }, config: { url: '/auth/login' } })
      expect(res.code).toBe(400)
      expect(errorMsg).not.toHaveBeenCalled()
    })

    it('Swagger 登录错误不触发全局提示', () => {
      const res = onResponse({ data: { code: 400 }, config: { url: '/auth/swagger-login' } })
      expect(res.code).toBe(400)
    })

    it('code=401 清理登录态', async () => {
      await expect(onResponse({ data: { code: 401 }, config: { url: '/projects' } })).rejects.toThrow()
      expect(logout).toHaveBeenCalled()
      expect(errorMsg).toHaveBeenCalled()
    })

    it('code=403 提示无权限', async () => {
      await expect(onResponse({ data: { code: 403, message: '禁止' }, config: { url: '/projects' } })).rejects.toThrow('禁止')
    })

    it('其他错误码走默认分支', async () => {
      await expect(onResponse({ data: { code: 500, message: '崩了' }, config: { url: '/projects' } })).rejects.toThrow('崩了')
    })

    it('错误码无 message 时回退文案', async () => {
      await expect(onResponse({ data: { code: 500 }, config: { url: '/projects' } })).rejects.toThrow('请求失败')
    })
  })

  describe('响应错误分支', () => {
    const cases = [
      [400, '请求参数错误'],
      [401, '未授权，请登录'],
      [403, '没有权限'],
      [404, '请求的资源不存在'],
      [500, '服务器内部错误'],
      [503, '服务不可用']
    ]

    it.each(cases)('HTTP %i 映射为中文提示', async (status, message) => {
      await expect(onResponseError({ response: { status, data: {} } })).rejects.toBeTruthy()
      expect(errorMsg).toHaveBeenCalledWith(message)
    })

    it('未知 HTTP 状态码回退服务端 message', async () => {
      await expect(
        onResponseError({ response: { status: 418, data: { message: '我是个茶壶' } } })
      ).rejects.toBeTruthy()
      expect(errorMsg).toHaveBeenCalledWith('我是个茶壶')
    })

    it('未知 HTTP 状态码无 message 时回退默认文案', async () => {
      await expect(onResponseError({ response: { status: 418, data: {} } })).rejects.toBeTruthy()
      expect(errorMsg).toHaveBeenCalledWith('请求失败')
    })

    it('请求已发出但无响应提示网络连接失败', async () => {
      await expect(onResponseError({ request: {} })).rejects.toBeTruthy()
      expect(errorMsg).toHaveBeenCalledWith('网络连接失败，请检查网络')
    })

    it('其他异常使用 error.message', async () => {
      await expect(onResponseError({ message: '超时了' })).rejects.toBeTruthy()
      expect(errorMsg).toHaveBeenCalledWith('超时了')
    })

    it('无任何信息的异常回退默认文案', async () => {
      await expect(onResponseError({})).rejects.toBeTruthy()
      expect(errorMsg).toHaveBeenCalledWith('请求失败')
    })
  })
})
