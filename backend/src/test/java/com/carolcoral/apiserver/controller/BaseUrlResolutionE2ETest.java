/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.oidc.OidcPublicConfigDTO;
import com.carolcoral.apiserver.service.OidcService;
import com.carolcoral.apiserver.config.PublicConfigController;
import com.carolcoral.apiserver.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 端到端串联校验：公开配置接口把请求地址透传给 OidcService，
 * 保证 iconUrl 与 redirect_uri 用的是同一套站点地址（避免两处不一致）。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BaseUrlResolutionE2ETest {

    @Mock SystemConfigService systemConfigService;
    @Mock OidcService oidcService;
    @Mock HttpServletRequest request;

    private PublicConfigController controller;

    private void stub(Map<String, String> headers) {
        controller = new PublicConfigController(systemConfigService, oidcService);
        when(request.getHeader(anyString())).thenAnswer(inv -> headers.get(inv.getArgument(0, String.class)));
        when(request.getScheme()).thenAnswer(inv -> headers.getOrDefault("scheme", "http"));
        when(request.getServerName()).thenReturn("inner-8080.cnb.run");
        when(request.getServerPort()).thenReturn(8080);
        OidcPublicConfigDTO dto = new OidcPublicConfigDTO(false, java.util.List.of());
        when(oidcService.getPublicConfig(any())).thenReturn(dto);
    }

    @Test
    @DisplayName("公开配置接口把 Origin 推导出的绝对站点地址传给 OIDC 公开配置（图标绝对地址）")
    void passesOriginToOidcPublicConfig() {
        stub(Map.of("Origin", "https://public-8080.cnb.run"));
        controller.getPublicConfig(request);
        verify(oidcService).getPublicConfig("https://public-8080.cnb.run");
    }

    @Test
    @DisplayName("网关只透传 Host 时用 X-Forwarded-Proto 协商出的 https 地址")
    void negotiatesSchemeForIconBase() {
        stub(Map.of("Host", "public-8080.cnb.run", "X-Forwarded-Proto", "https"));
        controller.getPublicConfig(request);
        verify(oidcService).getPublicConfig("https://public-8080.cnb.run");
    }

    @Test
    @DisplayName("拿不到任何请求头时回退容器地址（http://host:port）")
    void fallsBackToContainerAddress() {
        stub(Map.of());
        controller.getPublicConfig(request);
        verify(oidcService).getPublicConfig("http://inner-8080.cnb.run:8080");
    }
}
