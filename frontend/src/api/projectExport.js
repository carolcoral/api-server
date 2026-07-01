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
 * 导出项目 API 为 Swagger/OpenAPI 格式
 * @param {Number} projectId 项目ID
 * @param {String} version OpenAPI 版本 ("2.0" 或 "3.0")
 * @returns {Promise}
 */
export async function exportSwagger(projectId, version = '3.0') {
  const response = await request({
    url: `/projects/${projectId}/export-swagger`,
    method: 'get',
    params: { version },
    responseType: 'blob'
  })
  const blob = response.data || response
  const filename = version === '2.0' ? 'swagger.json' : 'openapi.json'
  downloadBlob(blob, filename)
}

/**
 * 导出项目完整数据为 JSON 格式
 * @param {Number} projectId 项目ID
 * @returns {Promise}
 */
export async function exportProjectData(projectId) {
  const response = await request({
    url: `/projects/${projectId}/export-data`,
    method: 'get',
    responseType: 'blob'
  })
  const blob = response.data || response
  downloadBlob(blob, `project-${projectId}-export.json`)
}

/**
 * 从 JSON 文件导入项目数据（自动创建项目）
 * 若 projectId 为 null，使用自动创建项目的端点
 * @param {Number|null} projectId 项目ID（null 表示自动创建项目）
 * @param {File} file JSON 文件
 * @param {String} mode 导入模式 ("merge" 或 "replace")
 * @returns {Promise}
 */
export function importProjectData(projectId, file, mode = 'merge') {
  const formData = new FormData()
  formData.append('file', file)
  const url = projectId != null
    ? `/projects/${projectId}/import-data`
    : '/projects/import-data'
  return request({
    url,
    method: 'post',
    data: formData,
    params: { mode },
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}
