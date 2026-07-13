import request from '@/utils/request'

const getBaseUrl = () => {
  if (typeof window !== 'undefined') {
    return window.location.origin
  }
  return ''
}

const getToken = () => {
  if (typeof window !== 'undefined') {
    return localStorage.getItem('token') || ''
  }
  return ''
}

/**
 * 使用原生 fetch 调用 Actuator 端点，绕过 axios 统一响应拦截器
 * Actuator 返回的是 Spring Boot 原始格式，不是 {code:200, data:...}
 */
const fetchActuator = async (path) => {
  const token = getToken()
  const res = await fetch(`${getBaseUrl()}${path}`, {
    headers: token ? { 'Authorization': `Bearer ${token}` } : {}
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  if (path === '/actuator/health') {
    return res.json()
  }
  return res.text()
}

/**
 * 系统运维与监控 API
 */
export default {
  // 获取备份信息
  getBackupInfo() {
    return request.get('/system/backup/info')
  },

  // 一键备份导出（返回 Blob）
  exportBackup() {
    return request.get('/system/backup/export', {
      responseType: 'blob'
    })
  },

  // 一键恢复
  restoreBackup(formData) {
    return request.post('/system/backup/restore', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },

  // 健康检查（走 fetch，绕过 axios 拦截器）
  getHealth() {
    return fetchActuator('/actuator/health')
  },

  // Prometheus 指标（走 fetch，绕过 axios 拦截器）
  getPrometheusMetrics() {
    return fetchActuator('/actuator/prometheus')
  }
}
