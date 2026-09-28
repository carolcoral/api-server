/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const request = { get: vi.fn(() => 'axios-get'), post: vi.fn(() => 'axios-post') }
vi.mock('@/utils/request', () => ({ default: request }))

const { default: systemOps } = await import('@/api/systemOps')

describe('api/systemOps', () => {
  beforeEach(() => {
    localStorage.clear()
    request.get.mockClear()
    request.post.mockClear()
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('备份信息走 axios', () => {
    systemOps.getBackupInfo()
    expect(request.get).toHaveBeenCalledWith('/system/backup/info')
  })

  it('导出备份带 blob 响应类型', () => {
    systemOps.exportBackup()
    expect(request.get).toHaveBeenCalledWith('/system/backup/export', { responseType: 'blob' })
  })

  it('恢复备份使用 multipart 表单', () => {
    const form = new FormData()
    systemOps.restoreBackup(form)
    expect(request.post).toHaveBeenCalledWith('/system/backup/restore', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  })

  it('健康检查解析 JSON 且携带 token', async () => {
    localStorage.setItem('token', 'tok')
    const fetchMock = vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({ status: 'UP' }) }))
    vi.stubGlobal('fetch', fetchMock)

    const health = await systemOps.getHealth()
    expect(health).toEqual({ status: 'UP' })
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/actuator/health'),
      expect.objectContaining({ headers: { Authorization: 'Bearer tok' } })
    )
  })

  it('Prometheus 指标返回纯文本', async () => {
    const fetchMock = vi.fn(() => Promise.resolve({ ok: true, text: () => Promise.resolve('# HELP jvm') }))
    vi.stubGlobal('fetch', fetchMock)
    await expect(systemOps.getPrometheusMetrics()).resolves.toBe('# HELP jvm')
  })

  it('未登录时不携带 Authorization 头', async () => {
    const fetchMock = vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve({}) }))
    vi.stubGlobal('fetch', fetchMock)
    await systemOps.getHealth()
    expect(fetchMock.mock.calls[0][1].headers).toEqual({})
  })

  it('HTTP 非 2xx 时抛出错误', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve({ ok: false, status: 503 })))
    await expect(systemOps.getHealth()).rejects.toThrow('HTTP 503')
  })
})
