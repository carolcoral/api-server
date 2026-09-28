/*
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import {
  OIDC_ICON_EXTENSIONS,
  OIDC_ICON_ACCEPT,
  OIDC_ICON_MAX_BYTES,
  fileExtension,
  isSupportedIconFile,
  readIconAsDataUri,
  estimateDataUriBytes
} from '@/utils/oidcIcon'
import { oidcIconUrl } from '@/api/auth'

/** 构造一个受支持的图标文件对象 */
const makeFile = (name, size = 100, type = '') => ({ name, size, type })

describe('utils/oidcIcon', () => {
  describe('扩展名与白名单', () => {
    it('白名单覆盖 svg/png/jpg/ico', () => {
      expect(OIDC_ICON_EXTENSIONS).toEqual(expect.arrayContaining(['svg', 'png', 'jpg', 'jpeg', 'ico', 'icon']))
      expect(OIDC_ICON_ACCEPT).toContain('.svg')
      expect(OIDC_ICON_ACCEPT).toContain('image/x-icon')
    })

    it('fileExtension 取小写扩展名', () => {
      expect(fileExtension('Logo.SVG')).toBe('svg')
      expect(fileExtension('a.b.png')).toBe('png')
      expect(fileExtension('noext')).toBe('')
      expect(fileExtension(null)).toBe('')
      expect(fileExtension(123)).toBe('')
    })

    it('isSupportedIconFile 仅放行白名单扩展名', () => {
      expect(isSupportedIconFile('logo.svg')).toBe(true)
      expect(isSupportedIconFile('logo.PNG')).toBe(true)
      expect(isSupportedIconFile('logo.jpeg')).toBe(true)
      expect(isSupportedIconFile('logo.ico')).toBe(true)
      expect(isSupportedIconFile('logo.icon')).toBe(true)
      expect(isSupportedIconFile('logo.gif')).toBe(false)
      expect(isSupportedIconFile('logo.webp')).toBe(false)
      expect(isSupportedIconFile('logo')).toBe(false)
    })
  })

  describe('estimateDataUriBytes', () => {
    it('按 Base64 长度反推字节数', () => {
      expect(estimateDataUriBytes('data:image/png;base64,AAAA')).toBe(3)
      expect(estimateDataUriBytes('data:image/png;base64,')).toBe(0)
      expect(estimateDataUriBytes('invalid')).toBe(0)
      expect(estimateDataUriBytes(null)).toBe(0)
    })
  })

  describe('readIconAsDataUri', () => {
    beforeEach(() => {
      // jsdom 未实现 FileReader.readAsDataURL，这里用最小桩实现
      class FakeFileReader {
        readAsDataURL(file) {
          this.result = `data:${file.type || 'application/octet-stream'};base64,${file.base64 || 'AAAA'}`
          setTimeout(() => this.onload && this.onload(), 0)
        }
      }
      vi.stubGlobal('FileReader', FakeFileReader)
    })

    afterEach(() => {
      vi.unstubAllGlobals()
    })

    it('未选择文件时报错', async () => {
      await expect(readIconAsDataUri(null)).rejects.toThrow('未选择文件')
    })

    it('不支持的格式直接被拒绝', async () => {
      await expect(readIconAsDataUri(makeFile('logo.gif', 10))).rejects.toThrow('unsupported')
    })

    it('超过体积上限直接被拒绝', async () => {
      await expect(readIconAsDataUri(makeFile('logo.png', OIDC_ICON_MAX_BYTES + 1))).rejects.toThrow('tooLarge')
    })

    it('按扩展名改写 MIME 并返回 data URI', async () => {
      const res = await readIconAsDataUri(makeFile('logo.SVG', 10, 'text/xml'))
      expect(res.name).toBe('logo.SVG')
      expect(res.dataUri.startsWith('data:image/svg+xml;base64,')).toBe(true)
    })

    it('ico 映射为 image/x-icon，jpeg 映射为 image/jpeg', async () => {
      const ico = await readIconAsDataUri(makeFile('favicon.ico', 10))
      expect(ico.dataUri.startsWith('data:image/x-icon;base64,')).toBe(true)
      const jpg = await readIconAsDataUri(makeFile('photo.jpeg', 10))
      expect(jpg.dataUri.startsWith('data:image/jpeg;base64,')).toBe(true)
    })

    it('data URI 缺少分隔符时报错', async () => {
      class BrokenReader {
        readAsDataURL() {
          this.result = 'broken'
          setTimeout(() => this.onload && this.onload(), 0)
        }
      }
      vi.stubGlobal('FileReader', BrokenReader)
      await expect(readIconAsDataUri(makeFile('logo.png', 10))).rejects.toThrow('invalid')
    })
  })
})

describe('api/auth oidcIconUrl', () => {
  it('生成公开图标地址并对标识做编码', () => {
    expect(oidcIconUrl('keycloak')).toBe('/api/public/oidc/keycloak/icon')
    expect(oidcIconUrl('a b')).toBe('/api/public/oidc/a%20b/icon')
  })
})
