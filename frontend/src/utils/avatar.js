/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { getRoleColor, ROLE_TEXT_COLOR } from './roleColors'

/**
 * 本地头像生成器：基于用户名生成 SVG 头像（首字母 + 角色色板背景）。
 * 完全离线，零外部网络请求，保证离线环境可用。
 */

// SVG 特殊字符转义，防止用户名注入破坏 SVG 结构
const escapeXml = (str) => String(str).replace(/[<>&"']/g, (c) => ({
  '<': '&lt;',
  '>': '&gt;',
  '&': '&amp;',
  '"': '&quot;',
  "'": '&apos;'
}[c]))

// 取用户名首字母（支持中英文，取第一个字符），无则回退 '?'
const getInitial = (name) => {
  if (!name) return '?'
  const ch = String(name).trim().charAt(0)
  return ch || '?'
}

/**
 * 根据用户名生成 SVG 头像 data URI。
 * 背景色取自角色色板（按用户名稳定取色），字体固定黑色。
 * @param {string} name - 用户名或邮箱
 * @returns {string} SVG data URI
 */
export const generateAvatarDataUri = (name) => {
  const initial = getInitial(name).toUpperCase()
  const bg = getRoleColor(name)
  const size = 200
  const fontSize = Math.round(size * 0.45)
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}">` +
    `<rect width="${size}" height="${size}" rx="${size / 2}" ry="${size / 2}" fill="${bg}"/>` +
    `<text x="50%" y="50%" dy=".35em" text-anchor="middle" ` +
    `font-family="'PingFang SC', 'Microsoft YaHei', Arial, sans-serif" ` +
    `font-size="${fontSize}" font-weight="bold" fill="${ROLE_TEXT_COLOR}">${escapeXml(initial)}</text>` +
    `</svg>`
  return `data:image/svg+xml;utf8,${encodeURIComponent(svg)}`
}
