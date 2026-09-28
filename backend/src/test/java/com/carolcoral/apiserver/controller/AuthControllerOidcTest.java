/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.LoginResponse;
import com.carolcoral.apiserver.repository.EmailConfigRepository;
import com.carolcoral.apiserver.repository.UserRepository;
import com.carolcoral.apiserver.service.EmailService;
import com.carolcoral.apiserver.service.OidcService;
import com.carolcoral.apiserver.service.PermissionService;
import com.carolcoral.apiserver.service.SystemConfigService;
import com.carolcoral.apiserver.service.UserService;
import com.carolcoral.apiserver.util.JwtTokenUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * AuthController 中 OIDC（多服务商）相关端点的单元测试。
 * <p>覆盖发起登录（authorize）与回调（callback）的成功、失败与异常分支。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerOidcTest {

    @Mock UserService userService;
    @Mock JwtTokenUtil jwtTokenUtil;
    @Mock SystemConfigService systemConfigService;
    @Mock EmailService emailService;
    @Mock EmailConfigRepository emailConfigRepository;
    @Mock UserRepository userRepository;
    @Mock PermissionService permissionService;
    @Mock OidcService oidcService;
    @Mock HttpServletRequest request;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userService, jwtTokenUtil, systemConfigService,
                emailService, emailConfigRepository, userRepository, permissionService, oidcService);
        when(request.getScheme()).thenReturn("https");
        when(request.getServerName()).thenReturn("app.fan");
        when(request.getServerPort()).thenReturn(443);
    }

    /**
     * 捕获 controller 推导出的 baseUrl（OIDC 回调地址 = baseUrl + /api/auth/oidc/callback）
     */
    private String capturedBaseUrl() {
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(oidcService).buildAuthorizationUrl(
                org.mockito.ArgumentMatchers.nullable(String.class), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("GET /api/auth/oidc/authorize")
    class Authorize {

        @Test
        @DisplayName("未启用：返回错误，不生成 URL")
        void notEnabled() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(false);
            ApiResponse<Map<String, String>> r = controller.oidcAuthorize(null, request);
            assertEquals(500, r.getCode());
            assertTrue(r.getMessage().contains("未启用"));
            assertNull(r.getData());
        }

        @Test
        @DisplayName("成功：返回授权地址，默认端口不拼接端口号")
        void success() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(oidcService.buildAuthorizationUrl(null, "https://app.fan")).thenReturn("https://tdp.fan/authorize?x=1");
            ApiResponse<Map<String, String>> r = controller.oidcAuthorize(null, request);
            assertEquals(200, r.getCode());
            assertEquals("https://tdp.fan/authorize?x=1", r.getData().get("authorizationUrl"));
        }

        @Test
        @DisplayName("非默认端口：baseUrl 拼接端口号")
        void nonDefaultPort() {
            when(request.getScheme()).thenReturn("http");
            when(request.getServerPort()).thenReturn(8080);
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(oidcService.buildAuthorizationUrl("keycloak", "http://app.fan:8080")).thenReturn("url");
            controller.oidcAuthorize("keycloak", request);
            // 通过 mock 校验 baseUrl 推导正确，且 providerId 透传
            org.mockito.Mockito.verify(oidcService).buildAuthorizationUrl("keycloak", "http://app.fan:8080");
        }

        @Test
        @DisplayName("反向代理只透传 Host：用 Origin 协商出 https，不把站点推成 http")
        void schemeFromOrigin() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getServerName()).thenReturn("k5lo5buaxq-8080.cnb.run");
            when(request.getServerPort()).thenReturn(8080);
            when(request.getHeader("Origin")).thenReturn("https://k5lo5buaxq-8080.cnb.run");
            when(request.getHeader("Host")).thenReturn("k5lo5buaxq-8080.cnb.run");

            controller.oidcAuthorize(null, request);

            assertEquals("https://k5lo5buaxq-8080.cnb.run", capturedBaseUrl(),
                    "Origin 为 https 时不能推导出 http（否则 redirect_uri 与 IdP 注册值不一致 → 400）");
        }

        @Test
        @DisplayName("Origin 协议被 X-Forwarded-Proto 校正：https 站点不会被推成 http")
        void schemePrefersForwardedProto() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
            // 网关把 https 回源成 http，Origin 里可能残留 http，但 host 仍是浏览器地址
            when(request.getHeader("Origin")).thenReturn("http://app.fan");
            when(request.getHeader("Host")).thenReturn("app.fan");

            controller.oidcAuthorize(null, request);

            assertEquals("https://app.fan", capturedBaseUrl());
        }

        @Test
        @DisplayName("Origin 已是最新的真实地址：host 与协议都取 Origin")
        void schemeAndHostFromOrigin() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getHeader("Origin")).thenReturn("https://public.cnb.run");
            when(request.getHeader("Host")).thenReturn("inner-8080.cnb.run");

            controller.oidcAuthorize(null, request);

            assertEquals("https://public.cnb.run", capturedBaseUrl());
        }

        @Test
        @DisplayName("X-Forwarded-Proto 取第一段（网关链路最靠近浏览器的一跳）")
        void schemeFirstSegment() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getHeader("X-Forwarded-Proto")).thenReturn("https, http");
            when(request.getHeader("Host")).thenReturn("app.fan");

            controller.oidcAuthorize(null, request);

            assertEquals("https://app.fan", capturedBaseUrl());
        }

        @Test
        @DisplayName("无 Origin 时用 Referer 协商协议")
        void schemeFromReferer() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getHeader("Referer")).thenReturn("https://app.fan/login");
            when(request.getHeader("Host")).thenReturn("app.fan");

            controller.oidcAuthorize(null, request);

            assertEquals("https://app.fan", capturedBaseUrl());
        }

        @Test
        @DisplayName("RFC 7239 Forwarded: proto=https 参与协商")
        void schemeFromForwardedHeader() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getHeader("Forwarded")).thenReturn("proto=\"https\";host=app.fan");
            when(request.getHeader("Host")).thenReturn("app.fan");

            controller.oidcAuthorize(null, request);

            assertEquals("https://app.fan", capturedBaseUrl());
        }

        @Test
        @DisplayName("无任何协商证据：回退容器 scheme（http 非默认端口补端口号）")
        void schemeFallsBackToContainer() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getScheme()).thenReturn("http");
            when(request.getServerName()).thenReturn("app.fan");
            when(request.getServerPort()).thenReturn(8080);
            when(request.getHeader("Host")).thenReturn(null);
            when(request.getHeader("X-Forwarded-Host")).thenReturn(null);
            when(request.getHeader("X-Forwarded-Server")).thenReturn(null);
            when(request.getHeader("X-Forwarded-Proto")).thenReturn(null);
            when(request.getHeader("Origin")).thenReturn(null);

            controller.oidcAuthorize(null, request);

            assertEquals("http://app.fan:8080", capturedBaseUrl());
        }

        @Test
        @DisplayName("无法识别的协议值被忽略，不污染 baseUrl")
        void schemeIgnoresGarbage() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(request.getHeader("X-Forwarded-Proto")).thenReturn("ftp");
            when(request.getHeader("Origin")).thenReturn("null");
            when(request.getHeader("Host")).thenReturn("app.fan");

            controller.oidcAuthorize(null, request);

            assertEquals("https://app.fan", capturedBaseUrl(), "非法协议应回退容器 scheme=https");
        }

        @Test
        @DisplayName("构建 URL 抛异常：捕获并返回错误")
        void buildThrows() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(oidcService.buildAuthorizationUrl(org.mockito.ArgumentMatchers.nullable(String.class),
                            org.mockito.ArgumentMatchers.anyString()))
                    .thenThrow(new IllegalStateException("discovery down"));
            ApiResponse<Map<String, String>> r = controller.oidcAuthorize(null, request);
            assertEquals(500, r.getCode());
            assertTrue(r.getMessage().contains("发起 OIDC 登录失败"));
        }
    }

    @Nested
    @DisplayName("GET /api/auth/oidc/callback")
    class Callback {

        private String location(ResponseEntity<Void> r) {
            return r.getHeaders().getFirst("Location");
        }

        /** 回调新增了 HttpServletRequest 参数（用于推导站点地址），统一补在末尾 */
        private ResponseEntity<Void> callback(String providerId, String code, String state,
                                              String error, String errorDescription) {
            return controller.oidcCallback(providerId, code, state, error, errorDescription, request);
        }

        @Test
        @DisplayName("授权方返回 error：重定向并携带错误")
        void providerError() {
            ResponseEntity<Void> r = callback(null, null, null, "access_denied", "用户拒绝");
            assertEquals(HttpStatus.FOUND, r.getStatusCode());
            assertTrue(location(r).startsWith("/login?oidc_error="));
            assertTrue(location(r).contains("%E7%94%A8%E6%88%B7"));
        }

        @Test
        @DisplayName("授权方 error 无描述：使用 error 值")
        void providerErrorNoDescription() {
            ResponseEntity<Void> r = callback(null, null, null, "server_error", null);
            assertTrue(location(r).contains("server_error"));
        }

        @Test
        @DisplayName("登录成功：重定向携带 token")
        void success() {
            LoginResponse lr = LoginResponse.builder().token("jwt-abc").build();
            when(oidcService.handleCallback(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("code"), org.mockito.ArgumentMatchers.eq("state"), org.mockito.ArgumentMatchers.any())).thenReturn(ApiResponse.success(lr));
            ResponseEntity<Void> r = callback(null, "code", "state", null, null);
            assertEquals(HttpStatus.FOUND, r.getStatusCode());
            assertEquals("/login?oidc_token=jwt-abc", location(r));
        }

        @Test
        @DisplayName("登录失败：重定向携带错误信息")
        void failure() {
            when(oidcService.handleCallback(org.mockito.ArgumentMatchers.eq("keycloak"), org.mockito.ArgumentMatchers.eq("c"), org.mockito.ArgumentMatchers.eq("s"), org.mockito.ArgumentMatchers.any())).thenReturn(ApiResponse.error("state 无效或已过期"));
            ResponseEntity<Void> r = callback("keycloak", "c", "s", null, null);
            assertTrue(location(r).startsWith("/login?oidc_error="));
            assertFalse(location(r).contains("oidc_token"));
        }

        @Test
        @DisplayName("返回 200 但 data 为 null：视为失败并给出默认提示")
        void successWithoutData() {
            when(oidcService.handleCallback(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("c"), org.mockito.ArgumentMatchers.eq("s"), org.mockito.ArgumentMatchers.any())).thenReturn(ApiResponse.success(null));
            ResponseEntity<Void> r = callback(null, "c", "s", null, null);
            assertTrue(location(r).contains("oidc_error="));
        }

        @Test
        @DisplayName("服务抛异常：捕获并重定向错误")
        void throwsException() {
            when(oidcService.handleCallback(org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("c"), org.mockito.ArgumentMatchers.eq("s"), org.mockito.ArgumentMatchers.any())).thenThrow(new RuntimeException("boom"));
            ResponseEntity<Void> r = callback(null, "c", "s", null, null);
            assertEquals(HttpStatus.FOUND, r.getStatusCode());
            assertTrue(location(r).startsWith("/login?oidc_error="));
        }
    }
}
