/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect } from 'vitest'
import { generateAvatarDataUri } from '@/utils/avatar'
import { getRoleColor, ROLE_TEXT_COLOR } from '@/utils/roleColors'

const decode = (uri) => decodeURIComponent(uri.replace('data:image/svg+xml;utf8,', ''))

describe('utils/avatar', () => {
  it('生成 SVG data URI', () => {
    const uri = generateAvatarDataUri('carolcoral')
    expect(uri.startsWith('data:image/svg+xml;utf8,')).toBe(true)
    expect(decode(uri)).toContain('<svg')
  })

  it('取用户名首字母并大写', () => {
    expect(decode(generateAvatarDataUri('carol'))).toContain('>C<')
  })

  it('中文用户名取第一个汉字', () => {
    expect(decode(generateAvatarDataUri('张三'))).toContain('>张<')
  })

  it('空用户名回退为问号头像', () => {
    expect(decode(generateAvatarDataUri(''))).toContain('>?<')
    expect(decode(generateAvatarDataUri(null))).toContain('>?<')
    expect(decode(generateAvatarDataUri('   '))).toContain('>?<')
  })

  it('背景色取自角色色板且字体为黑色', () => {
    const svg = decode(generateAvatarDataUri('carol'))
    expect(svg).toContain(getRoleColor('carol'))
    expect(svg).toContain(ROLE_TEXT_COLOR)
  })

  it('转义特殊字符，避免 SVG 注入', () => {
    const svg = decode(generateAvatarDataUri('<script>&"'))
    expect(svg).not.toContain('<script>')
    expect(svg).toContain('&lt;')
  })
})
