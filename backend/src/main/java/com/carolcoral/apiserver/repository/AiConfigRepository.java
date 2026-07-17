/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.AiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI 配置 Repository
 *
 * @author carolcoral
 * @since 2026-06-23
 */
@Tag(name = "AI配置Repository", description = "AI配置数据访问接口")
@Repository
public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {

    @Operation(summary = "查找启用的AI配置")
    Optional<AiConfig> findFirstByEnabledTrue();

    @Operation(summary = "按服务商标识查找")
    Optional<AiConfig> findByProvider(String provider);

    @Operation(summary = "查找所有启用的配置")
    List<AiConfig> findAllByEnabledTrue();

    @Operation(summary = "查找默认的AI配置")
    Optional<AiConfig> findFirstByIsDefaultTrueAndEnabledTrue();

    @Operation(summary = "检查服务商是否已配置")
    boolean existsByProvider(String provider);
}
