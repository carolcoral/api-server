/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect } from 'vitest'
import { ROLE_COLORS, ROLE_TEXT_COLOR, getRoleColor, getRoleTagStyle } from '@/utils/roleColors'

describe('utils/roleColors', () => {
  it('色板包含 20 个颜色且字体固定黑色', () => {
    expect(ROLE_COLORS).toHaveLength(20)
    expect(ROLE_TEXT_COLOR).toBe('#000000')
  })

  it('同一角色名稳定返回同一颜色', () => {
    expect(getRoleColor('admin')).toBe(getRoleColor('admin'))
    expect(ROLE_COLORS).toContain(getRoleColor('admin'))
  })

  it('不同角色名可得到不同颜色', () => {
    expect(getRoleColor('admin')).not.toBe(getRoleColor('guest'))
  })

  it('空角色名不再抛错，回退到色板内颜色', () => {
    expect(ROLE_COLORS).toContain(getRoleColor(''))
    expect(ROLE_COLORS).toContain(getRoleColor(null))
    expect(ROLE_COLORS).toContain(getRoleColor(undefined))
  })

  it('getRoleTagStyle 返回背景色与黑色字体', () => {
    const style = getRoleTagStyle('admin')
    expect(style.color).toBe(ROLE_TEXT_COLOR)
    expect(style.backgroundColor).toBe(getRoleColor('admin'))
    expect(style.borderColor).toBe(style.backgroundColor)
  })

  it('中文与长字符串角色名不越界', () => {
    expect(ROLE_COLORS).toContain(getRoleColor('超级管理员'))
    expect(ROLE_COLORS).toContain(getRoleColor('a'.repeat(512)))
  })
})
