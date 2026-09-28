/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * 单元测试配置（与 vite.config.js 分离，避免加载 monaco 等构建期插件）。
 *
 * 质量门禁：分支覆盖率 ≥ 75%、行覆盖率 ≥ 80%，未达标时 vitest 直接退出非 0。
 * 全局设置与红线见 .cnb/quality-gate.yml。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.{test,spec}.{js,jsx,mjs}'],
    setupFiles: ['./vitest.setup.js'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'json', 'json-summary', 'lcov'],
      reportsDirectory: './coverage',
      // 覆盖率统计范围：仅统计可单测的业务逻辑层（工具/状态/API/组合式函数）
      // 视图层（.vue）以交互与样式为主，交由 e2e/人工验收，不计入单测覆盖率红线
      include: ['src/{utils,stores,api,composables}/**/*.js'],
      exclude: [
        'src/**/*.{test,spec}.js',
        // 纯声明式配置 / 仅做请求转发的薄封装
        'src/api/ai.js',
        'src/api/aiUser.js',
        'src/api/codeTemplate.js',
        'src/api/projectExport.js'
      ],
      // 门禁红线：低于阈值直接失败
      thresholds: {
        branches: 75,
        lines: 80
      }
    }
  }
})
