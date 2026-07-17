/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.controller;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.entity.CustomCodeTemplate;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.plugin.TransformerRegistry;
import com.carolcoral.apiserver.service.CustomCodeTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自定义代码模板控制器
 *
 * @author carolcoral
 */
@Tag(name = "自定义代码模板管理", description = "自定义代码模板CRUD操作")
@RestController
@RequestMapping("/api/code-templates")
public class CustomCodeTemplateController {

    private final CustomCodeTemplateService templateService;
    private final TransformerRegistry transformerRegistry;

    public CustomCodeTemplateController(CustomCodeTemplateService templateService,
                                         TransformerRegistry transformerRegistry) {
        this.templateService = templateService;
        this.transformerRegistry = transformerRegistry;
    }

    /**
     * 获取当前登录用户信息
     */
    private Long getCurrentUserId(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        return userIdObj != null ? Long.valueOf(userIdObj.toString()) : null;
    }

    private User.UserRole getCurrentUserRole(HttpServletRequest request) {
        Object roleObj = request.getAttribute("userRole");
        if (roleObj != null) {
            String roleStr = roleObj.toString();
            try {
                return User.UserRole.valueOf(roleStr);
            } catch (IllegalArgumentException e) {
                return User.UserRole.USER;
            }
        }
        return User.UserRole.USER;
    }

    /**
     * 创建模板
     */
    @Operation(summary = "创建自定义代码模板")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('code-template:create')")
    @PostMapping
    public ApiResponse<CustomCodeTemplate> createTemplate(
            @RequestBody CustomCodeTemplate template,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        User.UserRole userRole = getCurrentUserRole(request);
        if (userId == null) {
            return ApiResponse.error("用户未登录");
        }
        return templateService.createTemplate(template, userId, userRole);
    }

    /**
     * 更新模板
     */
    @Operation(summary = "更新自定义代码模板")
    @PutMapping
    public ApiResponse<CustomCodeTemplate> updateTemplate(
            @RequestBody CustomCodeTemplate template,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        User.UserRole userRole = getCurrentUserRole(request);
        if (userId == null) {
            return ApiResponse.error("用户未登录");
        }
        return templateService.updateTemplate(template, userId, userRole);
    }

    /**
     * 删除模板
     */
    @Operation(summary = "删除自定义代码模板")
    @DeleteMapping("/{templateId}")
    public ApiResponse<Void> deleteTemplate(
            @Parameter(description = "模板ID") @PathVariable Long templateId,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        User.UserRole userRole = getCurrentUserRole(request);
        if (userId == null) {
            return ApiResponse.error("用户未登录");
        }
        return templateService.deleteTemplate(templateId, userId, userRole);
    }

    /**
     * 批量删除模板（仅系统管理员）
     */
    @Operation(summary = "批量删除自定义代码模板（需要 code-template:delete 权限或管理员）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('code-template:delete')")
    @DeleteMapping("/batch-delete")
    public ApiResponse<Void> batchDeleteTemplates(
            @Parameter(description = "模板ID列表") @RequestBody List<Long> ids,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ApiResponse.error("用户未登录");
        }
        return templateService.batchDeleteTemplates(ids, userId);
    }

    /**
     * 根据ID查询模板
     */
    @Operation(summary = "根据ID查询自定义代码模板")
    @GetMapping("/{templateId}")
    public ApiResponse<CustomCodeTemplate> getTemplateById(
            @Parameter(description = "模板ID") @PathVariable Long templateId) {
        return templateService.getTemplateById(templateId);
    }

    /**
     * 根据项目ID查询模板列表
     */
    @Operation(summary = "根据项目ID查询模板列表")
    @GetMapping("/project/{projectId}")
    public ApiResponse<List<CustomCodeTemplate>> getTemplatesByProjectId(
            @Parameter(description = "项目ID") @PathVariable Long projectId) {
        return templateService.getTemplatesByProjectId(projectId);
    }

    /**
     * 根据项目ID查询已启用的模板列表（用于下拉选择）
     */
    @Operation(summary = "根据项目ID查询已启用的模板列表")
    @GetMapping("/project/{projectId}/enabled")
    public ApiResponse<List<CustomCodeTemplate>> getEnabledTemplatesByProjectId(
            @Parameter(description = "项目ID") @PathVariable Long projectId) {
        return templateService.getEnabledTemplatesByProjectId(projectId);
    }

    /**
     * 查询用户可访问的模板列表（支持过滤和分页）
     * 系统管理员：查看所有模板
     * 普通用户：只查看所属项目的模板
     */
    @Operation(summary = "查询用户可访问的模板列表（支持按名称、项目、状态过滤和分页）")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('code-template:view')")
    @GetMapping
    public ApiResponse<com.carolcoral.apiserver.dto.PageResult<CustomCodeTemplate>> getAccessibleTemplates(
            @Parameter(description = "模板名称（模糊搜索）") @RequestParam(required = false) String name,
            @Parameter(description = "项目ID") @RequestParam(required = false) Long projectId,
            @Parameter(description = "启用状态") @RequestParam(required = false) Boolean enabled,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        User.UserRole userRole = getCurrentUserRole(request);
        if (userId == null) {
            return ApiResponse.error("用户未登录");
        }
        return templateService.searchAccessibleTemplates(userId, userRole, name, projectId, enabled, page, size);
    }

    /**
     * 编译验证模板源码
     */
    @Operation(summary = "验证Java代码模板源码", description = "编译验证用户提交的Java源码是否能正常编译，不保存到数据库")
    @PostMapping("/validate")
    public ApiResponse<String> validateSourceCode(
            @Parameter(description = "Java源码") @RequestBody java.util.Map<String, String> body,
            HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        if (userId == null) {
            return ApiResponse.error("用户未登录");
        }
        String sourceCode = body.get("sourceCode");
        if (sourceCode == null || sourceCode.trim().isEmpty()) {
            return ApiResponse.error("源码不能为空");
        }
        try {
            // 使用模板ID或时间戳作为编译器缓存键
            Long cacheKey = body.containsKey("templateId") && body.get("templateId") != null
                    ? Long.valueOf(body.get("templateId"))
                    : System.currentTimeMillis();
            String error = transformerRegistry.validateSourceCode(cacheKey, sourceCode);
            if (error == null) {
                return ApiResponse.success("编译验证通过");
            } else {
                return ApiResponse.error(error);
            }
        } catch (Exception e) {
            return ApiResponse.error("编译验证失败: " + e.getMessage());
        }
    }
}
