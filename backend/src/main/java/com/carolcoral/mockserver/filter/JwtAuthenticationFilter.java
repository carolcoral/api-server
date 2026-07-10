/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.mockserver.filter;

import com.carolcoral.mockserver.dto.ApiResponse;
import com.carolcoral.mockserver.entity.User;
import com.carolcoral.mockserver.repository.UserRepository;
import com.carolcoral.mockserver.service.CustomUserDetailsService;
import com.carolcoral.mockserver.util.JwtTokenUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * JWT认证过滤器
 *
 * @author carolcoral
 */
@Tag(name = "JWT过滤器", description = "JWT认证过滤器")
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * 构造器
     */
    public JwtAuthenticationFilter(JwtTokenUtil jwtTokenUtil,
        UserRepository userRepository,
        ObjectMapper objectMapper,
        CustomUserDetailsService customUserDetailsService) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.customUserDetailsService = customUserDetailsService;
    }

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MOCK_PATH_PREFIX = "/mock/";
    private static final String MOCK_SERVER_PATH_PREFIX = "/mock-server/";
    private static final String AUTH_LOGIN_PATH = "/auth/login";
    private static final String AUTH_REGISTER_PATH = "/auth/register";
    private static final String AUTH_SEND_VERIFICATION_CODE_PATH = "/auth/send-verification-code";
    private static final String AUTH_FORGOT_PASSWORD_PATH = "/auth/forgot-password";
    private static final String ACTUATOR_PATH_PREFIX = "/actuator/";

    private final JwtTokenUtil jwtTokenUtil;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CustomUserDetailsService customUserDetailsService;

    @Override
    @Operation(summary = "执行JWT认证过滤")
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        String servletPath = request.getServletPath(); // 去除context-path后的路径

        // 公开接口 - 直接放行，不设置认证（同时检查 requestUri 和 servletPath）
        if (isPublicPath(requestUri) || isPublicPath(servletPath)) {
            log.debug("公开路径放行: requestUri={}, servletPath={}", requestUri, servletPath);
            filterChain.doFilter(request, response);
            return;
        }

        // Mock接口路径 - 不需要认证
        if (servletPath.startsWith(MOCK_PATH_PREFIX) || requestUri.startsWith(MOCK_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Mock Server接口路径 - 不需要认证
        if (servletPath.startsWith(MOCK_SERVER_PATH_PREFIX) || requestUri.startsWith(MOCK_SERVER_PATH_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 其他接口 - 需要JWT认证
        log.info("需要认证的请求: requestUri={}, servletPath={}", requestUri, servletPath);
        String token = getTokenFromRequest(request);
        if (StringUtils.hasText(token)) {
            try {
                String username = jwtTokenUtil.getUsernameFromToken(token);
                Long userId = jwtTokenUtil.getUserIdFromToken(token);
                String role = jwtTokenUtil.getUserRoleFromToken(token);

                if (username != null && userId != null) {
                    UserDetails userDetails = userRepository.findByUsername(username).orElse(null);

                    if (userDetails != null && jwtTokenUtil.validateToken(token, userDetails)) {
                        // 加载动态权限（从 t_role_permission + t_permission）
                        if (userDetails instanceof User) {
                            customUserDetailsService.loadPermissions((User) userDetails);
                        }
                        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);

                        // 设置用户ID和角色到请求属性，方便后续使用
                        request.setAttribute("userId", userId);
                        request.setAttribute("userRole", User.UserRole.valueOf(role));
                        log.info("JWT认证成功: username={}, role={}, authorities={}",
                                username, role, userDetails.getAuthorities());
                    } else {
                        log.warn("JWT token验证失败或用户不存在");
                        writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                                ApiResponse.unauthorized());
                        return;
                    }
                } else {
                    log.warn("JWT token中无法获取用户名或用户ID");
                    writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                            ApiResponse.unauthorized());
                    return;
                }
            } catch (Exception e) {
                log.warn("JWT认证失败: {}", e.getMessage());
                writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ApiResponse.unauthorized());
                return;
            }
        } else {
            // 没有提供token，且不是公开接口，返回401未授权
            log.debug("请求没有提供JWT token: {}", requestUri);
            writeJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED,
                    ApiResponse.unauthorized());
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判断是否是公开路径（不需要认证）
     */
    private boolean isPublicPath(String requestUri) {
        // 前端静态资源 - 直接放行
        if (requestUri.equals("/") || requestUri.equals("/index.html") ||
            requestUri.equals("/favicon.ico") || requestUri.equals("/robots.txt") ||
            requestUri.equals("/USER_GUIDE.md") ||
            requestUri.equals("/CHANGELOG.md") ||
            requestUri.equals("/README.md") ||
            requestUri.equals("/README-US.md") ||
            requestUri.startsWith("/assets/") ||
            requestUri.startsWith("/badges/")) {
            return true;
        }

        // Bing 图片代理 - 直接放行
        if (requestUri.equals("/bing-hp")) {
            return true;
        }

        // 前端静态文件（图片、字体、CSS、JS） - 直接放行
        if (requestUri.matches("^/[^/]+\\.(jpg|jpeg|png|svg|gif|webp|ico|woff2?|ttf|css|js|map)$")) {
            return true;
        }

        // SPA 路由（Vue Router history 模式） - 直接放行，让前端处理
        if (requestUri.equals("/") ||
            requestUri.equals("/login") || requestUri.equals("/register") || requestUri.equals("/forgot-password") ||
            requestUri.equals("/dashboard") ||
            requestUri.equals("/home") || requestUri.equals("/projects") ||
            requestUri.startsWith("/projects/") || requestUri.equals("/apis") ||
            requestUri.equals("/users") || requestUri.equals("/settings") ||
            requestUri.equals("/statistics") || requestUri.equals("/debug-panel") ||
            requestUri.equals("/guide") ||
            requestUri.equals("/profile") || requestUri.equals("/code-templates") ||
            requestUri.equals("/changelog") ||
            requestUri.equals("/email-templates") ||
            requestUri.equals("/ai-settings") ||
            requestUri.equals("/ai-chat") ||
            requestUri.equals("/roles") ||
            requestUri.equals("/permissions")) {
            return true;
        }

        // API 公开接口
        return AUTH_LOGIN_PATH.equals(requestUri) ||
               ("/api" + AUTH_LOGIN_PATH).equals(requestUri) ||
               AUTH_REGISTER_PATH.equals(requestUri) ||
               ("/api" + AUTH_REGISTER_PATH).equals(requestUri) ||
               AUTH_SEND_VERIFICATION_CODE_PATH.equals(requestUri) ||
               ("/api" + AUTH_SEND_VERIFICATION_CODE_PATH).equals(requestUri) ||
               AUTH_FORGOT_PASSWORD_PATH.equals(requestUri) ||
               ("/api" + AUTH_FORGOT_PASSWORD_PATH).equals(requestUri) ||
               requestUri.startsWith(MOCK_SERVER_PATH_PREFIX) ||
               requestUri.startsWith("/api" + MOCK_SERVER_PATH_PREFIX) ||
               requestUri.startsWith(ACTUATOR_PATH_PREFIX) ||
               requestUri.startsWith("/api" + ACTUATOR_PATH_PREFIX) ||
               requestUri.startsWith("/api/public/") ||
               requestUri.equals("/error") ||
               requestUri.equals("/api/error") ||
               requestUri.equals("/system/version") ||
               requestUri.equals("/api/system/version");
    }

    /**
    /**
     * 从请求中获取令牌
     *
     * @param request 请求
     * @return 令牌
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        // 1. 尝试从Header获取
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        log.debug("Authorization Header: {}", bearerToken);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            String token = bearerToken.substring(BEARER_PREFIX.length());
            log.debug("从Header提取token: {}", token != null ? "token存在" : "token为空");
            return token;
        }

        // 2. 尝试从URL参数获取（用于Swagger等场景）
        String tokenParam = request.getParameter("token");
        if (StringUtils.hasText(tokenParam)) {
            log.debug("从URL参数提取token");
            return tokenParam;
        }

        log.warn("请求未提供token, URI: {}", request.getRequestURI());
        return null;
    }

    /**
     * 写入JSON响应
     */
    private void writeJsonResponse(HttpServletResponse response, int status, ApiResponse<?> apiResponse) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(apiResponse));
            writer.flush();
        }
    }
}
