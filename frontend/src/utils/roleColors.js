/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

/**
 * 角色配色工具：基于角色名稳定映射到固定色板，保证离线可用（无任何外部资源依赖）。
 */

// Issue #13 指定的角色背景色板（20 色），字体颜色固定为黑色
export const ROLE_COLORS = [
  'rgb(218, 149, 148)',
  'rgb(236, 207, 192)',
  'rgb(235, 213, 186)',
  'rgb(241, 237, 207)',
  'rgb(220, 230, 165)',
  'rgb(189, 229, 148)',
  'rgb(181, 228, 166)',
  'rgb(183, 237, 187)',
  'rgb(199, 233, 213)',
  'rgb(144, 224, 202)',
  'rgb(184, 231, 230)',
  'rgb(168, 205, 219)',
  'rgb(164, 190, 221)',
  'rgb(173, 179, 220)',
  'rgb(192, 185, 235)',
  'rgb(213, 191, 236)',
  'rgb(228, 191, 238)',
  'rgb(214, 143, 207)',
  'rgb(216, 155, 191)',
  'rgb(226, 167, 185)'
]

// 角色字体颜色固定为黑色（Issue #13 要求）
export const ROLE_TEXT_COLOR = '#000000'

// 简单字符串 hash：每个字符累加，保证相同输入得到相同 hash
const hashString = (str) => {
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = ((hash << 5) - hash + str.charCodeAt(i)) | 0
  }
  return Math.abs(hash)
}

/**
 * 根据角色名稳定选取色板中的背景色，保证同一角色颜色一致、不同角色颜色不同。
 * @param {string} roleName - 角色名称
 * @returns {string} RGB 颜色字符串
 */
export const getRoleColor = (roleName) => {
  const name = roleName || ''
  const idx = hashString(name) % ROLE_COLORS.length
  return ROLE_COLORS[idx]
}

/**
 * 生成角色标签样式对象：背景色取自色板，字体固定黑色。
 * @param {string} roleName - 角色名称
 * @returns {Object} Vue 内联 style 对象
 */
export const getRoleTagStyle = (roleName) => {
  const bgColor = getRoleColor(roleName)
  return {
    color: ROLE_TEXT_COLOR,
    backgroundColor: bgColor,
    borderColor: bgColor
  }
}
