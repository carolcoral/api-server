/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.util;

import java.util.Base64;
import java.util.Locale;
import java.util.Map;

/**
 * OIDC 登录图标工具类
 * <p>负责校验与规范化「data URI 形式的图标」：支持 svg / png / jpg(jpeg) / ico（icon）四种格式，
 * 统一约束体积上限，保证入库数据合法。图标随服务商配置持久化在系统配置表中，
 * 读取接口按 data URI 原样返回，由浏览器直接渲染。</p>
 *
 * @author carolcoral
 */
public final class OidcIconUtil {

    /** 图标体积上限（解码后字节数，256KB） */
    public static final int MAX_ICON_BYTES = 256 * 1024;

    /** data URI 长度上限（Base64 膨胀约 4/3，另留少量前缀余量） */
    public static final int MAX_DATA_URI_LENGTH = (MAX_ICON_BYTES / 3 + 1) * 4 + 64;

    /** MIME -> 允许的扩展名（用于前端上传校验提示） */
    public static final Map<String, String> SUPPORTED_TYPES = Map.of(
            "image/svg+xml", "svg",
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/x-icon", "ico"
    );

    private OidcIconUtil() {
    }

    /**
     * 校验并规范化 data URI
     *
     * @param raw 原始 data URI 字符串
     * @return 规范化后的 data URI（去掉空白）；非法或超限返回 null
     */
    public static String normalizeDataUri(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty() || value.length() > MAX_DATA_URI_LENGTH) {
            return null;
        }
        int comma = value.indexOf(',');
        if (!value.startsWith("data:") || comma < 0) {
            return null;
        }
        String header = value.substring(5, comma);
        String payload = value.substring(comma + 1);
        if (payload.isEmpty()) {
            return null;
        }
        boolean base64 = false;
        String mime = header;
        int semicolon = header.indexOf(';');
        if (semicolon >= 0) {
            mime = header.substring(0, semicolon);
            base64 = header.substring(semicolon + 1).toLowerCase(Locale.ROOT).contains("base64");
        }
        mime = mime.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.containsKey(mime)) {
            return null;
        }
        byte[] decoded;
        try {
            decoded = base64
                    ? Base64.getDecoder().decode(payload)
                    : payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (decoded.length == 0 || decoded.length > MAX_ICON_BYTES) {
            return null;
        }
        return value;
    }

    /**
     * 解析 data URI 的 MIME 类型
     *
     * @param dataUri data URI
     * @return MIME 类型；无法解析返回 {@code application/octet-stream}
     */
    public static String mimeOf(String dataUri) {
        if (dataUri == null) {
            return "application/octet-stream";
        }
        String value = dataUri.trim();
        int comma = value.indexOf(',');
        if (!value.startsWith("data:") || comma < 0) {
            return "application/octet-stream";
        }
        String header = value.substring(5, comma);
        int semicolon = header.indexOf(';');
        String mime = (semicolon >= 0 ? header.substring(0, semicolon) : header).trim().toLowerCase(Locale.ROOT);
        return mime.isEmpty() ? "application/octet-stream" : mime;
    }

    /**
     * 从文件名推断 data URI 的 MIME 类型
     *
     * @param fileName 文件名（如 logo.svg）
     * @return MIME 类型；不支持的扩展名返回 null
     */
    public static String mimeOfFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        String lower = fileName.trim().toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String ext = lower.substring(dot + 1);
        return switch (ext) {
            case "svg" -> "image/svg+xml";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "ico", "icon" -> "image/x-icon";
            default -> null;
        };
    }
}
