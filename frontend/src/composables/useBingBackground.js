/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { ref } from 'vue'

const DEFAULT_BG = '/default-bg.jpg'

// 模块级缓存：整个 SPA 生命周期只设置一次
let cachedUrl = null

/**
 * 获取登录/注册页背景图片 URL，自动缓存避免重复解析。
 *
 * 离线优先策略：直接使用本地默认背景 `/default-bg.jpg`，
 * 不请求 Bing 在线接口与 `cn.bing.com` 图片资源，保证离线环境可用。
 * Login、Register、ForgotPassword 页面共享同一份缓存。
 */
export function useBingBackground() {
  const bgImage = ref(cachedUrl || DEFAULT_BG)

  const fetchBingBg = () => {
    // 离线优先：直接使用本地默认背景，无任何外部网络请求
    if (cachedUrl) {
      bgImage.value = cachedUrl
      return
    }
    cachedUrl = DEFAULT_BG
    bgImage.value = DEFAULT_BG
  }

  return { bgImage, fetchBingBg }
}
