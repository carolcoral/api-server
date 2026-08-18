/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.filter;

import com.carolcoral.apiserver.service.SystemConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * iframe 嵌入来源控制过滤器
 * <p>
 * 根据系统配置 {@code iframeAllowedOrigins}（可在"系统设置 - 安全配置"中维护）动态生成
 * {@code Content-Security-Policy: frame-ancestors ...} 响应头，控制当前页面可被哪些来源 iframe 嵌入：
 * <ul>
 *   <li>未配置或为空：{@code frame-ancestors 'none'}，禁止被任何页面 iframe 嵌入（默认，最安全）</li>
 *   <li>配置为 {@code *}：不设置该响应头，允许所有来源嵌入</li>
 *   <li>配置为逗号分隔的 Origin 列表：仅允许同源及列表中来源嵌入，如
 *       {@code https://a.example.com,https://b.example.com}</li>
 * </ul>
 * 使用 CSP {@code frame-ancestors} 而非 {@code X-Frame-Options}，是因为后者仅支持
 * DENY/SAMEORIGIN，无法表达"允许特定来源"。
 * </p>
 *
 * @author carolcoral
 */
public class FrameOptionsHeaderFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FrameOptionsHeaderFilter.class);

    /**
     * 系统配置键：允许被 iframe 嵌入的来源（逗号分隔，* 表示全部，空表示禁止）
     */
    public static final String CONFIG_KEY_IFRAME_ALLOWED_ORIGINS = "iframeAllowedOrigins";

    private final SystemConfigService systemConfigService;

    /**
     * 构造器
     *
     * @param systemConfigService 系统配置服务
     */
    public FrameOptionsHeaderFilter(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String config = systemConfigService.getConfig(CONFIG_KEY_IFRAME_ALLOWED_ORIGINS);
            String csp = buildFrameAncestors(config);
            if (csp != null) {
                response.setHeader("Content-Security-Policy", csp);
            }
        } catch (Exception e) {
            // 配置读取异常时保持默认禁止策略，保证安全
            log.warn("读取 iframe 白名单配置失败，使用默认禁止策略: {}", e.getMessage());
            response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 根据配置生成 CSP frame-ancestors 指令
     *
     * @param config 配置值（逗号分隔的 Origin 列表；* 表示全部；空表示禁止）
     * @return CSP 指令文本；返回 {@code null} 表示完全放行，不设置该头
     */
    private String buildFrameAncestors(String config) {
        if (config == null || config.isBlank()) {
            return "frame-ancestors 'none'";
        }
        String trimmed = config.trim();
        if ("*".equals(trimmed)) {
            return null;
        }
        StringBuilder sb = new StringBuilder("frame-ancestors 'self'");
        for (String origin : trimmed.split(",")) {
            origin = origin.trim();
            if (!origin.isEmpty()) {
                sb.append(' ').append(origin);
            }
        }
        return sb.toString();
    }
}
