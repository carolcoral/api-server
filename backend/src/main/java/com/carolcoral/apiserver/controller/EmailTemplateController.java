/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.PageResult;
import com.carolcoral.apiserver.entity.EmailTemplate;
import com.carolcoral.apiserver.service.EmailTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 邮件模板Controller
 * 用于管理邮件模板的 CRUD 操作
 *
 * @author carolcoral
 * @version 1.0
 * @since 2026-06-16
 */
@Tag(name = "邮件模板管理", description = "邮件模板管理接口")
@RestController
@RequestMapping("/api/email-templates")
public class EmailTemplateController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EmailTemplateController.class);

    private final EmailTemplateService emailTemplateService;

    public EmailTemplateController(EmailTemplateService emailTemplateService) {
        this.emailTemplateService = emailTemplateService;
    }

    /**
     * 获取所有邮件模板（支持分页和搜索）
     *
     * @param name    模板名称（模糊搜索）
     * @param type    模板类型
     * @param enabled 启用状态
     * @param page    页码（从0开始）
     * @param size    每页大小
     * @return 分页结果
     */
    @GetMapping
    @Operation(summary = "获取所有邮件模板")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('email-template:view')")
    public ApiResponse<PageResult<EmailTemplate>> getAllTemplates(
            @Parameter(description = "模板名称（模糊搜索）") @RequestParam(required = false) String name,
            @Parameter(description = "模板类型") @RequestParam(required = false) String type,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        return emailTemplateService.searchTemplates(name, type, enabled, page, size);
    }

    /**
     * 根据ID获取邮件模板
     *
     * @param id 模板ID
     * @return 邮件模板
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取邮件模板")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('email-template:view')")
    public ApiResponse<EmailTemplate> getTemplate(@PathVariable Long id) {
        return emailTemplateService.getTemplateById(id);
    }

    /**
     * 创建邮件模板
     *
     * @param template 邮件模板
     * @return 创建的模板
     */
    @PostMapping
    @Operation(summary = "创建邮件模板")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('email-template:create')")
    public ApiResponse<EmailTemplate> createTemplate(@RequestBody EmailTemplate template) {
        return emailTemplateService.createTemplate(template);
    }

    /**
     * 更新邮件模板
     *
     * @param id       模板ID
     * @param template 更新的模板信息
     * @return 更新后的模板
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新邮件模板")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('email-template:edit')")
    public ApiResponse<EmailTemplate> updateTemplate(@PathVariable Long id, @RequestBody EmailTemplate template) {
        return emailTemplateService.updateTemplate(id, template);
    }

    /**
     * 删除邮件模板
     *
     * @param id 模板ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除邮件模板")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('email-template:delete')")
    public ApiResponse<Void> deleteTemplate(@PathVariable Long id) {
        return emailTemplateService.deleteTemplate(id);
    }
}
