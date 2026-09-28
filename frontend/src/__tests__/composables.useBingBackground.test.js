/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect } from 'vitest'
import { useBingBackground } from '@/composables/useBingBackground'

describe('composables/useBingBackground', () => {
  it('初始返回本地默认背景（离线优先）', () => {
    const { bgImage } = useBingBackground()
    expect(bgImage.value).toBe('/default-bg.jpg')
  })

  it('fetchBingBg 不发起任何外部请求', () => {
    const { bgImage, fetchBingBg } = useBingBackground()
    fetchBingBg()
    expect(bgImage.value).toBe('/default-bg.jpg')
  })

  it('重复调用命中缓存，返回同一背景', () => {
    const first = useBingBackground()
    first.fetchBingBg()
    const second = useBingBackground()
    second.fetchBingBg()
    expect(second.bgImage.value).toBe('/default-bg.jpg')
  })
})
