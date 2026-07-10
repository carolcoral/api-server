/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import request from '@/utils/request'

/**
 * 获取请求日志列表
 */
export function getRequestLogs(params) {
  return request({
    url: '/request-logs/list',
    method: 'get',
    params
  })
}

/**
 * 获取响应延迟分布
 */
export function getDelayDistribution(params) {
  return request({
    url: '/request-logs/delay-distribution',
    method: 'get',
    params
  })
}

/**
 * 获取请求概览
 */
export function getRequestOverview(params) {
  return request({
    url: '/request-logs/overview',
    method: 'get',
    params
  })
}
