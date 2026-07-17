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

    public PublicConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    /**
     * 获取公开系统配置（无需认证）
     * <p>返回注册功能开关状态等公开配置信息。</p>
     *
     * @return 公开配置信息
     */
    @GetMapping("/system-config")
    @Operation(summary = "获取公开系统配置")
    public ApiResponse<Map<String, Object>> getPublicConfig() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enableRegistration", parseBooleanConfig("enableRegistration", false));
        config.put("enableEmailVerification", parseBooleanConfig("enableEmailVerification", false));
        return ApiResponse.success(config);
    }

    private boolean parseBooleanConfig(String key, boolean defaultValue) {
        String value = systemConfigService.getConfig(key);
        if (value != null && !value.isEmpty()) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }
}
