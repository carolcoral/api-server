/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import request from '@/utils/request'

/**
 * 触发浏览器下载 blob 数据
 * @param {Blob} blob 数据
 * @param {String} filename 文件名
 */
function downloadBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * 获取接口列表
 * @returns {Promise}
 */
export function getMockApiList() {
  return request({
    url: '/mock-apis',
    method: 'get'
  })
}

/**
 * 根据项目ID获取接口列表
 * @param {Number} projectId 项目ID
 * @returns {Promise}
 */
export function getMockApisByProjectId(projectId) {
  return request({
    url: `/mock-apis/project/${projectId}`,
    method: 'get'
  })
}

/**
 * 根据ID获取接口
 * @param {Number} id 接口ID
 * @returns {Promise}
 */
export function getMockApiById(id) {
  return request({
    url: `/mock-apis/${id}`,
    method: 'get'
  })
}

/**
 * 创建接口
 * @param {Object} data 接口数据
 * @returns {Promise}
 */
export function createMockApi(data) {
  return request({
    url: '/mock-apis',
    method: 'post',
    data
  })
}

/**
 * 更新接口
 * @param {Object} data 接口数据
 * @returns {Promise}
 */
export function updateMockApi(data) {
  return request({
    url: '/mock-apis',
    method: 'put',
    data
  })
}

/**
 * 删除接口
 * @param {Number} id 接口ID
 * @returns {Promise}
 */
export function deleteMockApi(id) {
  return request({
    url: `/mock-apis/${id}`,
    method: 'delete'
  })
}

/**
 * 切换接口状态
 * @param {Number} id 接口ID
 * @returns {Promise}
 */
export function toggleApiStatus(id) {
  return request({
    url: `/mock-apis/${id}/toggle`,
    method: 'put'
  })
}

/**
 * 添加接口响应
 * @param {Number} apiId 接口ID
 * @param {Object} data 响应数据
 * @returns {Promise}
 */
export function addApiResponse(apiId, data) {
  return request({
    url: `/mock-apis/${apiId}/responses`,
    method: 'post',
    data
  })
}

/**
 * 更新接口响应
 * @param {Object} data 响应数据
 * @returns {Promise}
 */
export function updateApiResponse(data) {
  return request({
    url: '/mock-apis/responses',
    method: 'put',
    data
  })
}

/**
 * 删除接口响应
 * @param {Number} responseId 响应ID
 * @returns {Promise}
 */
export function deleteApiResponse(responseId) {
  return request({
    url: `/mock-apis/responses/${responseId}`,
    method: 'delete'
  })
}

/**
 * 导出多个接口为 Markdown 文档
 * @param {Array<Number>} apiIds 接口ID列表
 * @param {Boolean} aiEnhance 是否启用 AI 增强（手动控制）
 * @returns {Promise<Array<String>>} 导出过程中的警告信息列表（如 AI 增强异常提示）
 */
export async function exportMockApisMarkdown(apiIds, aiEnhance = false) {
  const response = await request({
    url: '/mock-apis/export-markdown',
    method: 'post',
    data: { apiIds, aiEnhance },
    responseType: 'blob'
  })
  const blob = response.data || response
  const filename = aiEnhance ? 'api-docs-ai-enhanced.md' : 'api-docs.md'
  downloadBlob(blob, filename)

  // 解析后端通过响应头返回的导出警告（如 AI 增强调用异常、输出不合规时的友好提示）
  let warnings = []
  try {
    const headers = response.headers || {}
    const raw = headers['x-export-warnings']
    if (raw) {
      warnings = JSON.parse(decodeURIComponent(raw))
    }
  } catch (error) {
    console.error('解析导出警告信息失败:', error)
  }
  return Array.isArray(warnings) ? warnings : []
}
