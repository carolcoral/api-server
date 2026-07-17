/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.repository;

import com.carolcoral.apiserver.entity.EmailTemplate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 邮件模板Repository
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Tag(name = "邮件模板Repository", description = "邮件模板数据访问接口")
@Repository
public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, Long>, JpaSpecificationExecutor<EmailTemplate> {

    /**
     * 根据模板类型查找
     *
     * @param type 模板类型
     * @return 模板列表
     */
    @Operation(summary = "根据模板类型查找")
    List<EmailTemplate> findByType(String type);

    /**
     * 查找启用的模板（按类型）
     *
     * @param type 模板类型
     * @param enabled 是否启用
     * @return 模板Optional
     */
    @Operation(summary = "查找启用的模板")
    Optional<EmailTemplate> findFirstByTypeAndEnabledTrue(String type);

    /**
     * 查找所有启用的模板
     *
     * @return 模板列表
     */
    @Operation(summary = "查找所有启用的模板")
    List<EmailTemplate> findByEnabledTrue();
}
