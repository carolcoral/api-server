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
 * AuthController 中 TDP OIDC 相关端点的单元测试。
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

    @Nested
    @DisplayName("GET /api/auth/oidc/authorize")
    class Authorize {

        @Test
        @DisplayName("未启用：返回错误，不生成 URL")
        void notEnabled() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(false);
            ApiResponse<Map<String, String>> r = controller.oidcAuthorize(request);
            assertEquals(500, r.getCode());
            assertTrue(r.getMessage().contains("未启用"));
            assertNull(r.getData());
        }

        @Test
        @DisplayName("成功：返回授权地址，默认端口不拼接端口号")
        void success() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(oidcService.buildAuthorizationUrl("https://app.fan")).thenReturn("https://tdp.fan/authorize?x=1");
            ApiResponse<Map<String, String>> r = controller.oidcAuthorize(request);
            assertEquals(200, r.getCode());
            assertEquals("https://tdp.fan/authorize?x=1", r.getData().get("authorizationUrl"));
        }

        @Test
        @DisplayName("非默认端口：baseUrl 拼接端口号")
        void nonDefaultPort() {
            when(request.getScheme()).thenReturn("http");
            when(request.getServerPort()).thenReturn(8080);
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(oidcService.buildAuthorizationUrl("http://app.fan:8080")).thenReturn("url");
            controller.oidcAuthorize(request);
            // 通过 mock 校验 baseUrl 推导正确
            org.mockito.Mockito.verify(oidcService).buildAuthorizationUrl("http://app.fan:8080");
        }

        @Test
        @DisplayName("构建 URL 抛异常：捕获并返回错误")
        void buildThrows() {
            when(oidcService.isEnabledAndConfigured()).thenReturn(true);
            when(oidcService.buildAuthorizationUrl(org.mockito.ArgumentMatchers.anyString()))
                    .thenThrow(new IllegalStateException("discovery down"));
            ApiResponse<Map<String, String>> r = controller.oidcAuthorize(request);
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

        @Test
        @DisplayName("授权方返回 error：重定向并携带错误")
        void providerError() {
            ResponseEntity<Void> r = controller.oidcCallback(null, null, "access_denied", "用户拒绝");
            assertEquals(HttpStatus.FOUND, r.getStatusCode());
            assertTrue(location(r).startsWith("/login?oidc_error="));
            assertTrue(location(r).contains("%E7%94%A8%E6%88%B7"));
        }

        @Test
        @DisplayName("授权方 error 无描述：使用 error 值")
        void providerErrorNoDescription() {
            ResponseEntity<Void> r = controller.oidcCallback(null, null, "server_error", null);
            assertTrue(location(r).contains("server_error"));
        }

        @Test
        @DisplayName("登录成功：重定向携带 token")
        void success() {
            LoginResponse lr = LoginResponse.builder().token("jwt-abc").build();
            when(oidcService.handleCallback("code", "state")).thenReturn(ApiResponse.success(lr));
            ResponseEntity<Void> r = controller.oidcCallback("code", "state", null, null);
            assertEquals(HttpStatus.FOUND, r.getStatusCode());
            assertEquals("/login?oidc_token=jwt-abc", location(r));
        }

        @Test
        @DisplayName("登录失败：重定向携带错误信息")
        void failure() {
            when(oidcService.handleCallback("c", "s")).thenReturn(ApiResponse.error("state 无效或已过期"));
            ResponseEntity<Void> r = controller.oidcCallback("c", "s", null, null);
            assertTrue(location(r).startsWith("/login?oidc_error="));
            assertFalse(location(r).contains("oidc_token"));
        }

        @Test
        @DisplayName("返回 200 但 data 为 null：视为失败并给出默认提示")
        void successWithoutData() {
            when(oidcService.handleCallback("c", "s")).thenReturn(ApiResponse.success(null));
            ResponseEntity<Void> r = controller.oidcCallback("c", "s", null, null);
            assertTrue(location(r).contains("oidc_error="));
        }

        @Test
        @DisplayName("服务抛异常：捕获并重定向错误")
        void throwsException() {
            when(oidcService.handleCallback("c", "s")).thenThrow(new RuntimeException("boom"));
            ResponseEntity<Void> r = controller.oidcCallback("c", "s", null, null);
            assertEquals(HttpStatus.FOUND, r.getStatusCode());
            assertTrue(location(r).startsWith("/login?oidc_error="));
        }
    }
}
