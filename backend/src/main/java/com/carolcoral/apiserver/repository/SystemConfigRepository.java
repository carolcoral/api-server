/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.SystemConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 系统配置Repository
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-03-06
 */
@Tag(name = "系统配置Repository", description = "系统配置数据访问接口")
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /**
     * 根据配置键查找配置
     *
     * @param configKey 配置键
     * @return 配置Optional
     */
    @Operation(summary = "根据配置键查找配置")
    Optional<SystemConfig> findByConfigKey(String configKey);
}
