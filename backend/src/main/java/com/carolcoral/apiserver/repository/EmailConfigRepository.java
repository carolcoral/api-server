/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.EmailConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 邮箱配置Repository
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Tag(name = "邮箱配置Repository", description = "邮箱配置数据访问接口")
@Repository
public interface EmailConfigRepository extends JpaRepository<EmailConfig, Long> {

    /**
     * 查找启用的邮箱配置
     *
     * @return 启用的邮箱配置
     */
    @Operation(summary = "查找启用的邮箱配置")
    Optional<EmailConfig> findFirstByEnabledTrue();
}
