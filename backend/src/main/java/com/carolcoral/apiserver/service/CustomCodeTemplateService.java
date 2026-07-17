/**
* Copyright (c) 2026, XINDU.SITE，Author: LXW
* All Rights Reserved.
* XINDU.SITE CONFIDENTIAL
*/

package com.carolcoral.apiserver.service;

import com.carolcoral.apiserver.dto.ApiResponse;
import com.carolcoral.apiserver.dto.PageResult;
import com.carolcoral.apiserver.entity.CustomCodeTemplate;
import com.carolcoral.apiserver.entity.Project;
import com.carolcoral.apiserver.entity.ProjectMember;
import com.carolcoral.apiserver.entity.User;
import com.carolcoral.apiserver.repository.CustomCodeTemplateRepository;
import com.carolcoral.apiserver.repository.ProjectMemberRepository;
import com.carolcoral.apiserver.repository.ProjectRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 自定义代码模板服务类
 *
 * @author carolcoral
 */
@Tag(name = "自定义代码模板服务", description = "自定义代码模板业务逻辑处理")
@Service
public class CustomCodeTemplateService {
    private static final Logger log = LoggerFactory.getLogger(CustomCodeTemplateService.class);

    private final CustomCodeTemplateRepository templateRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public CustomCodeTemplateService(CustomCodeTemplateRepository templateRepository,
                                     ProjectRepository projectRepository,
                                     ProjectMemberRepository projectMemberRepository) {
        this.templateRepository = templateRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    /**
     * 检查当前登录用户是否拥有指定权限（ADMIN 角色或指定 authority）
     */
    private boolean hasAuthorityOrAdmin(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authStr = ga.getAuthority();
            if ("ROLE_ADMIN".equals(authStr) || authority.equals(authStr)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 创建模板
     * 系统管理员可创建系统默认模板（isSystem=true, project=null）
     * 普通用户只能创建项目级模板
     */
    @Operation(summary = "创建模板")
    @Transactional
    public ApiResponse<CustomCodeTemplate> createTemplate(CustomCodeTemplate template, Long userId, User.UserRole userRole) {
        try {
            boolean isSystemTemplate = template.getIsSystem() != null && template.getIsSystem();

            if (isSystemTemplate) {
                // 只有系统管理员可以创建系统默认模板
                if (!hasAuthorityOrAdmin("code-template:edit")) {
                    return ApiResponse.error("只有系统管理员或有 code-template:edit 权限的用户可以创建系统默认模板");
                }
                // 系统模板不关联项目
                template.setProject(null);
            } else {
                if (template.getProject() == null || template.getProject().getId() == null) {
                    return ApiResponse.error("项目ID不能为空");
                }
                Optional<Project> projectOpt = projectRepository.findById(template.getProject().getId());
                if (!projectOpt.isPresent()) {
                    return ApiResponse.error("项目不存在");
                }
                template.setProject(projectOpt.get());
            }

            template.setCreateUserId(userId);

            CustomCodeTemplate saved = templateRepository.save(template);
            log.info("创建自定义代码模板成功: id={}, name={}, isSystem={}, projectId={}",
                    saved.getId(), saved.getName(), saved.getIsSystem(),
                    template.getProject() != null ? template.getProject().getId() : "null");
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("创建自定义代码模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("创建模板失败，请稍后重试");
        }
    }

    /**
     * 更新模板
     */
    @Operation(summary = "更新模板")
    @Transactional
    public ApiResponse<CustomCodeTemplate> updateTemplate(CustomCodeTemplate template, Long userId, User.UserRole userRole) {
        try {
            if (template.getId() == null) {
                return ApiResponse.error("模板ID不能为空");
            }

            Optional<CustomCodeTemplate> existingOpt = templateRepository.findById(template.getId());
            if (!existingOpt.isPresent()) {
                return ApiResponse.error("模板不存在");
            }

            CustomCodeTemplate existing = existingOpt.get();

            // 权限检查：系统管理员或有 code-template:edit 权限可编辑所有模板（含系统模板），项目创建者/管理员可编辑本项目模板
            if (!hasAuthorityOrAdmin("code-template:edit")) {
                // 普通用户：系统默认模板不可修改
                if (existing.getIsSystem() != null && existing.getIsSystem()) {
                    return ApiResponse.error("系统默认模板不可修改");
                }
                // 普通用户：只能编辑自己项目下的模板
                if (!hasProjectAdminPermission(existing.getProject().getId(), userId)) {
                    return ApiResponse.error("没有权限修改此模板");
                }
            }

            if (template.getName() != null) {
                existing.setName(template.getName());
            }
            if (template.getDescription() != null) {
                existing.setDescription(template.getDescription());
            }
            if (template.getSourceCode() != null) {
                existing.setSourceCode(template.getSourceCode());
            }
            if (template.getEnabled() != null) {
                existing.setEnabled(template.getEnabled());
            }
            // 系统管理员或有 code-template:edit 权限可以修改 isSystem 标志
            if (template.getIsSystem() != null && hasAuthorityOrAdmin("code-template:edit")) {
                existing.setIsSystem(template.getIsSystem());
            }
            if (template.getProject() != null && template.getProject().getId() != null) {
                Optional<Project> projectOpt = projectRepository.findById(template.getProject().getId());
                if (!projectOpt.isPresent()) {
                    return ApiResponse.error("项目不存在");
                }
                existing.setProject(projectOpt.get());
            }

            CustomCodeTemplate saved = templateRepository.save(existing);
            log.info("更新自定义代码模板成功: id={}", saved.getId());
            return ApiResponse.success(saved);
        } catch (Exception e) {
            log.error("更新自定义代码模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("更新模板失败，请稍后重试");
        }
    }

    /**
     * 删除模板
     */
    @Operation(summary = "删除模板")
    @Transactional
    public ApiResponse<Void> deleteTemplate(Long templateId, Long userId, User.UserRole userRole) {
        try {
            Optional<CustomCodeTemplate> existingOpt = templateRepository.findById(templateId);
            if (!existingOpt.isPresent()) {
                return ApiResponse.error("模板不存在");
            }

            CustomCodeTemplate existing = existingOpt.get();

            // 权限检查：系统管理员或有 code-template:delete 权限可删除所有模板（含系统模板），项目创建者/管理员可删除本项目模板
            if (!hasAuthorityOrAdmin("code-template:delete")) {
                // 普通用户：系统默认模板不可删除
                if (existing.getIsSystem() != null && existing.getIsSystem()) {
                    return ApiResponse.error("系统默认模板不可删除");
                }
                // 普通用户：只能删除自己项目下的模板
                if (!hasProjectAdminPermission(existing.getProject().getId(), userId)) {
                    return ApiResponse.error("没有权限删除此模板");
                }
            }

            templateRepository.delete(existing);
            log.info("删除自定义代码模板成功: id={}", templateId);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("删除自定义代码模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("删除模板失败，请稍后重试");
        }
    }

    /**
     * 批量删除模板（仅系统管理员）
     */
    @Operation(summary = "批量删除模板（仅管理员）")
    @Transactional
    public ApiResponse<Void> batchDeleteTemplates(List<Long> ids, Long userId) {
        try {
            if (ids == null || ids.isEmpty()) {
                return ApiResponse.error("请选择要删除的模板");
            }

            int successCount = 0;
            int failCount = 0;
            for (Long id : ids) {
                try {
                    Optional<CustomCodeTemplate> existingOpt = templateRepository.findById(id);
                    if (existingOpt.isPresent()) {
                        templateRepository.delete(existingOpt.get());
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    log.error("批量删除模板失败: id={}, error={}", id, e.getMessage());
                    failCount++;
                }
            }

            log.info("批量删除模板完成: 成功={}, 失败={}, 操作人={}", successCount, failCount, userId);
            return ApiResponse.success();
        } catch (Exception e) {
            log.error("批量删除模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("批量删除失败，请稍后重试");
        }
    }

    /**
     * 根据ID查询模板
     */
    @Operation(summary = "根据ID查询模板")
    public ApiResponse<CustomCodeTemplate> getTemplateById(Long templateId) {
        try {
            Optional<CustomCodeTemplate> templateOpt = templateRepository.findById(templateId);
            if (!templateOpt.isPresent()) {
                return ApiResponse.error("模板不存在");
            }
            return ApiResponse.success(templateOpt.get());
        } catch (Exception e) {
            log.error("查询模板失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询模板失败，请稍后重试");
        }
    }

    /**
     * 根据项目ID查询模板列表（包含系统默认模板）
     */
    @Operation(summary = "根据项目ID查询模板列表（包含系统默认模板）")
    public ApiResponse<List<CustomCodeTemplate>> getTemplatesByProjectId(Long projectId) {
        try {
            List<CustomCodeTemplate> templates = new ArrayList<>();
            // 系统默认模板（全局可用）
            templates.addAll(templateRepository.findByIsSystemTrue());
            // 该项目的模板
            templates.addAll(templateRepository.findByProjectId(projectId));
            return ApiResponse.success(templates);
        } catch (Exception e) {
            log.error("查询模板列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询模板列表失败，请稍后重试");
        }
    }

    /**
     * 查询所有模板（系统管理员）或用户有权限访问的项目下的模板（普通用户）
     * 支持按名称、项目ID、启用状态过滤
     */
    @Operation(summary = "查询用户可访问的模板列表（支持过滤）")
    public ApiResponse<List<CustomCodeTemplate>> getAccessibleTemplates(
            Long userId, User.UserRole userRole, String name, Long projectId, Boolean enabled) {
        try {
            Specification<CustomCodeTemplate> spec = buildAccessSpec(userId, userRole, name, projectId, enabled);
            List<CustomCodeTemplate> templates = templateRepository.findAll(spec);
            return ApiResponse.success(templates);
        } catch (Exception e) {
            log.error("查询模板列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询模板列表失败，请稍后重试");
        }
    }

    /**
     * 分页搜索用户可访问的模板
     */
    @Operation(summary = "分页搜索用户可访问的模板列表")
    public ApiResponse<PageResult<CustomCodeTemplate>> searchAccessibleTemplates(
            Long userId, User.UserRole userRole, String name, Long projectId, Boolean enabled, int page, int size) {
        try {
            Specification<CustomCodeTemplate> spec = buildAccessSpec(userId, userRole, name, projectId, enabled);
            PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createTime"));
            Page<CustomCodeTemplate> result = templateRepository.findAll(spec, pageRequest);
            PageResult<CustomCodeTemplate> pageResult = new PageResult<>();
            pageResult.setContent(result.getContent());
            pageResult.setPage(result.getNumber());
            pageResult.setSize(result.getSize());
            pageResult.setTotalElements(result.getTotalElements());
            pageResult.setTotalPages(result.getTotalPages());
            pageResult.setFirst(result.isFirst());
            pageResult.setLast(result.isLast());
            return ApiResponse.success(pageResult);
        } catch (Exception e) {
            log.error("分页搜索模板列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询模板列表失败，请稍后重试");
        }
    }

    /**
     * 构建查询条件
     * <p>
     * 查询逻辑：
     * 1. 系统默认模板（isSystem=true, project=null）对所有用户可见
     * 2. 普通用户额外可看所属项目的模板
     * 3. 管理员可看所有模板
     * </p>
     */
    private Specification<CustomCodeTemplate> buildAccessSpec(
            Long userId, User.UserRole userRole, String name, Long projectId, Boolean enabled) {

        Specification<CustomCodeTemplate> spec = Specification.where(null);

        // 系统默认模板对所有用户可见
        Specification<CustomCodeTemplate> systemSpec = (root, query, cb) ->
                cb.isTrue(root.get("isSystem"));

        if (!hasAuthorityOrAdmin("code-template:view_all")) {
            List<Long> projectIds = getAccessibleProjectIds(userId);
            if (projectIds.isEmpty()) {
                // 没有权限访问任何项目，只能看系统默认模板
                spec = spec.and(systemSpec);
            } else {
                // 普通用户：系统模板 OR 所属项目模板
                Specification<CustomCodeTemplate> projectSpec = (root, query, cb) ->
                        root.get("project").get("id").in(projectIds);
                spec = spec.and((root, query, cb) ->
                        cb.or(systemSpec.toPredicate(root, query, cb),
                              projectSpec.toPredicate(root, query, cb)));
            }
        }
        // 管理员：不做额外限制，可看所有模板

        // 按名称模糊搜索
        if (name != null && !name.isEmpty()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
        }

        // 按项目ID精确过滤
        if (projectId != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("project").get("id"), projectId));
        }

        // 按启用状态过滤
        if (enabled != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("enabled"), enabled));
        }

        return spec;
    }

    /**
     * 获取用户有权限访问的项目ID列表（仅通过项目成员表判断）
     */
    private List<Long> getAccessibleProjectIds(Long userId) {
        List<Long> projectIds = new ArrayList<>();

        // 用户作为成员的项目（项目管理员或成员用户）
        List<ProjectMember> memberships = projectMemberRepository.findByUserId(userId);
        projectIds.addAll(memberships.stream().map(ProjectMember::getProjectId).collect(Collectors.toList()));

        return projectIds.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 根据项目ID查询已启用的模板列表（用于下拉选择，包含系统默认模板）
     */
    @Operation(summary = "根据项目ID查询已启用的模板列表（包含系统默认模板）")
    public ApiResponse<List<CustomCodeTemplate>> getEnabledTemplatesByProjectId(Long projectId) {
        try {
            List<CustomCodeTemplate> templates = new ArrayList<>();
            // 系统默认已启用的模板（全局可用）
            templates.addAll(templateRepository.findByIsSystemTrueAndEnabledTrue());
            // 该项目已启用的模板
            templates.addAll(templateRepository.findByProjectIdAndEnabled(projectId, true));
            return ApiResponse.success(templates);
        } catch (Exception e) {
            log.error("查询启用模板列表失败: {}", e.getMessage(), e);
            return ApiResponse.error("查询模板列表失败，请稍后重试");
        }
    }

    /**
     * 检查用户是否是项目管理员
     */
    private boolean hasProjectAdminPermission(Long projectId, Long userId) {
        return projectMemberRepository
                .findByProjectIdAndUserIdAndRole(projectId, userId, ProjectMember.MemberRole.ADMIN)
                .isPresent();
    }
}
