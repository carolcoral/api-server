/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.config;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 公开配置Controller（无需认证）
 * 用于前端登录页/注册页获取公开配置信息
 *
 * @author carolcoral
 * @since 2026-06-12
 */
@Tag(name = "公开配置", description = "无需认证的公开配置接口")
@RestController
@RequestMapping("/api/public")
public class PublicConfigController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PublicConfigController.class);

    private final SystemConfigService systemConfigService;

    public PublicConfigController(SystemConfigService systemConfigService,
                                  com.carolcoral.apiserver.service.OidcService oidcService) {
        this.systemConfigService = systemConfigService;
        this.oidcService = oidcService;
    }

    private final com.carolcoral.apiserver.service.OidcService oidcService;

    /**
     * 获取公开系统配置（无需认证）
     * <p>返回注册功能开关状态等公开配置信息。</p>
     *
     * @return 公开配置信息
     */
    @GetMapping("/system-config")
    @Operation(summary = "获取公开系统配置")
    public ApiResponse<Map<String, Object>> getPublicConfig(
            jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enableRegistration", parseBooleanConfig("enableRegistration", false));
        config.put("enableEmailVerification", parseBooleanConfig("enableEmailVerification", false));
        // OIDC 登录公开配置，供登录页渲染各服务商登录入口（不含敏感信息）
        // 按实际访问地址补全图标绝对地址：登录页与后端可能不同域，相对路径会 404 导致图标不显示
        com.carolcoral.apiserver.dto.oidc.OidcPublicConfigDTO oidc =
                oidcService.getPublicConfig(resolveBaseUrl(request));
        config.put("oidcEnabled", oidc.getEnabled());
        config.put("oidcProviders", oidc.getProviders());
        // 兼容旧前端字段：首个可用服务商作为默认入口
        if (!oidc.getProviders().isEmpty()) {
            com.carolcoral.apiserver.dto.oidc.OidcPublicProviderDTO first = oidc.getProviders().get(0);
            config.put("oidcProvider", first.getProviderId());
            config.put("oidcButtonLabel", first.getButtonLabel());
        } else {
            config.put("oidcProvider", null);
            config.put("oidcButtonLabel", null);
        }
        return ApiResponse.success(config);
    }

    /**
     * 读取 OIDC 服务商登录图标（无需认证）
     * <p>登录页按 {@code oidcProviders[].iconUrl} 加载该接口渲染图标；
     * 未配置图标时返回 404，前端回退到内置默认图标。</p>
     *
     * @param providerId 服务商标识
     * @return 图标响应（data URI 原样返回，浏览器可直接渲染）
     */
    @GetMapping("/oidc/{providerId}/icon")
    @Operation(summary = "读取 OIDC 服务商登录图标")
    public org.springframework.http.ResponseEntity<byte[]> getOidcProviderIcon(
            @org.springframework.web.bind.annotation.PathVariable("providerId") String providerId) {
        String dataUri = oidcService.getProviderIcon(providerId);
        if (dataUri == null) {
            return org.springframework.http.ResponseEntity.notFound().build();
        }
        String mime = com.carolcoral.apiserver.util.OidcIconUtil.mimeOf(dataUri);
        byte[] body = dataUri.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, mime + ";charset=UTF-8")
                // 图标随配置变更而变，缓存 1 小时，降低登录页请求压力
                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .body(body);
    }

    /**
     * 从请求推导站点基础地址（scheme://host[:port]）
     * <p>与 {@code AuthController.resolveBaseUrl} 保持同一套优先级：
     * {@code Origin} → {@code X-Forwarded-Host}/{@code X-Forwarded-Host} → {@code Host}，
     * 协议协商顺序为 {@code X-Forwarded-Proto} → {@code Origin}/{@code Referer} → 容器 scheme。
     * 仅用于把公开接口中的相对资源地址（如 OIDC 图标）补成绝对地址。</p>
     *
     * @param request HTTP 请求
     * @return 站点基础地址；无法推导时为空串
     */
    private String resolveBaseUrl(jakarta.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String origin = firstNonBlank(request.getHeader("Origin"), request.getHeader("X-Forwarded-Origin"));
        if (origin != null && !"null".equalsIgnoreCase(origin)) {
            return stripTrailingSlash(origin);
        }
        String forwardedHost = firstSegment(firstNonBlank(request.getHeader("X-Forwarded-Host"),
                request.getHeader("X-Forwarded-Server")));
        if (forwardedHost == null) {
            forwardedHost = firstSegment(request.getHeader("Host"));
        }
        String scheme = resolveScheme(request);
        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            return stripTrailingSlash(scheme + "://" + forwardedHost);
        }
        String host = request.getServerName();
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return stripTrailingSlash(scheme + "://" + host + (defaultPort ? "" : ":" + port));
    }

    /**
     * 协商站点实际协议：X-Forwarded-Proto → Origin/Referer → Forwarded → 容器 scheme
     */
    private String resolveScheme(jakarta.servlet.http.HttpServletRequest request) {
        String scheme = schemeOf(firstSegment(request.getHeader("X-Forwarded-Proto")));
        if (scheme == null) {
            scheme = schemeOf(firstNonBlank(request.getHeader("Origin"),
                    request.getHeader("X-Forwarded-Origin"), request.getHeader("Referer")));
        }
        if (scheme == null) {
            scheme = schemeOf(request.getScheme());
        }
        return scheme != null ? scheme : "http";
    }

    /**
     * 从 URL / 协议串提取 scheme
     */
    private String schemeOf(String value) {
        String v = firstNonBlank(value);
        if (v == null) {
            return null;
        }
        int idx = v.indexOf("://");
        if (idx > 0) {
            v = v.substring(0, idx);
        }
        v = v.trim().toLowerCase(java.util.Locale.ROOT);
        return ("http".equals(v) || "https".equals(v)) ? v : null;
    }

    /**
     * 取逗号分隔头部的第一段
     */
    private String firstSegment(String value) {
        if (value == null) {
            return null;
        }
        int comma = value.indexOf(',');
        String segment = (comma >= 0 ? value.substring(0, comma) : value).trim();
        return segment.isEmpty() ? null : segment;
    }

    /**
     * 返回首个非空白值
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 去掉末尾斜杠
     */
    private String stripTrailingSlash(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean parseBooleanConfig(String key, boolean defaultValue) {
        String value = systemConfigService.getConfig(key);
        if (value != null && !value.isEmpty()) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }
}
