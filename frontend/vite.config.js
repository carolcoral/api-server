/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import monacoEditorPlugin from 'vite-plugin-monaco-editor'
import { resolve } from 'path'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => {
  // 加载环境变量
  const env = loadEnv(mode, process.cwd(), '')

  // 读取后端端口配置，默认8080
  const serverPort = env.VITE_SERVER_PORT || process.env.SERVER_PORT || 8080
  // 读取前端端口配置，默认3000
  const frontendPort = env.VITE_FRONTEND_PORT || process.env.FRONTEND_PORT || 3000

  return {
    plugins: [
      vue(),
      monacoEditorPlugin.default ? monacoEditorPlugin.default({}) : monacoEditorPlugin({})
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    server: {
      port: parseInt(frontendPort),
      host: '0.0.0.0',
      allowedHosts: true,
      proxy: {
        '/api': {
          target: `http://localhost:${serverPort}`,
          changeOrigin: true,
          secure: false,
          rewrite: (path) => path.replace(/^\/api/, '/api'),
          timeout: 600000,       // 代理请求超时 10 分钟，适应 AI 生成场景
          proxyTimeout: 600000   // 代理响应超时 10 分钟
        },
        // Bing 每日图片代理 → 后端 Spring Boot Controller（开发/生产统一）
        '/bing-hp': {
          target: `http://localhost:${serverPort}`,
          changeOrigin: true,
          secure: false,
        },
        // README.md 和 CHANGELOG.md 代理到后端静态资源（开发模式）
        '/README.md': {
          target: `http://localhost:${serverPort}`,
          changeOrigin: true,
          secure: false,
        },
        '/CHANGELOG.md': {
          target: `http://localhost:${serverPort}`,
          changeOrigin: true,
          secure: false,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: false,
      minify: 'terser',
      terserOptions: {
        compress: {
          drop_console: false,
          drop_debugger: true,
        },
      },
    },
  }
})
