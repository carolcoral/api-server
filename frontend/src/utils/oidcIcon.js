/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

/**
 * OIDC 服务商登录图标工具
 *
 * 约定：图标以 data URI 形式随服务商配置一并持久化到数据库（见 docs/ENGINEERING-RULES.md 第四节），
 * 因此前端只做「选文件 → 转 data URI → 校验体积/格式」这一步，不做上传接口中转。
 */

/** 支持的扩展名（与后端 OidcIconUtil 保持一致） */
export const OIDC_ICON_EXTENSIONS = ['svg', 'png', 'jpg', 'jpeg', 'ico', 'icon']

/** accept 属性值，供 <input type="file"> 使用 */
export const OIDC_ICON_ACCEPT = '.svg,.png,.jpg,.jpeg,.ico,.icon,image/svg+xml,image/png,image/jpeg,image/x-icon'

/** 图标体积上限（256KB，与后端 OidcIconUtil.MAX_ICON_BYTES 一致） */
export const OIDC_ICON_MAX_BYTES = 256 * 1024

/** 扩展名 → MIME */
const MIME_BY_EXT = {
  svg: 'image/svg+xml',
  png: 'image/png',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  ico: 'image/x-icon',
  icon: 'image/x-icon'
}

/**
 * 取文件扩展名（小写，不含点）
 * @param {string} fileName 文件名
 * @returns {string} 扩展名，无则空串
 */
export const fileExtension = (fileName) => {
  if (!fileName || typeof fileName !== 'string') return ''
  const dot = fileName.lastIndexOf('.')
  return dot < 0 ? '' : fileName.slice(dot + 1).toLowerCase()
}

/**
 * 判断文件名是否为受支持的图标格式
 * @param {string} fileName 文件名
 * @returns {boolean} 是否支持
 */
export const isSupportedIconFile = (fileName) => OIDC_ICON_EXTENSIONS.includes(fileExtension(fileName))

/**
 * 校验并读取图标文件为 data URI
 * @param {File} file 用户选择的文件
 * @returns {Promise<{ dataUri: string, name: string, size: number }>} 读取结果
 * @throws {Error} 格式不支持或体积超限时抛出（message 为可直接展示的提示）
 */
export const readIconAsDataUri = (file) => {
  return new Promise((resolve, reject) => {
    if (!file) {
      reject(new Error('未选择文件'))
      return
    }
    if (!isSupportedIconFile(file.name)) {
      reject(new Error('unsupported'))
      return
    }
    if (file.size > OIDC_ICON_MAX_BYTES) {
      reject(new Error('tooLarge'))
      return
    }
    const ext = fileExtension(file.name)
    const mime = MIME_BY_EXT[ext] || file.type || 'application/octet-stream'
    const reader = new FileReader()
    reader.onload = () => {
      const result = String(reader.result || '')
      const comma = result.indexOf(',')
      if (comma < 0) {
        reject(new Error('invalid'))
        return
      }
      // 统一改写 MIME，避免浏览器给出 application/octet-stream 等不被后端白名单接受的类型
      const dataUri = `data:${mime};base64,${result.slice(comma + 1)}`
      resolve({ dataUri, name: file.name, size: file.size })
    }
    reader.onerror = () => reject(new Error('invalid'))
    reader.readAsDataURL(file)
  })
}

/**
 * 估算 data URI 的原始字节数（用于回显体积，不便于精确解码时近似即可）
 * @param {string} dataUri data URI
 * @returns {number} 近似字节数
 */
export const estimateDataUriBytes = (dataUri) => {
  if (!dataUri || typeof dataUri !== 'string') return 0
  const comma = dataUri.indexOf(',')
  if (comma < 0) return 0
  const payload = dataUri.slice(comma + 1)
  return Math.floor((payload.length * 3) / 4)
}
