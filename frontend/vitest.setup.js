/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

/**
 * 单元测试全局初始化：提供 jsdom 缺失的浏览器能力，隔离外部依赖。
 */

// jsdom 未实现 matchMedia，Element Plus / 主题切换需要
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false
  })
}

// jsdom 未实现 ResizeObserver，ECharts / Element Plus 需要
if (!window.ResizeObserver) {
  window.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}

// jsdom 未实现 createObjectURL / revokeObjectURL，Blob 下载需要
if (!window.URL.createObjectURL) {
  window.URL.createObjectURL = () => 'blob:mock-url'
}
if (!window.URL.revokeObjectURL) {
  window.URL.revokeObjectURL = () => {}
}
