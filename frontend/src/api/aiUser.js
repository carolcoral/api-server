/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import request from '@/utils/request'

const BASE = '/user/ai'

// ==================== 服务商和模型（用户视角） ====================

export function listAvailableProviders() {
  return request({ url: `${BASE}/providers`, method: 'get' })
}

export function listAvailableModels(providerId) {
  return request({ url: `${BASE}/providers/${providerId}/models`, method: 'get' })
}

// ==================== 我的订阅 ====================

export function listMySubscriptions() {
  return request({ url: `${BASE}/subscriptions`, method: 'get' })
}

export function subscribeModel(modelId) {
  return request({ url: `${BASE}/subscriptions`, method: 'post', data: { modelId } })
}

export function unsubscribeModel(id) {
  return request({ url: `${BASE}/subscriptions/${id}`, method: 'delete' })
}

// ==================== 我的 API Keys ====================

export function listMyApiKeys() {
  return request({ url: `${BASE}/api-keys`, method: 'get' })
}

export function createMyApiKey(keyName) {
  return request({ url: `${BASE}/api-keys`, method: 'post', data: { keyName } })
}

export function deleteMyApiKey(id) {
  return request({ url: `${BASE}/api-keys/${id}`, method: 'delete' })
}
