/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import request from '@/utils/request'

const BASE = '/admin/ai'

// ==================== 服务商 ====================

export function listProviders() {
  return request({ url: `${BASE}/providers`, method: 'get' })
}

export function createProvider(data) {
  return request({ url: `${BASE}/providers`, method: 'post', data })
}

export function updateProvider(id, data) {
  return request({ url: `${BASE}/providers/${id}`, method: 'put', data })
}

export function deleteProvider(id) {
  return request({ url: `${BASE}/providers/${id}`, method: 'delete' })
}

// ==================== 模型 ====================

export function listModels(providerId) {
  return request({ url: `${BASE}/providers/${providerId}/models`, method: 'get' })
}

export function createModel(providerId, data) {
  return request({ url: `${BASE}/providers/${providerId}/models`, method: 'post', data })
}

export function updateModel(providerId, id, data) {
  return request({ url: `${BASE}/providers/${providerId}/models/${id}`, method: 'put', data })
}

export function deleteModel(providerId, id) {
  return request({ url: `${BASE}/providers/${providerId}/models/${id}`, method: 'delete' })
}

export function fetchProviderModels(providerId) {
  return request({ url: `${BASE}/providers/${providerId}/fetch-models`, method: 'post' })
}

export function batchCreateModels(providerId, modelNames) {
  return request({ url: `${BASE}/providers/${providerId}/models/batch`, method: 'post', data: modelNames })
}

export function getModelsHealth() {
  return request({ url: `${BASE}/models/health`, method: 'get' })
}

export function healthCheckModel(id) {
  return request({ url: `${BASE}/models/${id}/health-check`, method: 'post' })
}

// ==================== 订阅 ====================

export function listSubscriptions(params) {
  return request({ url: `${BASE}/subscriptions`, method: 'get', params })
}

export function createSubscription(data) {
  return request({ url: `${BASE}/subscriptions`, method: 'post', data })
}

export function updateSubscription(id, data) {
  return request({ url: `${BASE}/subscriptions/${id}`, method: 'put', data })
}

export function updateSubscriptionPriority(id, data) {
  return request({ url: `${BASE}/subscriptions/${id}/priority`, method: 'put', data })
}

export function deleteSubscription(id) {
  return request({ url: `${BASE}/subscriptions/${id}`, method: 'delete' })
}

// ==================== 额度 ====================

export function listQuotas(params) {
  return request({ url: `${BASE}/quotas`, method: 'get', params })
}

export function createQuota(data) {
  return request({ url: `${BASE}/quotas`, method: 'post', data })
}

export function updateQuota(id, data) {
  return request({ url: `${BASE}/quotas/${id}`, method: 'put', data })
}

export function deleteQuota(id) {
  return request({ url: `${BASE}/quotas/${id}`, method: 'delete' })
}

// ==================== API Key ====================

export function listApiKeys(params) {
  return request({ url: `${BASE}/api-keys`, method: 'get', params })
}

export function createApiKey(data) {
  return request({ url: `${BASE}/api-keys`, method: 'post', data })
}

export function deleteApiKey(id) {
  return request({ url: `${BASE}/api-keys/${id}`, method: 'delete' })
}

// ==================== 用户列表（供下拉选择） ====================

export function listAiUsers() {
  return request({ url: `${BASE}/users`, method: 'get' })
}

// ==================== 统计 ====================

export function getAiStatistics() {
  return request({ url: `${BASE}/statistics`, method: 'get' })
}

export function getUsageLogs(params) {
  return request({ url: `${BASE}/usage-logs`, method: 'get', params })
}

export function getFallbackLogs(params) {
  return request({ url: `${BASE}/fallback-logs`, method: 'get', params })
}
