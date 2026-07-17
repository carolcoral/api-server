/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

/**
 * 系统配置实体
 * 用于存储系统级别的配置，如默认语言
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Schema(description = "系统配置实体")
@Entity
@Table(name = "t_system_config")
public class SystemConfig {

    @Schema(description = "配置ID", example = "1")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "配置键", example = "defaultLanguage")
    @Column(nullable = false, unique = true, length = 100)
    private String configKey;

    @Schema(description = "配置值", example = "zh-CN")
    @Column(nullable = false, length = 500)
    private String configValue;

    @Schema(description = "配置描述")
    @Column(length = 500)
    private String description;

    @Schema(description = "创建时间")
    @Column(nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @Column(nullable = false)
    private LocalDateTime updateTime;

    /**
     * 获取配置ID
     *
     * @return 配置ID
     */
    public Long getId() {
        return id;
    }

    /**
     * 设置配置ID
     *
     * @param id 配置ID
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 获取配置键
     *
     * @return 配置键
     */
    public String getConfigKey() {
        return configKey;
    }

    /**
     * 设置配置键
     *
     * @param configKey 配置键
     */
    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    /**
     * 获取配置值
     *
     * @return 配置值
     */
    public String getConfigValue() {
        return configValue;
    }

    /**
     * 设置配置值
     *
     * @param configValue 配置值
     */
    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    /**
     * 获取配置描述
     *
     * @return 配置描述
     */
    public String getDescription() {
        return description;
    }

    /**
     * 设置配置描述
     *
     * @param description 配置描述
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
     * 设置更新时间
     *
     * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
