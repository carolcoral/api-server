/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OidcIconUtil 单元测试：图标 data URI 的格式白名单、体积上限与 MIME 解析。
 */
class OidcIconUtilTest {

    private static String dataUri(String mime, byte[] payload) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(payload);
    }

    @Nested
    @DisplayName("normalizeDataUri")
    class Normalize {

        @Test
        @DisplayName("四种受支持格式均可通过")
        void supported() {
            assertNotNull(OidcIconUtil.normalizeDataUri(
                    dataUri("image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8))));
            assertNotNull(OidcIconUtil.normalizeDataUri(dataUri("image/png", new byte[]{1, 2})));
            assertNotNull(OidcIconUtil.normalizeDataUri(dataUri("image/jpeg", new byte[]{1, 2})));
            assertNotNull(OidcIconUtil.normalizeDataUri(dataUri("image/x-icon", new byte[]{1, 2})));
        }

        @Test
        @DisplayName("去掉首尾空白后返回")
        void trims() {
            String uri = dataUri("image/png", new byte[]{1});
            assertEquals(uri, OidcIconUtil.normalizeDataUri("  " + uri + "\n"));
        }

        @Test
        @DisplayName("非 data URI / 空值 / 空载荷一律拒绝")
        void rejectsMalformed() {
            assertNull(OidcIconUtil.normalizeDataUri(null));
            assertNull(OidcIconUtil.normalizeDataUri(""));
            assertNull(OidcIconUtil.normalizeDataUri("https://a.fan/logo.png"));
            assertNull(OidcIconUtil.normalizeDataUri("data:image/png;base64"));
            assertNull(OidcIconUtil.normalizeDataUri("data:image/png;base64,"));
        }

        @Test
        @DisplayName("白名单外的 MIME 被拒绝")
        void rejectsUnsupportedMime() {
            assertNull(OidcIconUtil.normalizeDataUri(dataUri("image/gif", new byte[]{1})));
            assertNull(OidcIconUtil.normalizeDataUri(dataUri("text/html", new byte[]{1})));
            assertNull(OidcIconUtil.normalizeDataUri("data:;base64,AAAA"));
        }

        @Test
        @DisplayName("Base64 非法时被拒绝")
        void rejectsBadBase64() {
            assertNull(OidcIconUtil.normalizeDataUri("data:image/png;base64,!!!!"));
        }

        @Test
        @DisplayName("超过体积上限被拒绝")
        void rejectsOversize() {
            byte[] big = new byte[OidcIconUtil.MAX_ICON_BYTES + 1];
            assertNull(OidcIconUtil.normalizeDataUri(dataUri("image/png", big)));
        }

        @Test
        @DisplayName("恰好等于体积上限可通过")
        void acceptsAtLimit() {
            byte[] exact = new byte[OidcIconUtil.MAX_ICON_BYTES];
            assertNotNull(OidcIconUtil.normalizeDataUri(dataUri("image/png", exact)));
        }

        @Test
        @DisplayName("长度超过上限直接拒绝，不解析")
        void rejectsTooLong() {
            StringBuilder sb = new StringBuilder("data:image/png;base64,");
            sb.append("A".repeat(OidcIconUtil.MAX_DATA_URI_LENGTH));
            assertNull(OidcIconUtil.normalizeDataUri(sb.toString()));
        }

        @Test
        @DisplayName("非 Base64 的 SVG 明文 data URI 也支持")
        void plainSvg() {
            String uri = "data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg'/>";
            assertEquals(uri, OidcIconUtil.normalizeDataUri(uri));
        }
    }

    @Nested
    @DisplayName("mimeOf")
    class Mime {

        @Test
        @DisplayName("解析 data URI 头部的 MIME")
        void fromDataUri() {
            assertEquals("image/png", OidcIconUtil.mimeOf(dataUri("image/png", new byte[]{1})));
            assertEquals("image/svg+xml", OidcIconUtil.mimeOf("data:image/svg+xml;base64,AAAA"));
            assertEquals("image/x-icon", OidcIconUtil.mimeOf("data:image/x-icon;base64,AAAA"));
        }

        @Test
        @DisplayName("非 data URI 回退为 octet-stream")
        void fallback() {
            assertEquals("application/octet-stream", OidcIconUtil.mimeOf(null));
            assertEquals("application/octet-stream", OidcIconUtil.mimeOf("nope"));
            assertEquals("application/octet-stream", OidcIconUtil.mimeOf("data:;base64,AAAA"));
        }
    }

    @Nested
    @DisplayName("mimeOfFileName")
    class MimeByFileName {

        @Test
        @DisplayName("按扩展名映射 MIME，大小写不敏感")
        void maps() {
            assertEquals("image/svg+xml", OidcIconUtil.mimeOfFileName("logo.SVG"));
            assertEquals("image/png", OidcIconUtil.mimeOfFileName("a.png"));
            assertEquals("image/jpeg", OidcIconUtil.mimeOfFileName("a.jpg"));
            assertEquals("image/jpeg", OidcIconUtil.mimeOfFileName("a.jpeg"));
            assertEquals("image/x-icon", OidcIconUtil.mimeOfFileName("a.ico"));
            assertEquals("image/x-icon", OidcIconUtil.mimeOfFileName("a.icon"));
        }

        @Test
        @DisplayName("不支持或无扩展名返回 null")
        void unsupported() {
            assertNull(OidcIconUtil.mimeOfFileName(null));
            assertNull(OidcIconUtil.mimeOfFileName("logo"));
            assertNull(OidcIconUtil.mimeOfFileName("logo.webp"));
        }
    }
}
