/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.SystemConfig;
import com.carolcoral.apiserver.repository.SystemConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 系统配置Service
 * 用于管理系统级别的配置，如默认语言
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Tag(name = "系统配置Service", description = "系统配置业务逻辑处理")
@Service
public class SystemConfigService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SystemConfigService.class);

    private final SystemConfigRepository systemConfigRepository;

    /**
     * 构造器
     */
    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    /**
     * 获取系统配置
     *
     * @param configKey 配置键
     * @return 配置值
     */
    @Operation(summary = "获取系统配置")
    public String getConfig(String configKey) {
        Optional<SystemConfig> configOpt = systemConfigRepository.findByConfigKey(configKey);
        return configOpt.map(SystemConfig::getConfigValue).orElse(null);
    }

    /**
     * 获取系统默认语言
     *
     * @return 默认语言
     */
    @Operation(summary = "获取系统默认语言")
    public String getDefaultLanguage() {
        Optional<SystemConfig> configOpt = systemConfigRepository.findByConfigKey("defaultLanguage");
        return configOpt.map(SystemConfig::getConfigValue).orElse("zh-CN");
    }

    /**
     * 保存或更新系统配置
     *
     * @param configKey   配置键
     * @param configValue 配置值
     * @param description 配置描述
     * @return 操作结果
     */
    @Operation(summary = "保存系统配置")
    @Transactional
    public void saveConfig(String configKey, String configValue, String description) {
        Optional<SystemConfig> existingConfigOpt = systemConfigRepository.findByConfigKey(configKey);
        SystemConfig config;

        if (existingConfigOpt.isPresent()) {
            // 更新现有配置
            config = existingConfigOpt.get();
            config.setConfigValue(configValue);
            config.setDescription(description);
            config.setUpdateTime(LocalDateTime.now());
        } else {
            // 创建新配置
            config = new SystemConfig();
            config.setConfigKey(configKey);
            config.setConfigValue(configValue);
            config.setDescription(description);
            config.setCreateTime(LocalDateTime.now());
            config.setUpdateTime(LocalDateTime.now());
        }

        systemConfigRepository.save(config);
        log.info("系统配置已保存: {} = {}", configKey, configValue);
    }

    /**
     * 删除系统配置
     *
     * @param configKey 配置键
     */
    @Operation(summary = "删除系统配置")
    @Transactional
    public void deleteConfig(String configKey) {
        Optional<SystemConfig> existingConfigOpt = systemConfigRepository.findByConfigKey(configKey);
        if (existingConfigOpt.isPresent()) {
            systemConfigRepository.delete(existingConfigOpt.get());
            log.info("系统配置已删除: {}", configKey);
        }
    }
}
