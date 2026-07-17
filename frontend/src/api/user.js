/**
 * 用户管理 API
 */
import request from '@/utils/request'

const BASE = '/users'

/**
 * 搜索用户（支持用户名或邮箱模糊搜索）
 * @param {string} keyword 搜索关键词
 */
export function searchUsers(keyword) {
  return request({ url: `${BASE}/search`, method: 'get', params: { keyword } })
}

/**
 * 查询所有启用状态的用户
 */
export function listEnabledUsers() {
  return request({ url: `${BASE}/enabled`, method: 'get' })
}
