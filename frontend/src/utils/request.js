/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

// 从 localStorage 读取超时配置（由 Settings.vue 保存）
const savedTimeout = localStorage.getItem('axiosTimeout')
const defaultTimeout = savedTimeout ? parseInt(savedTimeout) : 30000

// 获取 AI 请求超时（由 AiSettings.vue 保存，默认 15 分钟）
function getAiTimeout() {
  const saved = localStorage.getItem('aiTimeout')
  return saved ? parseInt(saved) : 900000
}

// 创建axios实例
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: defaultTimeout,
  withCredentials: false // 关闭凭据发送，避免CORS问题
})

// 请求拦截器
service.interceptors.request.use(
  (config) => {
    // 添加token
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers['Authorization'] = `Bearer ${userStore.token}`
    }

    // AI 相关接口动态读取用户在 AI 设置中配置的超时时间（实时全局生效）
    // 注意：流式 SSE 接口使用原生 fetch()，不走 axios，不受此影响
    if (config.url && (
      config.url.includes('/ai/generate-') ||
      config.url.includes('/ai/chat') ||
      config.url.includes('/ai/chat-suggestions') ||
      config.url.includes('/ai-config/test-connectivity') ||
      config.url.includes('/mock-apis/export-markdown')
    )) {
      config.timeout = getAiTimeout()
    }

    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 判断是否为登录相关请求
const isAuthRequest = (url) => {
  return url && (
    url.includes('/auth/login') ||
    url.includes('/auth/swagger-login')
  )
}

// 响应拦截器
service.interceptors.response.use(
  (response) => {
    const res = response.data
    
    // 如果是二进制数据，直接返回
    if (response.config.responseType === 'blob') {
      return response
    }
    
    // 如果是 Actuator 端点（非统一响应格式），直接返回原始数据
    const url = response.config.url || ''
    if (url.includes('/actuator/')) {
      return response.data
    }

    // 处理响应数据
    if (res.code === 200) {
      return res
    }
    
    // 登录相关接口的错误，直接返回让调用方（Login.vue）处理
    if (isAuthRequest(response.config.url)) {
      return res
    }
    
    if (res.code === 401) {
      // token过期或未授权
      ElMessage.error('登录已过期，请重新登录')
      const userStore = useUserStore()
      userStore.logout()
      window.location.href = '/'
      return Promise.reject(new Error(res.message || '登录已过期'))
    } else if (res.code === 403) {
      // 禁止访问
      ElMessage.error('没有权限访问')
      return Promise.reject(new Error(res.message || '没有权限'))
    } else {
      // 其他错误
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  (error) => {
    let message = '网络错误'
    
    if (error.response) {
      // 服务器响应错误
      switch (error.response.status) {
        case 400:
          message = '请求参数错误'
          break
        case 401:
          message = '未授权，请登录'
          const userStore = useUserStore()
          userStore.logout()
          window.location.href = '/'
          break
        case 403:
          message = '没有权限'
          break
        case 404:
          message = '请求的资源不存在'
          break
        case 500:
          message = '服务器内部错误'
          break
        case 503:
          message = '服务不可用'
          break
        default:
          message = error.response.data?.message || '请求失败'
      }
    } else if (error.request) {
      // 请求发送失败
      message = '网络连接失败，请检查网络'
    } else {
      // 其他错误
      message = error.message || '请求失败'
    }
    
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
