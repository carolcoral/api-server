/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

const getMock = vi.fn()
vi.mock('@/utils/request', () => ({
  default: { get: (...args) => getMock(...args) }
}))

const { formatTime, getDateFormat, setDateFormat, loadDateFormat } =
  await import('@/utils/dateFormat')

describe('utils/dateFormat', () => {
  beforeEach(() => {
    getMock.mockReset()
    setDateFormat('YYYY-MM-DD')
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('未加载服务端配置时默认格式为 YYYY-MM-DD', () => {
    setDateFormat(null)
    expect(getDateFormat()).toBe('YYYY-MM-DD')
  })

  it('空值返回占位符', () => {
    expect(formatTime('')).toBe('-')
    expect(formatTime(null)).toBe('-')
    expect(formatTime(undefined)).toBe('-')
  })

  it('非法时间原样返回', () => {
    expect(formatTime('not-a-date')).toBe('not-a-date')
  })

  it('按 YYYY-MM-DD 格式化并补零', () => {
    expect(formatTime('2026-01-05T08:09:10')).toBe('2026-01-05 08:09:10')
  })

  it('按 DD/MM/YYYY 格式化', () => {
    setDateFormat('DD/MM/YYYY')
    expect(getDateFormat()).toBe('DD/MM/YYYY')
    expect(formatTime('2026-01-05T08:09:10')).toBe('05/01/2026 08:09:10')
  })

  it('按 MM/DD/YYYY 格式化', () => {
    setDateFormat('MM/DD/YYYY')
    expect(formatTime('2026-01-05T08:09:10')).toBe('01/05/2026 08:09:10')
  })

  it('未知格式回退到默认分支', () => {
    setDateFormat('UNKNOWN')
    expect(formatTime('2026-01-05T08:09:10')).toBe('2026-01-05 08:09:10')
  })

  it('loadDateFormat 成功时更新缓存', async () => {
    getMock.mockResolvedValue({ code: 200, data: { dateFormat: 'DD/MM/YYYY' } })
    await loadDateFormat()
    expect(getMock).toHaveBeenCalledWith('/system-config')
    expect(getDateFormat()).toBe('DD/MM/YYYY')
  })

  it('loadDateFormat 响应码非 200 时保持原格式', async () => {
    getMock.mockResolvedValue({ code: 500, data: { dateFormat: 'DD/MM/YYYY' } })
    await loadDateFormat()
    expect(getDateFormat()).toBe('YYYY-MM-DD')
  })

  it('loadDateFormat 数据缺失时保持原格式', async () => {
    getMock.mockResolvedValue({ code: 200, data: {} })
    await loadDateFormat()
    expect(getDateFormat()).toBe('YYYY-MM-DD')
  })

  it('loadDateFormat 异常时输出告警且不抛出', async () => {
    const warn = vi.spyOn(console, 'error').mockImplementation(() => {})
    getMock.mockRejectedValue(new Error('network down'))
    await expect(loadDateFormat()).resolves.toBeUndefined()
    expect(warn).toHaveBeenCalled()
  })
})
