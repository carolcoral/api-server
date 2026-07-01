/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import request from '@/utils/request'

/**
 * 获取支持的模板函数列表
 * @returns {Promise}
 */
export function getTemplateFunctions() {
  return request({
    url: '/mock-template/functions',
    method: 'get'
  })
}

/**
 * 预览模板渲染结果
 * @param {string} template 模板内容
 * @returns {Promise}
 */
export function previewTemplate(template) {
  return request({
    url: '/mock-template/preview',
    method: 'post',
    data: { template }
  })
}

/**
 * 批量预览模板渲染结果
 * @param {string} template 模板内容
 * @param {number} count 生成数量
 * @returns {Promise}
 */
export function previewTemplateBatch(template, count = 5) {
  return request({
    url: '/mock-template/preview/batch',
    method: 'post',
    data: { template, count }
  })
}
